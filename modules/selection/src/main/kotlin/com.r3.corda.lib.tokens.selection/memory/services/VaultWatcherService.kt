package com.r3.corda.lib.tokens.selection.memory.services

import com.r3.corda.lib.tokens.contracts.states.FungibleToken
import com.r3.corda.lib.tokens.contracts.types.IssuedTokenType
import com.r3.corda.lib.tokens.contracts.types.TokenType
import com.r3.corda.lib.tokens.contracts.utilities.withoutIssuer
import com.r3.corda.lib.tokens.selection.InsufficientBalanceException
import com.r3.corda.lib.tokens.selection.memory.config.InMemorySelectionConfig
import com.r3.corda.lib.tokens.selection.memory.internal.Holder
import com.r3.corda.lib.tokens.selection.memory.internal.lookupExternalIdFromKey
import com.r3.corda.lib.tokens.selection.sortByStateRefAscending
import net.corda.core.contracts.Amount
import net.corda.core.contracts.StateAndRef
import net.corda.core.internal.uncheckedCast
import net.corda.core.node.AppServiceHub
import net.corda.core.node.services.CordaService
import net.corda.core.node.services.Vault
import net.corda.core.node.services.vault.DEFAULT_PAGE_NUM
import net.corda.core.node.services.vault.PageSpecification
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.core.utilities.contextLogger
import rx.Observable
import rx.subjects.PublishSubject
import java.time.Duration
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

val UPDATER: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()
val EMPTY_BUCKET = TokenBucket()

const val PLACE_HOLDER: String = "THIS_IS_A_PLACE_HOLDER"

@CordaService
class VaultWatcherService(private val tokenObserver: TokenObserver,
                          private val providedConfig: InMemorySelectionConfig) : SingletonSerializeAsToken() {

    private val __backingMap: ConcurrentMap<StateAndRef<FungibleToken>, String> = ConcurrentHashMap()
    private val __indexed: ConcurrentMap<Class<out Holder>, ConcurrentMap<TokenIndex, TokenBucket>> = ConcurrentHashMap(
            providedConfig.indexingStrategies.map { it.ownerType to ConcurrentHashMap<TokenIndex, TokenBucket>() }.toMap()
    )

    private val indexViewCreationLock: ReentrantReadWriteLock = ReentrantReadWriteLock()

    enum class IndexingType(val ownerType: Class<out Holder>) {

        EXTERNAL_ID(Holder.MappedIdentity::class.java),
        PUBLIC_KEY(Holder.KeyIdentity::class.java);

        companion object {
            fun fromHolder(holder: Class<out Holder>): IndexingType {
                return when (holder) {
                    Holder.MappedIdentity::class.java -> {
                        EXTERNAL_ID
                    }

                    Holder.KeyIdentity::class.java -> {
                        PUBLIC_KEY;
                    }
                    else -> throw IllegalArgumentException("Unknown Holder type: $holder")
                }
            }
        }

    }

    constructor(appServiceHub: AppServiceHub) : this(getObservableFromAppServiceHub(appServiceHub), InMemorySelectionConfig.parse(appServiceHub.getAppContext().config))

    companion object {
        val LOG = contextLogger()

        private fun getObservableFromAppServiceHub(appServiceHub: AppServiceHub): TokenObserver {
            val config = appServiceHub.cordappProvider.getAppContext().config
            val configOptions: InMemorySelectionConfig = InMemorySelectionConfig.parse(config)

            if (!configOptions.enabled) {
                LOG.info("Disabling inMemory token selection - refer to documentation on how to enable")
                return TokenObserver(emptyList(), Observable.empty()) { _, _ ->
                    Holder.UnmappedIdentity()
                }
            }

            val ownerProvider: (StateAndRef<FungibleToken>, IndexingType) -> Holder = { token, indexingType ->
                when (indexingType) {
                    IndexingType.PUBLIC_KEY -> Holder.KeyIdentity(token.state.data.holder.owningKey)
                    IndexingType.EXTERNAL_ID -> {
                        val owningKey = token.state.data.holder.owningKey
                        lookupExternalIdFromKey(owningKey, appServiceHub)
                    }
                }
            }

            val existingStatesObservable = PublishSubject.create<Vault.Update<FungibleToken>>()

            val pageSize = 1000
            var currentPage = DEFAULT_PAGE_NUM
            val (firstPage, observable) = appServiceHub.vaultService.trackBy(
                    contractStateType = FungibleToken::class.java,
                    paging = PageSpecification(pageNumber = currentPage, pageSize = pageSize),
                    criteria = QueryCriteria.VaultQueryCriteria(),
                    sorting = sortByStateRefAscending())

            val mergedObservable = existingStatesObservable.mergeWith(observable)
            // we use the UPDATER thread for two reasons
            // 1 this means we return the service before all states are loaded, and so do not hold up the node startup
            // 2 because all updates to the cache (addition / removal) are also done via UPDATER, this means that until we have finished loading all updates are buffered preventing out of order updates
            UPDATER.submit {
                existingStatesObservable.onNext(Vault.Update(emptySet(), firstPage.states.toSet()))
                var shouldLoop = true
                while (shouldLoop) {
                    val newlyLoadedStates = appServiceHub.vaultService.queryBy(
                            contractStateType = FungibleToken::class.java,
                            paging = PageSpecification(pageNumber = ++currentPage, pageSize = pageSize),
                            criteria = QueryCriteria.VaultQueryCriteria(),
                            sorting = sortByStateRefAscending()
                    )
                    existingStatesObservable.onNext(Vault.Update(emptySet(), newlyLoadedStates.states.toSet()))
                    shouldLoop = newlyLoadedStates.states.isNotEmpty()
                }
            }
            return TokenObserver(emptyList(), uncheckedCast(mergedObservable), ownerProvider)
        }
    }

    init {
        addTokensToCache(tokenObserver.initialValues)
        tokenObserver.source.subscribe(::onVaultUpdate)
    }

    fun processToken(token: StateAndRef<FungibleToken>, indexingType: IndexingType): TokenIndex {
        val owner = tokenObserver.ownerProvider(token, indexingType)
        val type = token.state.data.amount.token.tokenType.tokenClass
        val typeId = token.state.data.amount.token.tokenType.tokenIdentifier
        return TokenIndex(owner, type, typeId)
    }

    private fun onVaultUpdate(t: Vault.Update<FungibleToken>) {
        try {
            UPDATER.submit {
                removeTokensFromCache(t.consumed)
                addTokensToCache(t.produced)
            }
        } catch (t: Throwable) {
            //we DO NOT want to kill the observable - as a single exception will terminate the feed
        }

    }

    private fun removeTokensFromCache(stateAndRefs: Collection<StateAndRef<FungibleToken>>) {
        indexViewCreationLock.read {
            for (stateAndRef in stateAndRefs) {
                val existingMark = __backingMap.remove(stateAndRef)
                existingMark
                        ?: LOG.warn("Attempted to remove existing token ${stateAndRef.ref}, but it was not found this suggests incorrect vault behaviours")
                for (key in __indexed.keys) {
                    val index = processToken(stateAndRef, IndexingType.fromHolder(key))
                    val indexedViewForHolder = __indexed[key]
                    indexedViewForHolder
                            ?: LOG.warn("tried to obtain an indexed view for holder type: $key but was not found in set of indexed views")

                    val bucketForIndex: TokenBucket? = indexedViewForHolder?.get(index)
                    bucketForIndex?.remove(stateAndRef)
                }
            }
        }
    }

    private fun addTokensToCache(stateAndRefs: Collection<StateAndRef<FungibleToken>>) {
        indexViewCreationLock.read {
            for (stateAndRef in stateAndRefs) {
                val existingMark = __backingMap.putIfAbsent(stateAndRef, PLACE_HOLDER)
                existingMark?.let {
                    LOG.warn("Attempted to overwrite existing token ${stateAndRef.ref}, this suggests incorrect vault behaviours")
                }
                for (key in __indexed.keys) {
                    val index = processToken(stateAndRef, IndexingType.fromHolder(key))
                    val indexedViewForHolder = __indexed[key]
                            ?: throw IllegalStateException("tried to obtain an indexed view for holder type: $key but was not found in set of indexed views")
                    val bucketForIndex: TokenBucket = indexedViewForHolder.computeIfAbsent(index) {
                        TokenBucket()
                    }
                    bucketForIndex.add(stateAndRef)
                }
            }
        }
    }

    private fun getOrCreateIndexViewForHolderType(holderType: Class<out Holder>): ConcurrentMap<TokenIndex, TokenBucket> {
        return __indexed[holderType] ?: indexViewCreationLock.write {
            __indexed[holderType] ?: generateNewIndexedView(holderType)
        }
    }

    private fun generateNewIndexedView(holderType: Class<out Holder>): ConcurrentMap<TokenIndex, TokenBucket> {
        val indexedViewForHolder: ConcurrentMap<TokenIndex, TokenBucket> = ConcurrentHashMap()
        for (stateAndRef in __backingMap.keys) {
            val index = processToken(stateAndRef, IndexingType.fromHolder(holderType))
            val bucketForIndex: TokenBucket = indexedViewForHolder.computeIfAbsent(index) {
                TokenBucket()
            }
            bucketForIndex.add(stateAndRef)
        }
        __indexed[holderType] = indexedViewForHolder
        return indexedViewForHolder
    }

    fun lockTokensExternal(list: List<StateAndRef<FungibleToken>>, knownSelectionId: String) {
        list.forEach {
            __backingMap.replace(it, PLACE_HOLDER, knownSelectionId)
        }
    }

    fun selectTokens(
            owner: Holder,
            requiredAmount: Amount<TokenType>,
            predicate: ((StateAndRef<FungibleToken>) -> Boolean) = { true },
            allowShortfall: Boolean = false,
            autoUnlockDelay: Duration = Duration.ofMinutes(5),
            selectionId: String
    ): List<StateAndRef<FungibleToken>> {
        //we have to handle both cases
        //1 when passed a raw TokenType - it's likely that the selecting entity does not care about the issuer and so we cannot constrain all selections to using IssuedTokenType
        //2 when passed an IssuedTokenType - it's likely that the selecting entity does care about the issuer, and so we must filter all tokens which do not match the issuer.
        val enrichedPredicate: AtomicReference<(StateAndRef<FungibleToken>) -> Boolean> = AtomicReference(if (requiredAmount.token is IssuedTokenType) {
            val issuer = (requiredAmount.token as IssuedTokenType).issuer
            { token ->
                predicate(token) && token.state.data.issuer == issuer
            }
        } else {
            predicate
        })

        val lockedTokens = mutableListOf<StateAndRef<FungibleToken>>()
        val bucket: Iterable<StateAndRef<FungibleToken>> = if (owner is Holder.TokenOnly) {
            val currentPredicate = enrichedPredicate.get()
            //why do we do this? It doesn't really make sense to index on token type, as it's very likely that there will be very few types of tokens in a given vault
            //so instead of relying on an indexed view, we can create a predicate on the fly which will constrain the selection to the correct token type
            //we will revisit in future if this assumption turns out to be wrong
            enrichedPredicate.set {
                val stateTokenType = it.state.data.tokenType
                currentPredicate(it) &&
                        stateTokenType.fractionDigits == requiredAmount.token.fractionDigits &&
                        requiredAmount.token.tokenClass == stateTokenType.tokenClass &&
                        requiredAmount.token.tokenIdentifier == stateTokenType.tokenIdentifier
            }
            __backingMap.keys
        } else {
            val indexedView = getOrCreateIndexViewForHolderType(owner.javaClass)
            getTokenBucket(owner, requiredAmount.token.tokenClass, requiredAmount.token.tokenIdentifier, indexedView)
        }

        val requiredAmountWithoutIssuer = requiredAmount.withoutIssuer()
        var amountLocked: Amount<TokenType> = requiredAmountWithoutIssuer.copy(quantity = 0)
        val finalPredicate = enrichedPredicate.get()
        for (tokenStateAndRef in bucket) {
            // Does the token satisfy the (optional) predicate eg. issuer?
            if (finalPredicate.invoke(tokenStateAndRef)) {
                // if so, race to lock the token, expected oldValue = PLACE_HOLDER
                if (__backingMap.replace(tokenStateAndRef, PLACE_HOLDER, selectionId)) {
                    // we won the race to lock this token
                    lockedTokens.add(tokenStateAndRef)
                    val token = tokenStateAndRef.state.data
                    amountLocked += uncheckedCast(token.amount.withoutIssuer())
                    if (amountLocked >= requiredAmountWithoutIssuer) {
                        break
                    }
                }
            }
        }

        if (!allowShortfall && amountLocked < requiredAmountWithoutIssuer) {
            lockedTokens.forEach {
                unlockToken(it, selectionId)
            }
            throw InsufficientBalanceException("Insufficient spendable states identified for $requiredAmount.")
        }

        UPDATER.schedule({
            lockedTokens.forEach {
                unlockToken(it, selectionId)
            }
        }, autoUnlockDelay.toMillis(), TimeUnit.MILLISECONDS)

        return uncheckedCast(lockedTokens)
    }

    fun unlockToken(it: StateAndRef<FungibleToken>, selectionId: String) {
        __backingMap.replace(it, selectionId, PLACE_HOLDER)
    }

    private fun getTokenBucket(idx: Holder,
                               tokenClass: Class<*>,
                               tokenIdentifier: String,
                               mapToSelectFrom: ConcurrentMap<TokenIndex, TokenBucket>): TokenBucket {
        return mapToSelectFrom[TokenIndex(idx, tokenClass, tokenIdentifier)] ?: EMPTY_BUCKET
    }

}

data class TokenObserver(val initialValues: List<StateAndRef<FungibleToken>>,
                         val source: Observable<Vault.Update<FungibleToken>>,
                         val ownerProvider: ((StateAndRef<FungibleToken>, VaultWatcherService.IndexingType) -> Holder))

class TokenBucket(set: MutableSet<StateAndRef<FungibleToken>> = ConcurrentHashMap<StateAndRef<FungibleToken>, Boolean>().keySet(true)) : MutableSet<StateAndRef<FungibleToken>> by set


data class TokenIndex(val owner: Holder, val tokenClazz: Class<*>, val tokenIdentifier: String)
