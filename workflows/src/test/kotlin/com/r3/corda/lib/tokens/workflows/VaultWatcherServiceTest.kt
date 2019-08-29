package com.r3.corda.lib.tokens.workflows

import com.r3.corda.lib.tokens.contracts.states.FungibleToken
import com.r3.corda.lib.tokens.contracts.types.IssuedTokenType
import com.r3.corda.lib.tokens.contracts.types.TokenType
import com.r3.corda.lib.tokens.contracts.utilities.heldBy
import com.r3.corda.lib.tokens.contracts.utilities.issuedBy
import com.r3.corda.lib.tokens.contracts.utilities.of
import com.r3.corda.lib.tokens.contracts.utilities.withoutIssuer
import com.r3.corda.lib.tokens.money.BTC
import com.r3.corda.lib.tokens.money.GBP
import com.r3.corda.lib.tokens.selection.internal.Holder
import com.r3.corda.lib.tokens.selection.services.InsufficientBalanceException
import com.r3.corda.lib.tokens.selection.services.TokenObserver
import com.r3.corda.lib.tokens.selection.services.VaultWatcherService
import com.r3.corda.lib.tokens.workflows.flows.rpc.IssueTokens
import net.corda.core.contracts.Amount
import net.corda.core.contracts.StateAndRef
import net.corda.core.contracts.StateRef
import net.corda.core.contracts.TransactionState
import net.corda.core.crypto.Crypto
import net.corda.core.crypto.SecureHash
import net.corda.core.identity.AnonymousParty
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.internal.sumByLong
import net.corda.core.internal.uncheckedCast
import net.corda.core.node.services.Vault
import net.corda.core.utilities.getOrThrow
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.core.CHARLIE_NAME
import net.corda.testing.core.TestIdentity
import net.corda.testing.core.singleIdentity
import net.corda.testing.node.MockServices
import net.corda.testing.node.internal.InternalMockNetwork
import net.corda.testing.node.internal.InternalMockNodeParameters
import net.corda.testing.node.internal.startFlow
import org.hamcrest.CoreMatchers
import org.hamcrest.CoreMatchers.`is`
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.CoreMatchers.everyItem
import org.hamcrest.CoreMatchers.not
import org.hamcrest.Matchers.greaterThanOrEqualTo
import org.hamcrest.Matchers.isIn
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import rx.subjects.PublishSubject
import java.security.PublicKey
import java.text.NumberFormat
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

class VaultWatcherServiceTest {
    private lateinit var services: MockServices

    @Before
    fun setup() {
        services = MockServices.makeTestDatabaseAndPersistentServices(
                cordappPackages = listOf("com.r3.corda.lib.tokens.workflows"),
                initialIdentity = TestIdentity(CordaX500Name("Test", "London", "GB")),
                networkParameters = testNetworkParameters(minimumPlatformVersion = 4),
                moreIdentities = emptySet(),
                moreKeys = emptySet()
        ).second
    }

    @Test
    fun `should accept token into the cache`() {

        val (VaultObserver, observable) = getDefaultVaultObserver()

        val vaultWatcherService = VaultWatcherService(VaultObserver, services)
        val owner = Crypto.generateKeyPair(Crypto.DEFAULT_SIGNATURE_SCHEME).public
        val amountToIssue: Long = 100
        val stateAndRef = createNewFiatCurrencyTokenRef(amountToIssue, owner, notary1, issuer1, GBP, observable)
        val selectedTokens = vaultWatcherService.selectTokens(Holder.KeyIdentity(owner), Amount(5, GBP), selectionId = "abc")
        Assert.assertThat(selectedTokens, `is`(CoreMatchers.equalTo(listOf<StateAndRef<FungibleToken>>(stateAndRef))))
    }

    @Test
    fun `should select enough tokens to satisfy the requested amount`() {
        val (VaultObserver,
                observable) = getDefaultVaultObserver()

        val vaultWatcherService = VaultWatcherService(VaultObserver, services)
        val owner = Crypto.generateKeyPair(Crypto.DEFAULT_SIGNATURE_SCHEME).public
        for (i in 1..100) {
            createNewFiatCurrencyTokenRef(((Math.random() * 10) + 1).toLong(), owner, notary1, issuer1, GBP, observable)
        }

        val selectedTokens = vaultWatcherService.selectTokens(Holder.KeyIdentity(owner), Amount(45, GBP), selectionId = "abc")
        Assert.assertThat(selectedTokens.map { it.state.data.amount.quantity }.sumByLong { it }, `is`(greaterThanOrEqualTo(45L)))
    }

    @Test(expected = InsufficientBalanceException::class)
    fun `should not allow double selection of token in the cache`() {
        val (VaultObserver,
                observable) = getDefaultVaultObserver()

        val vaultWatcherService = VaultWatcherService(VaultObserver, services)

        val owner = Crypto.generateKeyPair(Crypto.DEFAULT_SIGNATURE_SCHEME).public
        val amountToIssue: Long = 100
        val stateAndRef = createNewFiatCurrencyTokenRef(amountToIssue, owner, notary1, issuer1, GBP, observable)

        val selectedTokens = vaultWatcherService.selectTokens(Holder.JustToken, Amount(5, IssuedTokenType(issuer1, GBP)), selectionId = "abc")
        Assert.assertThat(selectedTokens, `is`(CoreMatchers.equalTo(listOf<StateAndRef<FungibleToken>>(stateAndRef))))
        vaultWatcherService.selectTokens(Holder.KeyIdentity(owner), Amount(5, IssuedTokenType(issuer1, GBP)), selectionId = "abc")
    }

    @Test
    fun `should allow filtering based on arbitary predicate`() {
        val (VaultObserver,
                observable) = getDefaultVaultObserver()

        val vaultWatcherService = VaultWatcherService(VaultObserver, services)

        val owner = Crypto.generateKeyPair(Crypto.DEFAULT_SIGNATURE_SCHEME).public
        val amountToIssue: Long = 100
        val stateAndRef = createNewFiatCurrencyTokenRef(amountToIssue, owner, notary1, issuer1, GBP, observable)
        createNewFiatCurrencyTokenRef(amountToIssue, owner, notary2, issuer1, GBP, observable)
        createNewFiatCurrencyTokenRef(amountToIssue + 1, owner, notary2, issuer1, GBP, observable)
        createNewFiatCurrencyTokenRef(amountToIssue + 2, owner, notary2, issuer1, GBP, observable)
        createNewFiatCurrencyTokenRef(amountToIssue + 3, owner, notary2, issuer1, GBP, observable)

        val selectedTokens = vaultWatcherService.selectTokens(Holder.KeyIdentity(owner), Amount(5, GBP), {
            it.state.notary == notary1
        }, selectionId = "abc")

        Assert.assertThat(selectedTokens, `is`(CoreMatchers.equalTo(listOf<StateAndRef<FungibleToken>>(stateAndRef))))

        val notary2Selected = vaultWatcherService.selectTokens(Holder.KeyIdentity(owner), Amount(amountToIssue * 2, GBP), {
            it.state.notary == notary2
        }, selectionId = "abc")

        Assert.assertThat(notary2Selected, `is`(CoreMatchers.equalTo(notary2Selected.filter { it.state.notary == notary2 })))
    }

    @Test
    fun `should remove states which have been spent`() {
        val (VaultObserver,
                observable) = getDefaultVaultObserver()

        val vaultWatcherService = VaultWatcherService(VaultObserver, services)
        val owner = Crypto.generateKeyPair(Crypto.DEFAULT_SIGNATURE_SCHEME).public

        for (i in 1..100) {
            createNewFiatCurrencyTokenRef((Math.random() * 10).toLong(), owner, notary1, issuer1, GBP, observable)
        }

        val selectedTokens = vaultWatcherService.selectTokens(Holder.KeyIdentity(owner), Amount(50, GBP), selectionId = "abc").toSet()
        val (spentInputs, _) = executeTx(
                selectedTokens,
                Amount(50, GBP),
                Crypto.generateKeyPair(Crypto.DEFAULT_SIGNATURE_SCHEME).public,
                observable = observable)


        val selectedTokensAfterSpend = vaultWatcherService.selectTokens(Holder.KeyIdentity(owner), Amount(10000000000, GBP), allowShortfall = true, selectionId = "abc")

        Assert.assertThat(spentInputs, everyItem(not(isIn(selectedTokensAfterSpend))))
    }

    @Test
    fun `very basic memory checking state scales`() {

        val (VaultObserver,
                observable) = getDefaultVaultObserver()

        VaultWatcherService(VaultObserver, services)
        val owner = Crypto.generateKeyPair(Crypto.DEFAULT_SIGNATURE_SCHEME).public


        for (j in 1..1000) {
            for (i in 1..1000) {
                createNewFiatCurrencyTokenRef((Math.random() * 10).toLong(), owner, notary1, issuer1, GBP, observable)
            }
            println("${j * 1000} total states = ${Runtime.getRuntime().totalMemory() / (1024 * 1024)}MB")
        }

    }

    @Test
    fun `very basic memory checking owner scales`() {
        val (VaultObserver, observable) = getDefaultVaultObserver()

        VaultWatcherService(VaultObserver, services)

        for (j in 1..50_000) {
            val owner = Crypto.generateKeyPair(Crypto.DEFAULT_SIGNATURE_SCHEME).public
            for (i in 1..5) {
                createNewFiatCurrencyTokenRef((Math.random() * 10).toLong(), owner, notary1, issuer1, GBP, observable)
            }
            println("$j owners ${NumberFormat.getInstance().format(j * 5)}, total states = ${Runtime.getRuntime().totalMemory() / (1024 * 1024)}MB")
        }
    }

    @Test
    fun `should support concurrent requests and inserts for tokens`() {
        val (VaultObserver,
                observable) = getDefaultVaultObserver()
        val vaultWatcherService = VaultWatcherService(VaultObserver, services)

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
                createNewDigitalCurrencyTokenRef(((Math.random() * 1000) + 1).toLong(), owner, notary1, issuer1, observable)
            }
        }

        val gbpInserter = Runnable {
            for (i in 1..1000) {
                val owner = if (Math.random() > 0.5) {
                    owner1
                } else {
                    owner2
                }
                createNewFiatCurrencyTokenRef(((Math.random() * 1000) + 1).toLong(), owner, notary1, issuer1, GBP, observable)
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
                            spendTracker = spendTracker
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
                            spendTracker = spendTracker
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


        Assert.assertThat(spendTracker.filter { it.value.get() > 1 }.toList(), `is`(equalTo(emptyList())))
    }

    @Test
    fun `should allow selection of tokens already issued from within a flow`() {

        val mockNet = InternalMockNetwork(cordappPackages = listOf(
                "com.r3.corda.lib.tokens.money",
                "com.r3.corda.lib.tokens.selection",
                "com.r3.corda.lib.tokens.contracts",
                "com.r3.corda.lib.tokens.workflows"
        ))
        val aliceNode = mockNet.createNode(InternalMockNodeParameters(legalName = ALICE_NAME))
        val issuerNode = mockNet.createNode(InternalMockNodeParameters(legalName = CHARLIE_NAME))
        val alice = aliceNode.info.singleIdentity()
        val issuer = issuerNode.info.singleIdentity()

        val btc = 100000 of BTC issuedBy issuer heldBy alice
        val resultFuture = issuerNode.services.startFlow(IssueTokens(listOf(btc))).resultFuture
        mockNet.runNetwork()
        val issueResultTx = resultFuture.get()
        val issuedStateRef = issueResultTx.coreTransaction.outRefsOfType<FungibleToken>().single()

        val tokensFuture = aliceNode.services.startFlow(SuspendingSelector(alice.owningKey, Amount(1, BTC), allowShortfall = false)).resultFuture
        mockNet.runNetwork()
        val selectedToken = tokensFuture.getOrThrow().single()

        Assert.assertThat(issuedStateRef, `is`(equalTo(selectedToken)))

        mockNet.stopNodes()
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
                                      index: Int = 0): StateAndRef<FungibleToken> {
            val thing = FungibleToken(amount issuedBy issuer, AnonymousParty(owner))
            val state = TransactionState(data = thing, notary = notary)
            val stateRef = StateRef(txHash, index)
            return StateAndRef(state, stateRef)
                    .also { observable?.onNext(Vault.Update(emptySet(), produced = setOf(it))) }
        }

        fun createNewFiatCurrencyTokenRef(amountToIssue: Long,
                                          owner: PublicKey,
                                          notary: Party,
                                          issuer: Party,
                                          currency: TokenType = GBP,
                                          observable: PublishSubject<Vault.Update<FungibleToken>>? = null): StateAndRef<FungibleToken> {
            val amount = Amount(amountToIssue, currency)
            return createNewTokenRef(amount, owner, notary, issuer, observable)
        }

        private fun createNewDigitalCurrencyTokenRef(amountToIssue: Long,
                                                     owner: PublicKey,
                                                     notary: Party,
                                                     issuer: Party,
                                                     observable: PublishSubject<Vault.Update<FungibleToken>>? = null): StateAndRef<FungibleToken> {
            val amount = Amount(amountToIssue, BTC)
            return createNewTokenRef(amount, owner, notary, issuer, observable)
        }

        fun executeTx(inputStates: Set<StateAndRef<FungibleToken>>,
                      amountToSpend: Amount<TokenType>,
                      newOwner: PublicKey,
                      map: ConcurrentMap<StateRef, StateAndRef<FungibleToken>>? = null,
                      observable: PublishSubject<Vault.Update<FungibleToken>>? = null,
                      spendTracker: ConcurrentHashMap<StateRef, AtomicInteger>? = null): Pair<Set<StateAndRef<FungibleToken>>, Set<StateAndRef<FungibleToken>>> {

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
                    index = 0)
            val movedState = createNewTokenRef(
                    amountToSpend,
                    newOwner,
                    inputStates.first().state.notary,
                    issuer1,
                    txHash = outputTx,
                    index = 1)

            map?.let {
                it.putIfAbsent(changeState.ref, uncheckedCast(changeState))
                it.putIfAbsent(movedState.ref, uncheckedCast(movedState))
            }

            observable?.let {
                observable.onNext(Vault.Update((inputStates), produced = setOf(changeState, movedState)))
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

        fun getDefaultVaultObserver(): Pair<TokenObserver, PublishSubject<Vault.Update<FungibleToken>>> {
            val observable = PublishSubject.create<Vault.Update<FungibleToken>>()
            return Pair(TokenObserver(listOf(), uncheckedCast(observable), { stateAndRef, _ -> Holder.KeyIdentity(stateAndRef.state.data.holder.owningKey) }), observable)
        }
    }
}
