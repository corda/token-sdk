package com.r3.corda.sdk.token.workflow.selection

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.sdk.token.contracts.states.FungibleToken
import com.r3.corda.sdk.token.contracts.types.IssuedTokenType
import com.r3.corda.sdk.token.contracts.types.TokenType
import com.r3.corda.sdk.token.workflow.utilities.sortByStateRefAscending
import net.corda.core.contracts.Amount
import net.corda.core.contracts.StateAndRef
import net.corda.core.node.AppServiceHub
import net.corda.core.node.services.CordaService
import net.corda.core.node.services.Vault
import net.corda.core.node.services.vault.DEFAULT_PAGE_NUM
import net.corda.core.node.services.vault.PageSpecification
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.core.utilities.contextLogger
import net.corda.node.services.keys.KeyManagementServiceInternal
import rx.Observable
import java.time.Duration
import java.util.concurrent.*

val UNLOCKER: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()
const val PLACE_HOLDER: String = "THIS_IS_A_PLACE_HOLDER"

@CordaService
class VaultWatcherService(val vaultObserver: VaultObserver? = null) : SingletonSerializeAsToken() {

    enum class IndexingType {
        EXTERNAL_ID, PUBLIC_KEY
    }

    private val cache: ConcurrentMap<TokenIndex, TokenBucket> = ConcurrentHashMap()

    constructor(appServiceHub: AppServiceHub) : this(getObservableFromAppServiceHub(appServiceHub))

    companion object {
        val LOG = contextLogger()

        private fun getObservableFromAppServiceHub(appServiceHub: AppServiceHub): VaultObserver {
            val config = appServiceHub.cordappProvider.getAppContext().config

            val indexingType = try {
                IndexingType.valueOf(config.get("ownerIndexingStrategy").toString())
            } catch (e: Exception) {
                IndexingType.PUBLIC_KEY
            }

            val ownerProvider: ((StateAndRef<FungibleToken<TokenType>>, AppServiceHub) -> Any) = if (indexingType == IndexingType.PUBLIC_KEY) {
                { stateAndRef, _ ->
                    stateAndRef.state.data.holder.owningKey
                }
            } else {
                { stateAndRef, appServiceHub ->
                    val kms = appServiceHub.keyManagementService as KeyManagementServiceInternal
                    kms.externalIdForPublicKey(stateAndRef.state.data.holder.owningKey)
                }
            }

            val pageSize = 1000
            var currentPage = DEFAULT_PAGE_NUM;
            var (existingStates, observable) = appServiceHub.vaultService.trackBy(
                    contractStateType = FungibleToken::class.java,
                    paging = PageSpecification(pageNumber = currentPage, pageSize = pageSize),
                    criteria = QueryCriteria.VaultQueryCriteria(),
                    sorting = sortByStateRefAscending())
            val listOfThings = mutableListOf<StateAndRef<FungibleToken<TokenType>>>()
            while (existingStates.states.isNotEmpty()) {
                listOfThings.addAll(existingStates.states as Iterable<StateAndRef<FungibleToken<TokenType>>>)
                existingStates = appServiceHub.vaultService.queryBy(FungibleToken::class.java, PageSpecification(pageNumber = ++currentPage, pageSize = pageSize))
            }
            return VaultObserver(listOfThings, observable as Observable<Vault.Update<FungibleToken<in TokenType>>>, ownerProvider)
        }

        fun processToken(token: FungibleToken<*>): TokenIndex {
            val owner = token.holder.owningKey
            val type = token.amount.token.tokenType.tokenClass
            val typeId = token.amount.token.tokenType.tokenIdentifier
            return TokenIndex(owner, type, typeId)
        }

    }


    //owner -> tokenClass -> tokenIdentifier -> List
    init {
        vaultObserver?.initialValues?.forEach(::addTokenToCache)
        vaultObserver?.source?.subscribe(::onVaultUpdate)
    }

    private fun onVaultUpdate(t: Vault.Update<FungibleToken<in TokenType>>) {
        t.consumed.forEach(::removeTokenFromCache)
        t.produced.forEach(::addTokenToCache)
    }

    private fun removeTokenFromCache(it: StateAndRef<FungibleToken<*>>) {
        val idx = processToken(it.state.data)
        val tokenSet = getTokenBucket(idx)
        val removeResult = tokenSet.remove(it)
        if (removeResult == PLACE_HOLDER || removeResult == null) {
            LOG.warn("Received a consumption event for a token ${it.ref} which was either not present in the token cache, or was not locked")
        }
    }

    private fun addTokenToCache(stateAndRef: StateAndRef<FungibleToken<in TokenType>>) {
        val token = stateAndRef.state.data

        val (owner, type, typeId) = processToken(token)
        val tokensForTypeInfo = getTokenBucket(owner, type, typeId)
        val existingMark = tokensForTypeInfo.putIfAbsent(stateAndRef, PLACE_HOLDER)
        existingMark?.let {
            LOG.warn("Attempted to overwrite existing token ${stateAndRef.ref} during cache initialization, this suggests incorrect vault behaviours")
        }
    }

    @Suspendable
    fun lockTokensExternal(list: List<StateAndRef<FungibleToken<in TokenType>>>, knownSelectionId: String) {
        list.forEach {
            val token = it.state.data
            val (owner, type, typeId) = processToken(token)
            val tokensForTypeInfo = getTokenBucket(owner, type, typeId)
            tokensForTypeInfo.replace(it, PLACE_HOLDER, knownSelectionId)
        }
    }

    @Suspendable
    inline fun <T : TokenType> selectTokens(
            owner: Any,
            amountRequested: Amount<IssuedTokenType<T>>,
            predicate: ((StateAndRef<FungibleToken<TokenType>>) -> Boolean) = { true },
            allowShortfall: Boolean = false,
            autoUnlockDelay: Duration = Duration.ofMinutes(5),
            selectionId: String
    ): List<StateAndRef<FungibleToken<T>>> {
        val set = getTokenBucket(owner, amountRequested.token.tokenType.tokenClass, amountRequested.token.tokenType.tokenIdentifier)
        val lockedTokens = mutableListOf<StateAndRef<FungibleToken<TokenType>>>()
        var amountLocked: Amount<IssuedTokenType<T>> = amountRequested.copy(quantity = 0)
        for (tokenStateAndRef in set.keys) {
            //does the token satisfy the (optional) predicate?
            if (amountRequested.token.issuer == tokenStateAndRef.state.data.amount.token.issuer && predicate.invoke(tokenStateAndRef)) {
                //if so, race to lock the token, expected oldValue = PLACE_HOLDER
                if (set.replace(tokenStateAndRef, PLACE_HOLDER, selectionId)) {
                    //we won the race to lock this token
                    lockedTokens.add(tokenStateAndRef)
                    val token = tokenStateAndRef.state.data
                    amountLocked += token.amount as Amount<IssuedTokenType<T>>
                    if (amountLocked >= amountRequested) {
                        break
                    }
                }
            }
        }
        if (!allowShortfall && amountLocked < amountRequested) {
            throw InsufficientBalanceException("Could not find enough tokens to satisfy token request")
        }

        //there is no way to "unlock" tokens from outside of this method
        UNLOCKER.schedule({
            lockedTokens.forEach {
                val token = it.state.data
                val idx = processToken(token)
                val tokensForTypeInfo = getTokenBucket(idx)
                tokensForTypeInfo.replace(it, selectionId, PLACE_HOLDER)
            }
        }, autoUnlockDelay.toMillis(), TimeUnit.MILLISECONDS)

        return lockedTokens as List<StateAndRef<FungibleToken<T>>>
    }

    fun getTokenBucket(idx: Any, tokenClass: Class<*>, tokenIdentifier: String): TokenBucket {
        return getTokenBucket(TokenIndex(idx, tokenClass, tokenIdentifier))
    }

    fun getTokenBucket(idx: TokenIndex): TokenBucket {
        return cache.computeIfAbsent(idx) {
            TokenBucket()
        }
    }

}

data class VaultObserver(val initialValues: List<StateAndRef<FungibleToken<TokenType>>>,
                         val source: Observable<Vault.Update<FungibleToken<in TokenType>>>,
                         val ownerProvider: ((StateAndRef<FungibleToken<TokenType>>, AppServiceHub) -> Any)? = null)

class TokenBucket(private val __backingMap: ConcurrentMap<StateAndRef<FungibleToken<in TokenType>>, String> = ConcurrentHashMap()) : ConcurrentMap<StateAndRef<FungibleToken<in TokenType>>, String> by __backingMap

data class TokenIndex(val owner: Any, val tokenClazz: Class<*>, val tokenIdentifier: String)

class InsufficientBalanceException(message: String) : RuntimeException(message)
