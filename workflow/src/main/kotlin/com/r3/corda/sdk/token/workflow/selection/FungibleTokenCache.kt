package com.r3.corda.sdk.token.workflow.selection

import com.github.benmanes.caffeine.cache.Caffeine
import com.github.benmanes.caffeine.cache.LoadingCache
import com.r3.corda.sdk.token.contracts.states.FungibleToken
import com.r3.corda.sdk.token.contracts.types.IssuedTokenType
import com.r3.corda.sdk.token.contracts.types.TokenType
import net.corda.core.contracts.*
import net.corda.core.node.AppServiceHub
import net.corda.core.node.services.CordaService
import net.corda.core.node.services.Vault
import net.corda.core.node.services.vault.DEFAULT_PAGE_NUM
import net.corda.core.node.services.vault.PageSpecification
import net.corda.core.utilities.contextLogger
import org.checkerframework.checker.nullness.qual.Nullable
import rx.Observable
import java.math.BigDecimal
import java.security.PublicKey
import java.time.Duration
import java.util.concurrent.*


@CordaService
class FungibleTokenCache(private val tokenVaultObserver: TokenVaultObserver) {

    private val cache: ConcurrentMap<PublicKey, ConcurrentMap<Class<*>, ConcurrentMap<String, ConcurrentMap<AmountAndStateAndRef<TokenType>, Boolean>>>> = ConcurrentHashMap()

    constructor(appServiceHub: AppServiceHub) : this(getObservableFromAppServiceHub(appServiceHub))

    companion object {
        val LOG = contextLogger()

        private fun getObservableFromAppServiceHub(appServiceHub: AppServiceHub): TokenVaultObserver {

            val loadingCache: LoadingCache<StateRef, TransactionState<FungibleToken<TokenType>>> = Caffeine
                    .newBuilder()
                    .maximumSize(100_000)
                    .build{it: StateRef ->
                        appServiceHub.loadState(it) as TransactionState<FungibleToken<TokenType>>
                    }

            val pageSize = 1000
            var currentPage = DEFAULT_PAGE_NUM;
            var (existingStates, observable) = appServiceHub.vaultService.trackBy(FungibleToken::class.java, PageSpecification(pageNumber = currentPage, pageSize = pageSize))
            val listOfThings = mutableListOf<StateAndRef<FungibleToken<TokenType>>>()
            while (existingStates.states.isNotEmpty()) {
                listOfThings.addAll(existingStates.states as Iterable<StateAndRef<FungibleToken<TokenType>>>)
                existingStates = appServiceHub.vaultService.queryBy(FungibleToken::class.java, PageSpecification(pageNumber = ++currentPage, pageSize = pageSize))
            }
            return TokenVaultObserver(listOfThings, observable){
                loadingCache.get(it)
            }
        }

        private val UNLOCKER: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()
        private val DEFAULT_PREDICATE: ((TransactionState<FungibleToken<TokenType>>) -> Boolean) = {true}

    }


    //owner -> tokenClass -> tokenIdentifier -> List
    init {
        tokenVaultObserver.initialValues.forEach(::addTokenToCache)
        tokenVaultObserver.updateSource.subscribe(::onVaultUpdate)
    }

    private fun onVaultUpdate(t: Vault.Update<FungibleToken<out TokenType>>) {
        t.consumed.forEach(::removeTokenFromCache)
        t.produced.forEach(::addTokenToCache)
    }

    private fun removeTokenFromCache(it: StateAndRef<FungibleToken<*>>) {
        val (owner, type, typeId) = processToken(it.state.data)
        val tokenSet = getTokenSet(owner, type, typeId)
        if (tokenSet.remove(it.forRemoval()) != true) {
            LOG.warn("Received a consumption event for a token ${it.ref} which was either not present in the token cache, or was not locked")
        }
    }

    private fun addTokenToCache(stateAndRef: StateAndRef<FungibleToken<out TokenType>>) {
        val token = stateAndRef.state.data
        val (owner, type, typeId) = processToken(token)
        val tokensForTypeInfo = getTokenSet(owner, type, typeId)
        val existingMark = tokensForTypeInfo.putIfAbsent(stateAndRef.forAddition(tokenVaultObserver), false)
        existingMark?.let {
            LOG.warn("Attempted to overwrite existing token ${stateAndRef.ref} during cache initialization, this suggests incorrect vault behaviours")
        }
    }

    private fun unlockToken(stateAndRef: StateAndRef<FungibleToken<TokenType>>) {
        val token = stateAndRef.state.data
        val (owner, type, typeId) = processToken(token)
        val tokensForTypeInfo = getTokenSet(owner, type, typeId)
        tokensForTypeInfo.replace(stateAndRef.forAddition(tokenVaultObserver), true, false)
    }

    fun <T : TokenType> selectTokens(
            owner: PublicKey,
            amountRequested: Amount<IssuedTokenType<T>>,
            predicate: ((TransactionState<FungibleToken<TokenType>>) -> Boolean) = DEFAULT_PREDICATE,
            allowShortFall: Boolean = false,
            autoUnlockDelay: Duration = Duration.ofMinutes(5)
    ): List<StateAndRef<FungibleToken<T>>> {
        val set = getTokenSet(owner, amountRequested.token.tokenType.tokenClass, amountRequested.token.tokenType.tokenIdentifier)
        val lockedTokens = mutableListOf<StateAndRef<FungibleToken<TokenType>>>()
        var amountLocked: Amount<IssuedTokenType<T>> = amountRequested.copy(quantity = 0)
        for (amountAndStateRef in set.keys) {
            //does the token satisfy the (optional) predicate?
            var loadedState: TransactionState<FungibleToken<TokenType>>? = amountAndStateRef.transactionState
            if ((loadedState != null) && (predicate === DEFAULT_PREDICATE || predicate.invoke(loadedState))) {
                //if so, race to lock the token, expected oldValue = false
                if (set.replace(amountAndStateRef, false, true)) {
                    //we won the race to lock this token
                    lockedTokens.add(StateAndRef(loadedState, amountAndStateRef.stateRef))
                    amountLocked += amountAndStateRef.amount as Amount<IssuedTokenType<T>>
                    if (amountLocked >= amountRequested) {
                        break
                    }
                }
            }
        }
        if (!allowShortFall && amountLocked < amountRequested) {
            throw InsufficientBalanceException("Could not find enough tokens to satisfy token request")
        }

        UNLOCKER.schedule({
            lockedTokens.forEach { unlockToken(it) }
        }, autoUnlockDelay.toMillis(), TimeUnit.MILLISECONDS)

        return lockedTokens as List<StateAndRef<FungibleToken<T>>>
    }

    private fun processToken(token: FungibleToken<*>): Triple<PublicKey, Class<*>, String> {
        val owner = token.holder.owningKey
        val type = token.amount.token.tokenType.tokenClass
        val typeId = token.amount.token.tokenType.tokenIdentifier
        return Triple(owner, type, typeId)
    }

    fun getTokenSet(
            owner: PublicKey,
            type: Class<*>,
            typeId: String
    ): ConcurrentMap<AmountAndStateAndRef<TokenType>, Boolean> {
        return cache.computeIfAbsent(owner) {
            ConcurrentHashMap()
        }.computeIfAbsent(type) {
            ConcurrentHashMap()
        }.computeIfAbsent(typeId) {
            ConcurrentHashMap()
        }
    }

}

class TokenVaultObserver(val initialValues: List<StateAndRef<FungibleToken<TokenType>>>,
                         val updateSource: Observable<Vault.Update<FungibleToken<out TokenType>>>,
                         val loader: (StateRef) -> @Nullable TransactionState<FungibleToken<TokenType>>?)

class AmountAndStateAndRef<T : TokenType>(val stateRef: StateRef,
                                          internal val amount: Amount<IssuedTokenType<T>>,
                                          private val loadingFunction: ((StateRef) -> TransactionState<FungibleToken<TokenType>>?)){


    val transactionState: TransactionState<FungibleToken<T>>?
    get() {
        return loadingFunction.invoke(stateRef) as TransactionState<FungibleToken<T>>?
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as AmountAndStateAndRef<*>
        if (stateRef != other.stateRef) return false
        return true
    }

    override fun hashCode(): Int {
        return stateRef.hashCode()
    }

}

class InsufficientBalanceException(message: String) : RuntimeException(message)

private val defaultAmount = Amount<TokenType>(0, object : TokenType{
    override val tokenIdentifier: String
        get() = ""
    override val tokenClass: Class<*>
        get() = this::class.java
    override val displayTokenSize: BigDecimal
        get() = BigDecimal.ONE

})
private val defaultLoader: ((StateRef) -> TransactionState<FungibleToken<TokenType>>) = {throw NotImplementedError()}

private fun <T: ContractState> StateAndRef<T>.forRemoval(): AmountAndStateAndRef<TokenType> {
    return AmountAndStateAndRef(this.ref, defaultAmount as Amount<IssuedTokenType<TokenType>>, defaultLoader)
}

private fun <T: FungibleToken<*>> StateAndRef<T>.forAddition(ob: TokenVaultObserver): AmountAndStateAndRef<TokenType> {
    return AmountAndStateAndRef(this.ref, this.state.data.amount as Amount<IssuedTokenType<TokenType>>, loadingFunction = {
        ob.loader.invoke(this.ref)
    })
}