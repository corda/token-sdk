package com.r3.corda.sdk.token.workflow.selection

import com.r3.corda.sdk.token.contracts.states.FungibleToken
import com.r3.corda.sdk.token.contracts.types.IssuedTokenType
import com.r3.corda.sdk.token.contracts.types.TokenType
import com.r3.corda.sdk.token.money.BTC
import com.r3.corda.sdk.token.money.DigitalCurrency
import com.r3.corda.sdk.token.money.FiatCurrency
import com.r3.corda.sdk.token.money.GBP
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
import net.corda.core.node.services.Vault
import net.corda.core.utilities.getOrThrow
import net.corda.testing.core.TestIdentity
import org.hamcrest.CoreMatchers
import org.hamcrest.CoreMatchers.*
import org.hamcrest.Matchers.greaterThanOrEqualTo
import org.hamcrest.Matchers.isIn
import org.junit.Assert
import org.junit.Test
import rx.subjects.PublishSubject
import java.security.PublicKey
import java.text.NumberFormat
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlin.IllegalStateException

class VaultWatcherServiceTest {

    @Test
    fun `should accept token into the cache`() {

        val (VaultObserver,
                observable) = getDefaultVaultObserver()

        val vaultWatcherService = VaultWatcherService(VaultObserver)
        val owner = Crypto.generateKeyPair(Crypto.DEFAULT_SIGNATURE_SCHEME).public
        val amountToIssue: Long = 100
        val stateAndRef = createNewFiatCurrencyTokenRef(amountToIssue, owner, notary1, observable)
        val selectedTokens = vaultWatcherService.selectTokens(owner, Amount(5, IssuedTokenType(issuer1, GBP)))
        Assert.assertThat(selectedTokens, `is`(CoreMatchers.equalTo(listOf<StateAndRef<FungibleToken<*>>>(stateAndRef))))
    }

    @Test
    fun `should select enough tokens to satisfy the requested amount`() {
        val (VaultObserver,
                observable) = getDefaultVaultObserver()

        val vaultWatcherService = VaultWatcherService(VaultObserver)
        val owner = Crypto.generateKeyPair(Crypto.DEFAULT_SIGNATURE_SCHEME).public
        for (i in 1..100) {
            createNewFiatCurrencyTokenRef(((Math.random() * 10) + 1).toLong(), owner, notary1, observable)
        }

        val selectedTokens = vaultWatcherService.selectTokens(owner, Amount(45, IssuedTokenType(issuer1, GBP)))
        Assert.assertThat(selectedTokens.map { it.state.data.amount.quantity }.sumByLong { it }, `is`(greaterThanOrEqualTo(45L)))
    }

    @Test(expected = InsufficientBalanceException::class)
    fun `should not allow double selection of token in the cache`() {
        val (VaultObserver,
                observable) = getDefaultVaultObserver()

        val vaultWatcherService = VaultWatcherService(VaultObserver)

        val owner = Crypto.generateKeyPair(Crypto.DEFAULT_SIGNATURE_SCHEME).public
        val amountToIssue: Long = 100
        val stateAndRef = createNewFiatCurrencyTokenRef(amountToIssue, owner, notary1, observable)

        val selectedTokens = vaultWatcherService.selectTokens(owner, Amount(5, IssuedTokenType(issuer1, GBP)))
        Assert.assertThat(selectedTokens, `is`(CoreMatchers.equalTo(listOf<StateAndRef<FungibleToken<*>>>(stateAndRef))))
        vaultWatcherService.selectTokens(owner, Amount(5, IssuedTokenType(issuer1, GBP)))
    }

    @Test
    fun `should allow filtering based on arbitary predicate`() {
        val (VaultObserver,
                observable) = getDefaultVaultObserver()

        val vaultWatcherService = VaultWatcherService(VaultObserver)

        val owner = Crypto.generateKeyPair(Crypto.DEFAULT_SIGNATURE_SCHEME).public
        val amountToIssue: Long = 100
        val stateAndRef = createNewFiatCurrencyTokenRef(amountToIssue, owner, notary1, observable)
        val stateAndRef1 = createNewFiatCurrencyTokenRef(amountToIssue, owner, notary2, observable)
        val stateAndRef2 = createNewFiatCurrencyTokenRef(amountToIssue + 1, owner, notary2, observable)
        val stateAndRef3 = createNewFiatCurrencyTokenRef(amountToIssue + 2, owner, notary2, observable)
        val stateAndRef4 = createNewFiatCurrencyTokenRef(amountToIssue + 3, owner, notary2, observable)

        val selectedTokens = vaultWatcherService.selectTokens(owner, Amount(5, IssuedTokenType(issuer1, GBP)), {
            it.state.notary == notary1
        })

        Assert.assertThat(selectedTokens, `is`(CoreMatchers.equalTo(listOf<StateAndRef<FungibleToken<*>>>(stateAndRef))))

        val notary2Selected = vaultWatcherService.selectTokens(owner, Amount(amountToIssue * 2, IssuedTokenType(issuer1, GBP)), {
            it.state.notary == notary2
        })

        Assert.assertThat(notary2Selected, `is`(CoreMatchers.equalTo(notary2Selected.filter { it.state.notary == notary2 })))
    }

    @Test
    fun `should remove states which have been spent`() {
        val (VaultObserver,
                observable) = getDefaultVaultObserver()

        val vaultWatcherService = VaultWatcherService(VaultObserver)
        val owner = Crypto.generateKeyPair(Crypto.DEFAULT_SIGNATURE_SCHEME).public

        for (i in 1..100) {
            createNewFiatCurrencyTokenRef((Math.random() * 10).toLong(), owner, notary1, observable)
        }

        val selectedTokens = vaultWatcherService.selectTokens(owner, Amount(50, IssuedTokenType(issuer1, GBP))).toSet()
        val (spentInputs, createdOutputs) = executeTx(
                selectedTokens,
                Amount(50, IssuedTokenType(issuer1, GBP)),
                Crypto.generateKeyPair(Crypto.DEFAULT_SIGNATURE_SCHEME).public,
                observable = observable)


        val selectedTokensAfterSpend = vaultWatcherService.selectTokens(owner, Amount(10000000000, IssuedTokenType(issuer1, GBP)), allowSubSelect = true)

        Assert.assertThat(spentInputs, everyItem(not(isIn(selectedTokensAfterSpend))))
    }

    @Test
    fun `very basic memory checking state scales`() {

        val (VaultObserver,
                observable) = getDefaultVaultObserver()

        val vaultWatcherService = VaultWatcherService(VaultObserver)
        val owner = Crypto.generateKeyPair(Crypto.DEFAULT_SIGNATURE_SCHEME).public


        for (j in 1..1000) {
            for (i in 1..1000) {
                createNewFiatCurrencyTokenRef((Math.random() * 10).toLong(), owner, notary1, observable)
            }
            println("${j * 1000} total states = ${Runtime.getRuntime().totalMemory() / (1024 * 1024)}MB")
        }

    }

    @Test
    fun `very basic memory checking owner scales`() {

        val (VaultObserver,
                observable) = getDefaultVaultObserver()

        val vaultWatcherService = VaultWatcherService(VaultObserver)

        for (j in 1..1000) {
            val owner = Crypto.generateKeyPair(Crypto.DEFAULT_SIGNATURE_SCHEME).public
            for (i in 1..5000) {
                createNewFiatCurrencyTokenRef((Math.random() * 10).toLong(), owner, notary1, observable)
            }
            println("$j owners ${NumberFormat.getInstance().format(j * 5000)} total states = ${Runtime.getRuntime().totalMemory() / (1024 * 1024)}MB")
        }

    }


    @Test
    fun `should support concurrent requests and inserts for tokens`() {
        val (VaultObserver,
                observable) = getDefaultVaultObserver()
        val vaultWatcherService = VaultWatcherService(VaultObserver)

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
                createNewDigitalCurrencyTokenRef(((Math.random() * 1000) + 1).toLong(), owner, notary1, observable)
            }
        }

        val gbpInserter = Runnable {
            for (i in 1..1000) {
                val owner = if (Math.random() > 0.5) {
                    owner1
                } else {
                    owner2
                }
                createNewFiatCurrencyTokenRef(((Math.random() * 1000) + 1).toLong(), owner, notary1, observable)
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
                    val amountRequested = Amount((10 * Math.random()).toLong() + 1, IssuedTokenType(issuer1, BTC))
                    val selectedTokens = vaultWatcherService.selectTokens(owner, amountRequested).toSet()
                    val (spent, created) = executeTx(
                            selectedTokens,
                            amountRequested,
                            newOwner,
                            observable = observable,
                            spendTracker = spendTracker)

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
                    val amountRequested = Amount(10, IssuedTokenType(issuer1, GBP))
                    val selectedTokens = vaultWatcherService.selectTokens(owner, amountRequested).toSet()
                    val (spent, created) = executeTx(
                            selectedTokens,
                            amountRequested,
                            newOwner,
                            observable = observable,
                            spendTracker = spendTracker)
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

    companion object {

        val issuer1 = TestIdentity(CordaX500Name("BANK", "London", "GB")).party
        val issuer2 = TestIdentity(CordaX500Name("THE_CHAIN", "London", "GB")).party

        val notary1 = TestIdentity(CordaX500Name("Notary1", "London", "GB")).party
        val notary2 = TestIdentity(CordaX500Name("Notary2", "London", "GB")).party

        private fun <T : TokenType> createNewTokenRef(amount: Amount<IssuedTokenType<T>>,
                                                      owner: PublicKey,
                                                      notary: Party,
                                                      observable: PublishSubject<Vault.Update<FungibleToken<out TokenType>>>? = null,
                                                      txHash: SecureHash = SecureHash.randomSHA256(),
                                                      index: Int = 0): StateAndRef<FungibleToken<T>> {
            val thing = FungibleToken(amount, AnonymousParty(owner))
            val state = TransactionState(data = thing, notary = notary)
            val stateRef = StateRef(txHash, index)
            return StateAndRef(state, stateRef)
                    .also { observable?.onNext(Vault.Update(emptySet(), produced = setOf(it))) }
        }


        private fun createNewFiatCurrencyTokenRef(amountToIssue: Long, owner: PublicKey, notary: Party,
                                                  observable: PublishSubject<Vault.Update<FungibleToken<out TokenType>>>? = null): StateAndRef<FungibleToken<FiatCurrency>> {
            val amount = Amount(amountToIssue, IssuedTokenType(issuer1, GBP))
            return createNewTokenRef(amount, owner, notary, observable)
        }

        private fun createNewDigitalCurrencyTokenRef(amountToIssue: Long, owner: PublicKey, notary: Party,
                                                     observable: PublishSubject<Vault.Update<FungibleToken<out TokenType>>>? = null): StateAndRef<FungibleToken<DigitalCurrency>> {
            val amount = Amount(amountToIssue, IssuedTokenType(issuer1, BTC))
            return createNewTokenRef(amount, owner, notary, observable)
        }

        fun <T : TokenType> executeTx(inputStates: Set<StateAndRef<FungibleToken<T>>>,
                                      amountToSpend: Amount<IssuedTokenType<T>>,
                                      newOwner: PublicKey,
                                      map: ConcurrentMap<StateRef, StateAndRef<FungibleToken<TokenType>>>? = null,
                                      observable: PublishSubject<Vault.Update<FungibleToken<out TokenType>>>? = null,
                                      spendTracker: ConcurrentHashMap<StateRef, AtomicInteger>? = null): Pair<Set<StateAndRef<FungibleToken<T>>>, Set<StateAndRef<FungibleToken<T>>>> {

            var totalForInput = amountToSpend.copy(quantity = 0)
            inputStates.forEach {
                totalForInput += it.state.data.amount
            }

            if (totalForInput < amountToSpend) {
                throw IllegalStateException("Not enough value in input states for required transaction")
            }

            val change = totalForInput - amountToSpend
            val outputTx = SecureHash.randomSHA256()

            val changeState = createNewTokenRef(
                    change,
                    inputStates.first().state.data.holder.owningKey,
                    inputStates.first().state.notary,
                    txHash = outputTx,
                    index = 0)
            val movedState = createNewTokenRef(
                    amountToSpend,
                    newOwner,
                    inputStates.first().state.notary,
                    txHash = outputTx,
                    index = 1)

            map?.let {
                it.putIfAbsent(changeState.ref, changeState as StateAndRef<FungibleToken<TokenType>>)
                it.putIfAbsent(movedState.ref, movedState as StateAndRef<FungibleToken<TokenType>>)
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

        fun getDefaultVaultObserver(): Pair<VaultObserver, PublishSubject<Vault.Update<FungibleToken<out TokenType>>>> {
            val observable = PublishSubject.create<Vault.Update<FungibleToken<out TokenType>>>()
            return Pair(VaultObserver(listOf(), observable), observable)

        }
    }
}
