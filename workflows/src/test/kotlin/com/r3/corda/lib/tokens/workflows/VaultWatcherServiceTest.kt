package com.r3.corda.lib.tokens.workflows

import com.r3.corda.lib.tokens.contracts.states.FungibleToken
import com.r3.corda.lib.tokens.contracts.types.IssuedTokenType
import com.r3.corda.lib.tokens.contracts.types.TokenType
import com.r3.corda.lib.tokens.contracts.utilities.issuedBy
import com.r3.corda.lib.tokens.contracts.utilities.withoutIssuer
import com.r3.corda.lib.tokens.money.BTC
import com.r3.corda.lib.tokens.money.GBP
import com.r3.corda.lib.tokens.selection.InsufficientBalanceException
import com.r3.corda.lib.tokens.selection.memory.config.InMemorySelectionConfig
import com.r3.corda.lib.tokens.selection.memory.internal.Holder
import com.r3.corda.lib.tokens.selection.memory.services.TokenObserver
import com.r3.corda.lib.tokens.selection.memory.services.VaultWatcherService
import net.corda.core.contracts.Amount
import net.corda.core.contracts.StateAndRef
import net.corda.core.contracts.StateRef
import net.corda.core.contracts.TransactionState
import net.corda.core.crypto.Crypto
import net.corda.core.crypto.SecureHash
import net.corda.core.identity.AnonymousParty
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.internal.uncheckedCast
import net.corda.core.node.services.Vault
import net.corda.core.utilities.getOrThrow
import net.corda.nodeapi.internal.persistence.CordaPersistence
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.core.*
import net.corda.testing.node.MockServices
import org.hamcrest.CoreMatchers.*
import org.hamcrest.Matchers.greaterThanOrEqualTo
import org.hamcrest.Matchers.`is`
import org.hamcrest.Matchers.`in`
import org.hamcrest.MatcherAssert.assertThat
import org.junit.*
import rx.subjects.PublishSubject
import java.security.PublicKey
import java.text.NumberFormat
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

class VaultWatcherServiceTest {
    @Rule
    @JvmField
    val testSerialization = SerializationEnvironmentRule()

    private lateinit var services: MockServices
    private lateinit var database: CordaPersistence
    @Before
    fun setupServices() {
        val mockDbAndServices = MockServices.makeTestDatabaseAndPersistentServices(
                cordappPackages = listOf("com.r3.corda.lib.tokens.workflows"),
                initialIdentity = TestIdentity(CordaX500Name("Test", "London", "GB")),
                networkParameters = testNetworkParameters(minimumPlatformVersion = 6),
                moreIdentities = emptySet(),
                moreKeys = emptySet()
        )
        services = mockDbAndServices.second
        database = mockDbAndServices.first
    }

    @Test(timeout = 300_000)
    fun `should accept token into the cache`() {

        val (VaultObserver, observable) = getDefaultVaultObserver()

        val vaultWatcherService = VaultWatcherService(VaultObserver, InMemorySelectionConfig.defaultConfig())
        val owner = Crypto.generateKeyPair(Crypto.DEFAULT_SIGNATURE_SCHEME).public
        val amountToIssue: Long = 100
        val stateAndRef = createNewFiatCurrencyTokenRef(amountToIssue, owner, notary1, issuer1, GBP, observable, database)
        val selectedTokens = vaultWatcherService.selectTokens(Holder.KeyIdentity(owner), Amount(5, GBP), selectionId = "abc")
        assertThat(selectedTokens, `is`(equalTo(listOf(stateAndRef))))
    }

    @Test(timeout = 300_000)
    fun `should select enough tokens to satisfy the requested amount`() {
        val (VaultObserver,
                observable) = getDefaultVaultObserver()

        val vaultWatcherService = VaultWatcherService(VaultObserver, InMemorySelectionConfig.defaultConfig())
        val owner = Crypto.generateKeyPair(Crypto.DEFAULT_SIGNATURE_SCHEME).public
        for (i in 1..100) {
            createNewFiatCurrencyTokenRef(((Math.random() * 10) + 1).toLong(), owner, notary1, issuer1, GBP, observable, database)
        }

        val selectedTokens = vaultWatcherService.selectTokens(Holder.KeyIdentity(owner), Amount(45, GBP), selectionId = "abc")
        assertThat(selectedTokens.map { it.state.data.amount.quantity }.sumOf { it }, `is`(greaterThanOrEqualTo(45L)))
    }

    @Test(expected = InsufficientBalanceException::class)
    fun `should not allow double selection of token in the cache`() {
        val (VaultObserver,
                observable) = getDefaultVaultObserver()

        val vaultWatcherService = VaultWatcherService(VaultObserver, InMemorySelectionConfig.defaultConfig())

        val owner = Crypto.generateKeyPair(Crypto.DEFAULT_SIGNATURE_SCHEME).public
        val amountToIssue: Long = 100
        val stateAndRef = createNewFiatCurrencyTokenRef(amountToIssue, owner, notary1, issuer1, GBP, observable, database)

        val selectedTokens = vaultWatcherService.selectTokens(Holder.TokenOnly(), Amount(5, IssuedTokenType(issuer1, GBP)), selectionId = "abc")
        assertThat(selectedTokens, `is`(equalTo(listOf(stateAndRef))))
        vaultWatcherService.selectTokens(Holder.KeyIdentity(owner), Amount(5, IssuedTokenType(issuer1, GBP)), selectionId = "abc")
    }

    @Test(timeout = 300_000)
    fun `should allow filtering based on arbitary predicate`() {
        val (VaultObserver,
                observable) = getDefaultVaultObserver()

        val vaultWatcherService = VaultWatcherService(VaultObserver, InMemorySelectionConfig.defaultConfig())

        val owner = Crypto.generateKeyPair(Crypto.DEFAULT_SIGNATURE_SCHEME).public
        val amountToIssue: Long = 100
        val stateAndRef = createNewFiatCurrencyTokenRef(amountToIssue, owner, notary1, issuer1, GBP, observable, database)
        createNewFiatCurrencyTokenRef(amountToIssue, owner, notary2, issuer1, GBP, observable, database)
        createNewFiatCurrencyTokenRef(amountToIssue + 1, owner, notary2, issuer1, GBP, observable, database)
        createNewFiatCurrencyTokenRef(amountToIssue + 2, owner, notary2, issuer1, GBP, observable, database)
        createNewFiatCurrencyTokenRef(amountToIssue + 3, owner, notary2, issuer1, GBP, observable, database)

        val selectedTokens = vaultWatcherService.selectTokens(Holder.KeyIdentity(owner), Amount(5, GBP), {
            it.state.notary == notary1
        }, selectionId = "abc")

        assertThat(selectedTokens, `is`(equalTo(listOf(stateAndRef))))

        val notary2Selected = vaultWatcherService.selectTokens(Holder.KeyIdentity(owner), Amount(amountToIssue * 2, GBP), {
            it.state.notary == notary2
        }, selectionId = "abc")

        assertThat(notary2Selected, `is`(equalTo(notary2Selected.filter { it.state.notary == notary2 })))
    }

    @Test(timeout = 300_000)
    fun `should remove states which have been spent`() {
        val (VaultObserver,
                observable) = getDefaultVaultObserver()

        val vaultWatcherService = VaultWatcherService(VaultObserver, InMemorySelectionConfig.defaultConfig())
        val owner = Crypto.generateKeyPair(Crypto.DEFAULT_SIGNATURE_SCHEME).public

        for (i in 1..100) {
            createNewFiatCurrencyTokenRef((Math.random() * 10).toLong(), owner, notary1, issuer1, GBP, observable, database)
        }

        val selectedTokens = vaultWatcherService.selectTokens(Holder.KeyIdentity(owner), Amount(50, GBP), selectionId = "abc").toSet()
        val (spentInputs, _) = executeTx(
                selectedTokens,
                Amount(50, GBP),
                Crypto.generateKeyPair(Crypto.DEFAULT_SIGNATURE_SCHEME).public,
                observable = observable,
                database = database)


        val selectedTokensAfterSpend = vaultWatcherService.selectTokens(Holder.KeyIdentity(owner), Amount(10000000000, GBP), allowShortfall = true, selectionId = "abc")

        assertThat(spentInputs, everyItem(not(`is`(`in`(selectedTokensAfterSpend)))))
    }

    @Test(timeout = 300_000)
    fun `should remove states which have been spent via redeem`() {
        val (VaultObserver,
                observable) = getDefaultVaultObserver()

        val vaultWatcherService = VaultWatcherService(VaultObserver, InMemorySelectionConfig.defaultConfig())
        val owner = Crypto.generateKeyPair(Crypto.DEFAULT_SIGNATURE_SCHEME).public

        for (i in 1..100) {
            createNewFiatCurrencyTokenRef((Math.random() * 10).toLong(), owner, notary1, issuer1, GBP, observable, database)
        }

        val selectedTokens = vaultWatcherService.selectTokens(Holder.KeyIdentity(owner), Amount(50, GBP), selectionId = "abc").toSet()
        executeConsumption(selectedTokens, observable, database)

        val selectedTokensAfterSpend = vaultWatcherService.selectTokens(Holder.KeyIdentity(owner), Amount(10000000000, GBP), allowShortfall = true, selectionId = "abc")
        assertThat(selectedTokens, everyItem(not(`is`(`in`(selectedTokensAfterSpend)))))
    }

    @Test(timeout = 300_000)
    fun `should allow selection by multiple holder types`() {

        val accountsAndKeys = (0 until 10).map {
            val account = UUID.randomUUID()
            val keysForAccount = (0 until 5).map {
                Crypto.generateKeyPair(Crypto.DEFAULT_SIGNATURE_SCHEME).public
            }
            account to keysForAccount
        }.toMap()

        val keyToAccount = accountsAndKeys.flatMap { accountEntry ->
            accountEntry.value.map {
                it to accountEntry.key
            }
        }.toMap()

        val ownerProvider = object : (StateAndRef<FungibleToken>, VaultWatcherService.IndexingType) -> Holder {
            override fun invoke(tokenState: StateAndRef<FungibleToken>, indexingType: VaultWatcherService.IndexingType): Holder {
                return when (indexingType) {
                    VaultWatcherService.IndexingType.EXTERNAL_ID -> {
                        Holder.MappedIdentity(keyToAccount[tokenState.state.data.holder.owningKey]
                                ?: error("should never happen"))
                    }
                    VaultWatcherService.IndexingType.PUBLIC_KEY -> {
                        Holder.KeyIdentity(tokenState.state.data.holder.owningKey)
                    }
                }
            }
        }

        val observable = PublishSubject.create<Vault.Update<FungibleToken>>()

        val vaultWatcherService = VaultWatcherService(TokenObserver(emptyList(), observable, ownerProvider), InMemorySelectionConfig.defaultConfig())
        val keyToTokenMap = HashMap<PublicKey, StateAndRef<FungibleToken>>()

        val accountToIssuedTokensMap = accountsAndKeys.map {
            val tokensIssuedToAccount = it.value.map { key ->
                val token = createNewFiatCurrencyTokenRef(1, key, notary1, issuer1, GBP, observable, database)
                keyToTokenMap[key] = token
                token
            }
            it.key to tokensIssuedToAccount
        }.toMap()


        //check we can select by account
        for (accountsAndKeyEntry in accountsAndKeys) {
            val account = accountsAndKeyEntry.key
            val selectedTokens = vaultWatcherService.selectTokens(Holder.MappedIdentity(account), Amount(10000000000, GBP), allowShortfall = true, selectionId = "CHEESEY_BITES").sortedBy { it.toString() }
            val expectedTokens = accountToIssuedTokensMap[account]!!.sortedBy { it.toString() }
            assertThat(selectedTokens, `is`(equalTo(expectedTokens)))
            expectedTokens.forEach {
                vaultWatcherService.unlockToken(it, "CHEESEY_BITES")
            }
        }

        //check we can select by owning key
        for (keyToTokenEntry in keyToTokenMap) {
            val key = keyToTokenEntry.key
            val selectedTokens = vaultWatcherService.selectTokens(Holder.KeyIdentity(key), Amount(10000000000, GBP), allowShortfall = true, selectionId = "CHEESEY_BITES").sortedBy { it.toString() }
            val expectedTokens = listOf(keyToTokenEntry.value)
            assertThat(selectedTokens, `is`(equalTo(expectedTokens)))
        }
    }

    @Test(timeout = 300_000)
    @Ignore
    fun `very basic memory checking state scales`() {

        val (VaultObserver,
                observable) = getDefaultVaultObserver()

        VaultWatcherService(VaultObserver, InMemorySelectionConfig.defaultConfig())
        val owner = Crypto.generateKeyPair(Crypto.DEFAULT_SIGNATURE_SCHEME).public


        for (j in 1..1000) {
            for (i in 1..1000) {
                createNewFiatCurrencyTokenRef((Math.random() * 10).toLong(), owner, notary1, issuer1, GBP, observable, database)
            }
            println("${j * 1000} total states = ${Runtime.getRuntime().totalMemory() / (1024 * 1024)}MB")
        }

    }

    @Test(timeout = 300_000)
    @Ignore
    fun `very basic memory checking owner scales`() {
        val (VaultObserver, observable) = getDefaultVaultObserver()

        VaultWatcherService(VaultObserver, InMemorySelectionConfig.defaultConfig())

        for (j in 1..50_000) {
            val owner = Crypto.generateKeyPair(Crypto.DEFAULT_SIGNATURE_SCHEME).public
            for (i in 1..5) {
                createNewFiatCurrencyTokenRef((Math.random() * 10).toLong(), owner, notary1, issuer1, GBP, observable, database)
            }
            println("$j owners ${NumberFormat.getInstance().format(j * 5)}, total states = ${Runtime.getRuntime().totalMemory() / (1024 * 1024)}MB")
        }
    }

    @Test(timeout = 300_000)
    fun `should support concurrent requests and inserts for tokens`() {
        val (VaultObserver,
                observable) = getDefaultVaultObserver()
        val vaultWatcherService = VaultWatcherService(VaultObserver, InMemorySelectionConfig.defaultConfig())

        val executor = Executors.newFixedThreadPool(10)
        val owner1 = Crypto.generateKeyPair(Crypto.DEFAULT_SIGNATURE_SCHEME).public
        val owner2 = Crypto.generateKeyPair(Crypto.DEFAULT_SIGNATURE_SCHEME).public
        val spendTracker = ConcurrentHashMap<StateRef, AtomicInteger>()

        val btcInserter = Runnable {
            for (i in 1..1000) {
                val owner = if (Math.random() > 0.5) {
                    owner1
                } else {
                    owner2
                }
                createNewDigitalCurrencyTokenRef(((Math.random() * 1000) + 1).toLong(), owner, notary1, issuer1, observable, database)
                Thread.sleep(2)
            }
        }

        val gbpInserter = Runnable {
            for (i in 1..1000) {
                val owner = if (Math.random() > 0.5) {
                    owner1
                } else {
                    owner2
                }
                createNewFiatCurrencyTokenRef(((Math.random() * 1000) + 1).toLong(), owner, notary1, issuer1, GBP, observable, database)
                Thread.sleep(2)
            }
        }


        val btcSpender = Runnable {
            var selects = 0
            while (selects < 10_000) {
                val (owner, newOwner) = if (Math.random() > 0.5) {
                    owner1 to owner2
                } else {
                    owner2 to owner1
                }
                try {
                    val amountRequested = Amount((10 * Math.random()).toLong() + 1, BTC)
                    val selectedTokens = vaultWatcherService.selectTokens(Holder.KeyIdentity(owner), amountRequested, selectionId = "abc").toSet()
                    executeTx(
                            selectedTokens,
                            amountRequested,
                            newOwner,
                            observable = observable,
                            spendTracker = spendTracker,
                            database = database
                    )

                    selects++
                } catch (e: InsufficientBalanceException) {
                }
            }
        }

        val gbpSpender = Runnable {
            var selects = 0
            while (selects < 10_000) {
                val (owner, newOwner) = if (Math.random() > 0.5) {
                    owner1 to owner2
                } else {
                    owner2 to owner1
                }
                try {
                    val amountRequested = Amount(10, GBP)
                    val selectedTokens = vaultWatcherService.selectTokens(Holder.KeyIdentity(owner), amountRequested, selectionId = "abc").toSet()
                    executeTx(
                            selectedTokens,
                            amountRequested,
                            newOwner,
                            observable = observable,
                            spendTracker = spendTracker,
                            database = database
                    )
                    selects++
                } catch (e: InsufficientBalanceException) {
                }
            }
        }


        val btcInsertFuture = executor.submit(btcInserter)
        val gbpInsertFuture = executor.submit(gbpInserter)
        val btcSpendFuture = executor.submit(btcSpender)
        val btcSpendFuture1 = executor.submit(btcSpender)
        val btcSpendFuture2 = executor.submit(btcSpender)
        val gbpSpendFuture = executor.submit(gbpSpender)
        val gbpSpendFuture1 = executor.submit(gbpSpender)
        val gbpSpendFuture2 = executor.submit(gbpSpender)

        btcInsertFuture.getOrThrow()
        gbpInsertFuture.getOrThrow()
        btcSpendFuture.getOrThrow()
        btcSpendFuture1.getOrThrow()
        btcSpendFuture2.getOrThrow()
        gbpSpendFuture.getOrThrow()
        gbpSpendFuture1.getOrThrow()
        gbpSpendFuture.getOrThrow()
        gbpSpendFuture2.getOrThrow()


        assertThat(spendTracker.filter { it.value.get() > 1 }.toList(), `is`(equalTo(emptyList())))
    }

    companion object {

        val issuer1 = TestIdentity(CordaX500Name("BANK", "London", "GB")).party
        val notary1 = TestIdentity(CordaX500Name("Notary1", "London", "GB")).party
        val notary2 = TestIdentity(CordaX500Name("Notary2", "London", "GB")).party

        private fun createNewTokenRef(amount: Amount<TokenType>,
                                      owner: PublicKey,
                                      notary: Party,
                                      issuer: Party,
                                      observable: PublishSubject<Vault.Update<FungibleToken>>? = null,
                                      txHash: SecureHash = SecureHash.randomSHA256(),
                                      index: Int = 0,
                                      database: CordaPersistence): StateAndRef<FungibleToken> {
            val thing = FungibleToken(amount issuedBy issuer, AnonymousParty(owner))
            val state = TransactionState(data = thing, notary = notary)
            val stateRef = StateRef(txHash, index)
            return StateAndRef(state, stateRef)
                    .also { database.transaction { observable?.onNext(Vault.Update(emptySet(), produced = setOf(it))) } }
        }

        fun createNewFiatCurrencyTokenRef(amountToIssue: Long,
                                          owner: PublicKey,
                                          notary: Party,
                                          issuer: Party,
                                          currency: TokenType = GBP,
                                          observable: PublishSubject<Vault.Update<FungibleToken>>? = null,
                                          database: CordaPersistence): StateAndRef<FungibleToken> {
            val amount = Amount(amountToIssue, currency)
            return createNewTokenRef(amount, owner, notary, issuer, observable, database = database)
        }

        private fun createNewDigitalCurrencyTokenRef(amountToIssue: Long,
                                                     owner: PublicKey,
                                                     notary: Party,
                                                     issuer: Party,
                                                     observable: PublishSubject<Vault.Update<FungibleToken>>? = null,
                                                     database: CordaPersistence): StateAndRef<FungibleToken> {
            val amount = Amount(amountToIssue, BTC)
            return createNewTokenRef(amount, owner, notary, issuer, observable, database = database)
        }

        fun executeTx(inputStates: Set<StateAndRef<FungibleToken>>,
                      amountToSpend: Amount<TokenType>,
                      newOwner: PublicKey,
                      map: ConcurrentMap<StateRef, StateAndRef<FungibleToken>>? = null,
                      observable: PublishSubject<Vault.Update<FungibleToken>>? = null,
                      spendTracker: ConcurrentHashMap<StateRef, AtomicInteger>? = null,
                      database: CordaPersistence): Pair<Set<StateAndRef<FungibleToken>>, Set<StateAndRef<FungibleToken>>> {

            var totalForInput = amountToSpend.copy(quantity = 0)
            inputStates.forEach {
                totalForInput += it.state.data.amount.withoutIssuer()
            }

            if (totalForInput < amountToSpend) {
                throw IllegalStateException("Not enough value in input states for required transaction")
            }

            // TODO Normally we would sum for each issuer separately
            val change = totalForInput - amountToSpend
            val outputTx = SecureHash.randomSHA256()

            val changeState = createNewTokenRef(
                    change,
                    inputStates.first().state.data.holder.owningKey,
                    inputStates.first().state.notary,
                    issuer1,
                    txHash = outputTx,
                    index = 0,
                    database = database)
            val movedState = createNewTokenRef(
                    amountToSpend,
                    newOwner,
                    inputStates.first().state.notary,
                    issuer1,
                    txHash = outputTx,
                    index = 1,
                    database = database)

            map?.let {
                it.putIfAbsent(changeState.ref, uncheckedCast(changeState))
                it.putIfAbsent(movedState.ref, uncheckedCast(movedState))
            }

            observable?.let {
                database.transaction {
                    observable.onNext(Vault.Update(consumed = inputStates, produced = setOf(changeState, movedState)))
                }
            }

            spendTracker?.let {
                inputStates.forEach { spentInput ->
                    val counter = it.computeIfAbsent(spentInput.ref) {
                        AtomicInteger(0)
                    }
                    counter.incrementAndGet()
                }
            }
            return inputStates to setOf(changeState, movedState)
        }

        fun executeConsumption(inputStates: Set<StateAndRef<FungibleToken>>,
                          observable: PublishSubject<Vault.Update<FungibleToken>>,
                          database: CordaPersistence) =
                database.transaction {
                    observable.onNext(Vault.Update(consumed = inputStates, produced = setOf()))
                }

        fun getDefaultVaultObserver(): Pair<TokenObserver, PublishSubject<Vault.Update<FungibleToken>>> {
            val observable = PublishSubject.create<Vault.Update<FungibleToken>>()
            return Pair(TokenObserver(listOf(), uncheckedCast(observable), { stateAndRef, _ -> Holder.KeyIdentity(stateAndRef.state.data.holder.owningKey) }), observable)
        }
    }
}
