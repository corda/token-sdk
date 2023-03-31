package com.r3.corda.lib.tokens.selection.memory.services

import com.r3.corda.lib.tokens.contracts.states.FungibleToken
import com.r3.corda.lib.tokens.contracts.types.IssuedTokenType
import com.r3.corda.lib.tokens.contracts.types.TokenType
import com.r3.corda.lib.tokens.contracts.utilities.withoutIssuer
import com.r3.corda.lib.tokens.selection.memory.config.InMemorySelectionConfig
import com.r3.corda.lib.tokens.selection.InsufficientBalanceException
import com.r3.corda.lib.tokens.selection.InsufficientNotLockedBalanceException
import com.r3.corda.lib.tokens.selection.memory.internal.Holder
import com.r3.corda.lib.tokens.selection.memory.internal.lookupExternalIdFromKey
import com.r3.corda.lib.tokens.selection.sortByTimeStampAscending
import net.corda.core.contracts.Amount
import net.corda.core.contracts.StateAndRef
import net.corda.core.internal.uncheckedCast
import net.corda.core.node.AppServiceHub
import net.corda.core.node.services.CordaService
import net.corda.core.node.services.ServiceLifecycleEvent
import net.corda.core.node.services.Vault
import net.corda.core.node.services.vault.DEFAULT_PAGE_NUM
import net.corda.core.node.services.vault.PageSpecification
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.core.utilities.contextLogger
import rx.Observable
import java.time.Duration
import java.util.ArrayDeque
import java.util.Collections
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write
import kotlin.math.floor

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

    var tokenLoadingStarted = false
    constructor(appServiceHub: AppServiceHub) : this(getObservableFromAppServiceHub(appServiceHub), InMemorySelectionConfig.parse(appServiceHub.getAppContext().config)) {
        appServiceHub.register(AppServiceHub.SERVICE_PRIORITY_NORMAL) { event ->
            when (event.name) {
                ServiceLifecycleEvent.BEFORE_STATE_MACHINE_START.toString(),
                ServiceLifecycleEvent.STATE_MACHINE_STARTED.toString() -> if (!tokenLoadingStarted) {
                    tokenLoadingStarted = true
                    tokenObserver.startLoading(::onVaultUpdate)
                }
            }
        }
    }

    companion object {
        val LOG = contextLogger()
        @Volatile var tokenLoadingFinished = false
        val missedConsumes = ConcurrentHashMap.newKeySet<StateAndRef<FungibleToken>>()

        private fun getObservableFromAppServiceHub(appServiceHub: AppServiceHub): TokenObserver {
            val config = appServiceHub.cordappProvider.getAppContext().config
            val configOptions: InMemorySelectionConfig = InMemorySelectionConfig.parse(config)

            if (!configOptions.enabled) {
                LOG.info("Disabling inMemory token selection - refer to documentation on how to enable")
                return TokenObserver(emptyList(), Observable.empty(), { _, _ ->
                    Holder.UnmappedIdentity()
                })
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


            val pageSize = 1000
            val (_, vaultObservable) = appServiceHub.vaultService.trackBy(
                    contractStateType = FungibleToken::class.java,
                    paging = PageSpecification(pageNumber = DEFAULT_PAGE_NUM, pageSize = pageSize),
                    criteria = QueryCriteria.VaultQueryCriteria(status = Vault.StateStatus.ALL),
                    sorting = sortByTimeStampAscending())

            // we use the UPDATER thread for two reasons
            // 1 this means we return the service before all states are loaded, and so do not hold up the node startup
            // 2 because all updates to the cache (addition / removal) are also done via UPDATER, this means that until we have finished loading all updates are buffered preventing out of order updates
            val asyncLoader = object : ((Vault.Update<FungibleToken>) -> Unit) -> Unit {
                override fun invoke(callback: (Vault.Update<FungibleToken>) -> Unit) {
                    LOG.info("Starting async token loading from vault")
                    UPDATER.submit {
                        try {
                            var shouldLoop = true
                            var startResultSetIndex = 0L
                            while (shouldLoop) {
                                val queryResult = queryVaultForStates(appServiceHub, startResultSetIndex)
                                val newlyLoadedStates = queryResult.first.toSet()
                                startResultSetIndex = queryResult.second
                                shouldLoop = queryResult.third
                                LOG.info("publishing ${newlyLoadedStates.size} to async state loading callback")
                                callback(Vault.Update(emptySet(), newlyLoadedStates))
                                callback(Vault.Update(missedConsumes, emptySet()))
                                missedConsumes.clear()
                                LOG.debug("shouldLoop=${shouldLoop}")
                            }
                            LOG.info("finished token loading")
                        } catch (t: Throwable) {
                            LOG.error("Token Loading Failed due to: ", t)
                        }
                        tokenLoadingFinished = true
                    }
                }

                private val pageSpecifications = ArrayDeque<PageSpecification>()
                private var syncPoint: SyncPoint? = null
                val initialSpecification = PageSpecification(DEFAULT_PAGE_NUM, 1999)
                val primes = intArrayOf(1009, 1013, 1019, 1021, 1031, 1033, 1039, 1049, 1051, 1061, 1063, 1069, 1087, 1091, 1093, 1097, 1103, 1109, 1117, 1123, 1129, 1151, 1153, 1163, 1171, 1181, 1187, 1193, 1201, 1213, 1217, 1223, 1229, 1231, 1237, 1249, 1259, 1277, 1279, 1283, 1289, 1291, 1297, 1301, 1303, 1307, 1319, 1321, 1327, 1361, 1367, 1373, 1381, 1399, 1409, 1423, 1427, 1429, 1433, 1439, 1447, 1451, 1453, 1459, 1471, 1481, 1483, 1487, 1489, 1493, 1499, 1511, 1523, 1531, 1543, 1549, 1553, 1559, 1567, 1571, 1579, 1583, 1597, 1601, 1607, 1609, 1613, 1619, 1621, 1627, 1637, 1657, 1663, 1667, 1669, 1693, 1697, 1699, 1709, 1721, 1723, 1733, 1741, 1747, 1753, 1759, 1777, 1783, 1787, 1789, 1801, 1811, 1823, 1831, 1847, 1861, 1867, 1871, 1873, 1877, 1879, 1889, 1901, 1907, 1913, 1931, 1933, 1949, 1951, 1973, 1979, 1987, 1993, 1997, 1999)
                val stateMetadataComparator = compareBy<Vault.StateMetadata> { it.recordedTime }.thenBy { it.ref.index }.thenBy { it.ref.txhash }
                // if item not found in list, binary search gives us the inverted search result (-insertion point - 1)
                // so convert this to the next element down from the insertion point (or -1 if no next element down)
                private fun convertBinarySearchResult(result: Int): Int = if (result < 0) {(result * -1) - 2} else result

                private fun queryVaultForStates(appServiceHub: AppServiceHub, startResultSetIndex: Long): Triple<List<StateAndRef<FungibleToken>>, Long, Boolean> {
                    var newlyLoadedStatesList: List<StateAndRef<FungibleToken>> = emptyList()
                    var sortedStateMetadata: List<Vault.StateMetadata> = emptyList()
                    var specification = getOverlappingPageSpecification(startResultSetIndex)
                    var newStartPos = -1
                    while (newStartPos == -1) {
                        val page = appServiceHub.vaultService.queryBy(
                            contractStateType = FungibleToken::class.java,
                            paging = specification,
                            criteria = QueryCriteria.VaultQueryCriteria(),
                            sorting = sortByTimeStampAscending()
                        )
                        newlyLoadedStatesList = page.states
                        sortedStateMetadata = extractSortedMetadataList(page)
                        newStartPos = isListInSync(sortedStateMetadata, specification)
                        if (newStartPos == -1) {
                            specification = popPriorPageSpecification()
                        }
                    }
                    val lastResultSetIndex = toResultSetIndex(sortedStateMetadata.lastIndex, specification)
                    val shouldLoop = sortedStateMetadata.size == specification.pageSize
                    val states = convertStateMetadataListToStateAndRefList(newlyLoadedStatesList, sortedStateMetadata.subList(newStartPos, sortedStateMetadata.size))
                    return Triple(states, lastResultSetIndex, shouldLoop)
                }

                private fun extractSortedMetadataList(page: Vault.Page<FungibleToken>): List<Vault.StateMetadata> {
                    val statesMetadata = page.statesMetadata.toMutableList()
                    Collections.sort<Vault.StateMetadata>(statesMetadata, stateMetadataComparator)
                    return statesMetadata
                }

                private fun convertStateMetadataListToStateAndRefList(states: List<StateAndRef<FungibleToken>>, statesMetadata: List<Vault.StateMetadata>): List<StateAndRef<FungibleToken>> {
                    val statesMap = states.associateBy { it.ref }
                    return statesMetadata.mapNotNull { statesMap[it.ref] }
                }

                private fun getOverlappingPageSpecification(startResultSetIndex: Long): PageSpecification {
                    val specification = if (startResultSetIndex == 0L) {
                        initialSpecification
                    }
                    else {
                        var numberPages = 1
                        var dec = 1.0
                        var pageSz = 0
                        primes.forEach { prime ->
                            val (intPart, decPart) = splitDouble(startResultSetIndex.toDouble() / prime)
                            if (decPart > 0 && dec > decPart) {
                                dec = decPart
                                numberPages = intPart + 1     // Pages number start at 1
                                pageSz = prime
                            }
                        }
                        PageSpecification(numberPages, pageSz)
                    }
                    pageSpecifications.add(specification)
                    return specification
                }
                private fun splitDouble(multiple: Double): Pair<Int, Double> {
                    val intPart = floor(multiple).toInt()
                    return Pair(intPart, multiple - intPart)
                }
                private fun isListInSync(stateMetadataList: List<Vault.StateMetadata>, specification: PageSpecification): Int {
                    if (stateMetadataList.isEmpty()) {
                        return 0
                    }
                    if (syncPoint == null) {
                        syncPoint = SyncPoint(stateMetadataList.last(), specification, toResultSetIndex(stateMetadataList.lastIndex, specification))
                        return 0
                    }
                    val localSyncPoint: SyncPoint = syncPoint!!     // We're single threaded here

                    val listIndex = toIndexWithinPage(localSyncPoint.lastKnownResultSetIndexOfStateMetadata, specification)
                    if (listIndex in 0..stateMetadataList.lastIndex && stateMetadataList[listIndex] == localSyncPoint.stateMetadata) {
                        // No change in the list
                        syncPoint = SyncPoint(stateMetadataList.last(), specification, toResultSetIndex(stateMetadataList.lastIndex, specification))
                        return listIndex+1 // Return first unread entry index, which is sync pos + 1
                    }
                    val largestSeenStateRefIndex = convertBinarySearchResult(Collections.binarySearch(stateMetadataList, localSyncPoint.stateMetadata, stateMetadataComparator))
                    if (largestSeenStateRefIndex != -1) {
                        syncPoint = SyncPoint(stateMetadataList.last(), specification, toResultSetIndex(stateMetadataList.lastIndex, specification))
                        if (largestSeenStateRefIndex == stateMetadataList.lastIndex) {
                            // We have seen the whole list already, so just return last element which we have already seen,
                            // to simplify processing. Could happen if large number of tokens gets added while we are reading in.
                            return largestSeenStateRefIndex
                        }
                        return largestSeenStateRefIndex + 1 // first unread element
                    }
                    LOG.info("Token loading has become out of sync, will re-sync")
                    return -1
                }
                private fun popPriorPageSpecification(): PageSpecification {
                    pageSpecifications.pollLast()
                    return if (pageSpecifications.isEmpty()) {
                        syncPoint = null
                        pageSpecifications.add(initialSpecification)
                        initialSpecification
                    } else {
                        pageSpecifications.last
                    }
                }

                private fun toResultSetIndex(listEntryIndex: Int, specification: PageSpecification): Long {
                    return specification.pageSize * (specification.pageNumber-1) + listEntryIndex.toLong()
                }

                private fun toIndexWithinPage(resultSetIndex: Long, specification: PageSpecification): Int {
                    return (resultSetIndex - ((specification.pageNumber-1) * specification.pageSize)).toInt()
                }
            }
            return TokenObserver(emptyList(), uncheckedCast(vaultObservable), ownerProvider, asyncLoader)
        }
        data class SyncPoint(val stateMetadata: Vault.StateMetadata, val specification: PageSpecification, var lastKnownResultSetIndexOfStateMetadata: Long)
    }

    init {
        addTokensToCache(tokenObserver.initialValues)
        tokenObserver.source.doOnError {
            LOG.error("received error from observable", it)
        }
        tokenObserver.source.subscribe(::onVaultUpdate)
    }

    private fun processToken(token: StateAndRef<FungibleToken>, indexingType: IndexingType): TokenIndex {
        val owner = tokenObserver.ownerProvider(token, indexingType)
        val type = token.state.data.amount.token.tokenType.tokenClass
        val typeId = token.state.data.amount.token.tokenType.tokenIdentifier
        return TokenIndex(owner, type, typeId)
    }

    private fun onVaultUpdate(t: Vault.Update<FungibleToken>) {
        LOG.info("received token vault update with ${t.consumed.size} consumed states and: ${t.produced.size} produced states")
        try {
            removeTokensFromCache(t.consumed)
            addTokensToCache(t.produced)
        } catch (t: Throwable) {
            //we DO NOT want to kill the observable - as a single exception will terminate the feed
            LOG.error("Failure during token cache update", t)
        }
    }

    private fun removeTokensFromCache(stateAndRefs: Collection<StateAndRef<FungibleToken>>) {
        indexViewCreationLock.read {
            for (stateAndRef in stateAndRefs) {
                val existingMark = __backingMap.remove(stateAndRef)
                if (existingMark == null) {
                    if (tokenLoadingFinished) {
                        LOG.warn("Attempted to remove existing token ${stateAndRef.ref}, but it was not found this suggests incorrect vault behaviours")
                    }
                    else if (stateAndRefs !== missedConsumes){
                        missedConsumes.add(stateAndRef)
                    }
                }
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
                    LOG.debug("Attempted to overwrite existing token ${stateAndRef.ref}, this suggests a result set re-sync occurred")
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
        // this is the running total of soft locked tokens that we encounter until the target token amount is reached
        var amountAlreadySoftLocked: Amount<TokenType> = requiredAmountWithoutIssuer.copy(quantity = 0)
        val finalPredicate = enrichedPredicate.get()
        for (tokenStateAndRef in bucket) {
            // Does the token satisfy the (optional) predicate eg. issuer?
            if (finalPredicate.invoke(tokenStateAndRef)) {
                val tokenAmount = uncheckedCast(tokenStateAndRef.state.data.amount.withoutIssuer())
                // if so, race to lock the token, expected oldValue = PLACE_HOLDER
                if (__backingMap.replace(tokenStateAndRef, PLACE_HOLDER, selectionId)) {
                    // we won the race to lock this token
                    lockedTokens.add(tokenStateAndRef)
                    amountLocked += tokenAmount
                    if (amountLocked >= requiredAmountWithoutIssuer) {
                        break
                    }
                } else {
                    amountAlreadySoftLocked += tokenAmount
                }
            }
        }

        if (!allowShortfall && amountLocked < requiredAmountWithoutIssuer) {
            lockedTokens.forEach {
                unlockToken(it, selectionId)
            }
            if (amountLocked + amountAlreadySoftLocked < requiredAmountWithoutIssuer) {
                throw InsufficientBalanceException("Insufficient spendable states identified for $requiredAmount.")
            } else {
                throw InsufficientNotLockedBalanceException("Insufficient not-locked spendable states identified for $requiredAmount.")
            }
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

class TokenObserver(val initialValues: List<StateAndRef<FungibleToken>>,
                    val source: Observable<Vault.Update<FungibleToken>>,
                    val ownerProvider: ((StateAndRef<FungibleToken>, VaultWatcherService.IndexingType) -> Holder),
                    inline val asyncLoader: ((Vault.Update<FungibleToken>) -> Unit) -> Unit = { _ -> }) {

    fun startLoading(loadingCallBack: (Vault.Update<FungibleToken>) -> Unit) {
        asyncLoader(loadingCallBack)
    }
}

class TokenBucket(set: MutableSet<StateAndRef<FungibleToken>> = ConcurrentHashMap<StateAndRef<FungibleToken>, Boolean>().keySet(true)) : MutableSet<StateAndRef<FungibleToken>> by set


data class TokenIndex(val owner: Holder, val tokenClazz: Class<*>, val tokenIdentifier: String)
