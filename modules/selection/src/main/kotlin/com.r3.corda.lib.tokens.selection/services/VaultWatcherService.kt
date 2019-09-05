package com.r3.corda.lib.tokens.selection.services

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.lib.tokens.contracts.states.FungibleToken
import com.r3.corda.lib.tokens.contracts.types.TokenType
import com.r3.corda.lib.tokens.contracts.utilities.withoutIssuer
import com.r3.corda.lib.tokens.selection.config.InMemorySelectionConfig
import com.r3.corda.lib.tokens.selection.internal.Holder
import com.r3.corda.lib.tokens.selection.internal.lookupExternalIdFromKey
import com.r3.corda.lib.tokens.selection.sortByStateRefAscending
import net.corda.core.contracts.Amount
import net.corda.core.contracts.StateAndRef
import net.corda.core.internal.uncheckedCast
import net.corda.core.node.AppServiceHub
import net.corda.core.node.ServiceHub
import net.corda.core.node.services.CordaService
import net.corda.core.node.services.Vault
import net.corda.core.node.services.vault.DEFAULT_PAGE_NUM
import net.corda.core.node.services.vault.PageSpecification
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.core.utilities.contextLogger
import rx.Observable
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

val UNLOCKER: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()
const val PLACE_HOLDER: String = "THIS_IS_A_PLACE_HOLDER"

@CordaService
class VaultWatcherService(private val tokenObserver: TokenObserver, private val serviceHub: ServiceHub) : SingletonSerializeAsToken() {
    enum class IndexingType {
        EXTERNAL_ID, // external id (all keys registered to this id as an owner) + token class + token identifier
        PUBLIC_KEY, // Public key + token class + token identifier
        TOKEN // Just token class + token identifier without owner
    }

    private val cache: ConcurrentMap<TokenIndex, TokenBucket> = ConcurrentHashMap()
    // TODO for now not used, add some reasonable default - stop listening print massive error; Figure out sensible default
    //    5% of heap, your heap needs to be min 8GB?
    private var cacheSize: Int = 1024

    constructor(appServiceHub: AppServiceHub) : this(getObservableFromAppServiceHub(appServiceHub, null), appServiceHub)

    companion object {
        val LOG = contextLogger()

        private fun getObservableFromAppServiceHub(appServiceHub: AppServiceHub, providedConfig: InMemorySelectionConfig?): TokenObserver {
            val config = appServiceHub.cordappProvider.getAppContext().config
            val configOptions = if (providedConfig == null) {
                InMemorySelectionConfig.parse(config)
            } else providedConfig

            val ownerProvider: ((StateAndRef<FungibleToken>, ServiceHub) -> Holder) = when (configOptions.indexingStrategy) {
                IndexingType.PUBLIC_KEY ->
                    { stateAndRef, _ ->
                        Holder.KeyIdentity(stateAndRef.state.data.holder.owningKey)
                    }
                IndexingType.EXTERNAL_ID ->
                    { stateAndRef, services ->
                        val owningKey = stateAndRef.state.data.holder.owningKey
                        lookupExternalIdFromKey(owningKey, services)
                    }
                IndexingType.TOKEN -> // This is default indexing.
                    { _, _ ->
                        Holder.JustToken
                    }
            }

            val pageSize = 1000
            var currentPage = DEFAULT_PAGE_NUM
            var (existingStates, observable) = appServiceHub.vaultService.trackBy(
                    contractStateType = FungibleToken::class.java,
                    paging = PageSpecification(pageNumber = currentPage, pageSize = pageSize),
                    criteria = QueryCriteria.VaultQueryCriteria(),
                    sorting = sortByStateRefAscending())
            val statesToProcess = mutableListOf<StateAndRef<FungibleToken>>()
            while (existingStates.states.isNotEmpty()) {
                statesToProcess.addAll(uncheckedCast(existingStates.states))
                existingStates = appServiceHub.vaultService.queryBy(
                        contractStateType = FungibleToken::class.java,
                        paging = PageSpecification(pageNumber = ++currentPage, pageSize = pageSize)
                )
            }
            return TokenObserver(statesToProcess, uncheckedCast(observable), ownerProvider)
        }
    }

    //owner -> tokenClass -> tokenIdentifier -> List
    init {
        tokenObserver.initialValues.forEach(::addTokenToCache)
        tokenObserver.source.subscribe(::onVaultUpdate)
    }

    fun processToken(token: StateAndRef<FungibleToken>): TokenIndex {
        val owner = tokenObserver.ownerProvider(token, serviceHub)
        val type = token.state.data.amount.token.tokenType.tokenClass
        val typeId = token.state.data.amount.token.tokenType.tokenIdentifier
        return TokenIndex(owner, type, typeId)
    }

    private fun onVaultUpdate(t: Vault.Update<FungibleToken>) {
        t.consumed.forEach(::removeTokenFromCache)
        t.produced.forEach(::addTokenToCache)
    }

    private fun removeTokenFromCache(it: StateAndRef<FungibleToken>) {
        val idx = processToken(it)
        val tokenSet = getTokenBucket(idx)
        val removeResult = tokenSet.remove(it)
        if (removeResult == PLACE_HOLDER || removeResult == null) {
            LOG.warn("Received a consumption event for a token ${it.ref} which was either not present in the token cache, or was not locked")
        }
    }

    private fun addTokenToCache(stateAndRef: StateAndRef<FungibleToken>) {
        // TODO after some cache size is reached, stop listening to new events and print massive warning & switch to database selection on this event
        val idx = processToken(stateAndRef)
        val tokensForTypeInfo = getTokenBucket(idx)
        val existingMark = tokensForTypeInfo.putIfAbsent(stateAndRef, PLACE_HOLDER)
        existingMark?.let {
            LOG.warn("Attempted to overwrite existing token ${stateAndRef.ref} during cache initialization, this suggests incorrect vault behaviours")
        }
    }

    @Suspendable
    fun lockTokensExternal(list: List<StateAndRef<FungibleToken>>, knownSelectionId: String) {
        list.forEach {
            val idx = processToken(it)
            val tokensForTypeInfo = getTokenBucket(idx)
            tokensForTypeInfo.replace(it, PLACE_HOLDER, knownSelectionId)
        }
    }

    @Suspendable
    fun selectTokens(
            owner: Holder,
            requiredAmount: Amount<TokenType>,
            predicate: ((StateAndRef<FungibleToken>) -> Boolean) = { true },
            allowShortfall: Boolean = false,
            autoUnlockDelay: Duration = Duration.ofMinutes(5),
            selectionId: String
    ): List<StateAndRef<FungibleToken>> {
        val bucket = getTokenBucket(owner, requiredAmount.token.tokenClass, requiredAmount.token.tokenIdentifier)
        val lockedTokens = mutableListOf<StateAndRef<FungibleToken>>()
        var amountLocked: Amount<TokenType> = requiredAmount.copy(quantity = 0)
        for (tokenStateAndRef in bucket.keys) {
            // Does the token satisfy the (optional) predicate eg. issuer?
            if (predicate.invoke(tokenStateAndRef)) {
                // if so, race to lock the token, expected oldValue = PLACE_HOLDER
                if (bucket.replace(tokenStateAndRef, PLACE_HOLDER, selectionId)) {
                    // we won the race to lock this token
                    lockedTokens.add(tokenStateAndRef)
                    val token = tokenStateAndRef.state.data
                    amountLocked += uncheckedCast(token.amount.withoutIssuer())
                    if (amountLocked >= requiredAmount) {
                        break
                    }
                }
            }
        }

        if (!allowShortfall && amountLocked < requiredAmount) {
            lockedTokens.forEach {
                unlockToken(it, selectionId)
            }
            throw InsufficientBalanceException("Insufficient spendable states identified for $requiredAmount.")
        }

        UNLOCKER.schedule({
            lockedTokens.forEach {
                unlockToken(it, selectionId)
            }
        }, autoUnlockDelay.toMillis(), TimeUnit.MILLISECONDS)

        return uncheckedCast(lockedTokens)
    }

    fun unlockToken(it: StateAndRef<FungibleToken>, selectionId: String) {
        val idx = processToken(it)
        val tokensForTypeInfo = getTokenBucket(idx)
        tokensForTypeInfo.replace(it, selectionId, PLACE_HOLDER)
    }

    private fun getTokenBucket(idx: Holder, tokenClass: Class<*>, tokenIdentifier: String): TokenBucket {
        return getTokenBucket(TokenIndex(idx, tokenClass, tokenIdentifier))
    }

    private fun getTokenBucket(idx: TokenIndex): TokenBucket {
        return cache.computeIfAbsent(idx) {
            TokenBucket()
        }
    }

}

data class TokenObserver(val initialValues: List<StateAndRef<FungibleToken>>,
                         val source: Observable<Vault.Update<FungibleToken>>,
                         val ownerProvider: ((StateAndRef<FungibleToken>, ServiceHub) -> Holder))

class TokenBucket(private val __backingMap: ConcurrentMap<StateAndRef<FungibleToken>, String> = ConcurrentHashMap()) : ConcurrentMap<StateAndRef<FungibleToken>, String> by __backingMap

data class TokenIndex(val owner: Holder, val tokenClazz: Class<*>, val tokenIdentifier: String)

class InsufficientBalanceException(message: String) : RuntimeException(message)
