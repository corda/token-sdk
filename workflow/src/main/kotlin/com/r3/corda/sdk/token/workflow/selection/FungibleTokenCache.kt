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
import rx.Observable
import java.lang.reflect.Field
import java.math.BigDecimal
import java.security.PublicKey
import java.time.Duration
import java.util.Map
import java.util.concurrent.*
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write


@CordaService
class FungibleTokenCache(private val tokenVaultObserver: TokenVaultObserver) {

    private val cache: ConcurrentMap<PublicKey, ConcurrentMap<Class<*>, ConcurrentMap<String, TokenBucket<AmountAndStateAndRef<TokenType>, Boolean>>>> = ConcurrentHashMap()
    private val loadingCache: LoadingCache<StateRef, TransactionState<FungibleToken<out TokenType>>> = Caffeine
            .newBuilder()
            .maximumSize(100_000)
            .build { it: StateRef ->
                tokenVaultObserver.loader.invoke(it)
            }

    constructor(appServiceHub: AppServiceHub) : this(getObservableFromAppServiceHub(appServiceHub))

    companion object {
        val LOG = contextLogger()

        private fun getObservableFromAppServiceHub(appServiceHub: AppServiceHub): TokenVaultObserver {
            val pageSize = 1000
            var currentPage = DEFAULT_PAGE_NUM;
            var (existingStates, observable) = appServiceHub.vaultService.trackBy(FungibleToken::class.java, PageSpecification(pageNumber = currentPage, pageSize = pageSize))
            val listOfThings = mutableListOf<StateAndRef<FungibleToken<TokenType>>>()
            while (existingStates.states.isNotEmpty()) {
                listOfThings.addAll(existingStates.states as Iterable<StateAndRef<FungibleToken<TokenType>>>)
                existingStates = appServiceHub.vaultService.queryBy(FungibleToken::class.java, PageSpecification(pageNumber = ++currentPage, pageSize = pageSize))
            }

            return TokenVaultObserver(listOf(), observable) {
                appServiceHub.loadState(it) as TransactionState<FungibleToken<TokenType>>
            }
        }

        private val UNLOCKER: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()
        private val DEFAULT_PREDICATE: ((TransactionState<FungibleToken<TokenType>>) -> Boolean) = { true }

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
        tokenSet.write {
            if (remove(it.forRemoval()) != true) {
                LOG.warn("Received a consumption event for a token ${it.ref} which was either not present in the token cache, or was not locked")
            }
        }
        loadingCache.invalidate(it.ref)
    }

    private fun addTokenToCache(stateAndRef: StateAndRef<FungibleToken<out TokenType>>) {
        val token = stateAndRef.state.data
        val (owner, type, typeId) = processToken(token)
        val tokensForTypeInfo = getTokenSet(owner, type, typeId)
        loadingCache.put(stateAndRef.ref, stateAndRef.state)
        tokensForTypeInfo.write {
            val existingMark = putIfAbsent(stateAndRef.forAddition(tokenVaultObserver), false)
            existingMark?.let {
                LOG.warn("Attempted to overwrite existing token ${stateAndRef.ref} during cache initialization, this suggests incorrect vault behaviours")
            }
        }
    }

    private fun unlockToken(stateAndRef: StateAndRef<FungibleToken<TokenType>>) {
        val token = stateAndRef.state.data
        val (owner, type, typeId) = processToken(token)
        val tokensForTypeInfo = getTokenSet(owner, type, typeId)
        tokensForTypeInfo.write {
            replace(stateAndRef.forRemoval(), true, false)
        }
    }

    fun <T : TokenType> selectTokens(
            owner: PublicKey,
            amountRequested: Amount<IssuedTokenType<T>>,
            predicate: ((TransactionState<FungibleToken<TokenType>>) -> Boolean) = DEFAULT_PREDICATE,
            allowShortFall: Boolean = false,
            autoUnlockDelay: Duration = Duration.ofMinutes(5)
    ): List<StateAndRef<FungibleToken<T>>> {
        val tokenBucket = getTokenSet(owner, amountRequested.token.tokenType.tokenClass, amountRequested.token.tokenType.tokenIdentifier)
        val lockedTokens = mutableListOf<StateAndRef<FungibleToken<TokenType>>>()
        var amountLocked: Amount<IssuedTokenType<T>> = amountRequested.copy(quantity = 0)

        tokenBucket.read {
            for (amountAndStateRef in keys) {
                //does the token satisfy the (optional) predicate?
                val loadedState: TransactionState<FungibleToken<TokenType>>? = amountAndStateRef.transactionState
                if ((loadedState != null) && (predicate === DEFAULT_PREDICATE || predicate.invoke(loadedState))) {
                    //if so, race to lock the token, expected oldValue = false
                    if (replace(amountAndStateRef, false, true)) {
                        //we won the race to lock this token
                        lockedTokens.add(StateAndRef(loadedState, amountAndStateRef.stateRef))
                        amountLocked += amountAndStateRef.amount as Amount<IssuedTokenType<T>>
                        if (amountLocked >= amountRequested) {
                            break
                        }
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
    ): TokenBucket<AmountAndStateAndRef<TokenType>, Boolean> {
        return cache.computeIfAbsent(owner) {
            ConcurrentHashMap()
        }.computeIfAbsent(type) {
            ConcurrentHashMap()
        }.computeIfAbsent(typeId) {
            TokenBucket()
        }
    }

}

class TokenVaultObserver(val initialValues: List<StateAndRef<FungibleToken<TokenType>>>,
                         val updateSource: Observable<Vault.Update<FungibleToken<out TokenType>>>,
                         val loader: (StateRef) -> TransactionState<FungibleToken<TokenType>>?)

class AmountAndStateAndRef<T : TokenType>(val stateRef: StateRef,
                                          internal val amount: Amount<IssuedTokenType<T>>,
                                          private val loadingFunction: ((StateRef) -> TransactionState<FungibleToken<TokenType>>?)) {


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

//THIS IS NOT PRODUCTION CODE - JUST A DEMO OF REVERSED LHM

val tailField: Field = LinkedHashMap<Any, Any>().javaClass.getDeclaredField("tail").also { it.isAccessible = true }
val beforeField: Field = tailField.get(LinkedHashMap<Any, Any>().also { it.put("thisIsHAck", "thisIsNotHAck") }).javaClass.getDeclaredField("before").also { it.isAccessible = true }

class TokenBucket<K, V>(val lock: ReentrantReadWriteLock = ReentrantReadWriteLock()) {

    val __backingMap = LinkedHashMap<K, V>()

    inline fun <R> read(body: LinkedHashMap<K, V>.() -> R): R = lock.read { body(__backingMap) }
    inline fun <R> write(body: LinkedHashMap<K, V>.() -> R): R = lock.write { body(__backingMap) }


    fun <R> reversedKeys(body: Iterator<K>.() -> R): R {
        return lock.read {
            var currentPoint: Map.Entry<K, V>? = tailField.get(__backingMap) as Map.Entry<K, V>?
            val iterator = object : Iterator<K> {
                override fun hasNext(): Boolean {
                    return currentPoint != null
                }

                override fun next(): K {
                    return currentPoint?.key.also { currentPoint = beforeField.get(currentPoint) as Map.Entry<K, V>? }
                            ?: throw NoSuchElementException()
                }
            }

            body(iterator)
        }
    }


}


class InsufficientBalanceException(message: String) : RuntimeException(message)

private val defaultAmount = Amount<TokenType>(0, object : TokenType {
    override val tokenIdentifier: String
        get() = ""
    override val tokenClass: Class<*>
        get() = this::class.java
    override val displayTokenSize: BigDecimal
        get() = BigDecimal.ONE

})
private val defaultLoader: ((StateRef) -> TransactionState<FungibleToken<TokenType>>) = { throw NotImplementedError() }

private fun <T : FungibleToken<*>> StateAndRef<T>.forRemoval(): AmountAndStateAndRef<TokenType> {
    return AmountAndStateAndRef(this.ref, defaultAmount as Amount<IssuedTokenType<TokenType>>, defaultLoader)
}

private fun <T : FungibleToken<*>> StateAndRef<T>.forAddition(ob: TokenVaultObserver): AmountAndStateAndRef<TokenType> {
    return AmountAndStateAndRef(this.ref, this.state.data.amount as Amount<IssuedTokenType<TokenType>>, loadingFunction = {
        ob.loader.invoke(this.ref)
    })
}


fun main(args: Array<String>) {

    val tokenBucket = TokenBucket<Any, Any>()

    tokenBucket.write { put("Test1", "T") }
    tokenBucket.write { put("Test2", "T") }
    tokenBucket.write { put("Test3", "T") }
    tokenBucket.write { put("Test4", "T") }

    tokenBucket.reversedKeys {
        for (any in this) {
            println(any)
        }
    }

}