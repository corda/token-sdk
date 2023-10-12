package com.r3.corda.lib.tokens.workflows

import com.r3.corda.lib.tokens.contracts.states.FungibleToken
import com.r3.corda.lib.tokens.money.GBP
import com.r3.corda.lib.tokens.money.USD
import com.r3.corda.lib.tokens.selection.InsufficientBalanceException
import com.r3.corda.lib.tokens.selection.InsufficientNotLockedBalanceException
import com.r3.corda.lib.tokens.selection.memory.config.InMemorySelectionConfig
import com.r3.corda.lib.tokens.selection.memory.internal.Holder
import com.r3.corda.lib.tokens.selection.memory.internal.lookupExternalIdFromKey
import com.r3.corda.lib.tokens.selection.memory.selector.LocalTokenSelector
import com.r3.corda.lib.tokens.selection.memory.services.TokenObserver
import com.r3.corda.lib.tokens.selection.memory.services.VaultWatcherService
import net.corda.core.contracts.Amount
import net.corda.core.identity.CordaX500Name
import net.corda.core.internal.uncheckedCast
import net.corda.core.node.services.Vault
import net.corda.nodeapi.internal.persistence.CordaPersistence
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.core.SerializationEnvironmentRule
import net.corda.testing.core.TestIdentity
import net.corda.testing.node.MockServices
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import rx.subjects.PublishSubject
import java.util.*

class InMemorySelectionTest {
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

    @Test(expected = InsufficientBalanceException::class)
    fun `external id indexing - spend other token type from same uuid`() {
        val (vaultObserver, observable) = getExternalIdVaultObserver()

        val vaultWatcherService = VaultWatcherService(vaultObserver, InMemorySelectionConfig.defaultConfig())
        val uuid = UUID.randomUUID()
        val key = services.keyManagementService.freshKey(uuid)
        val amountToIssue: Long = 5
        VaultWatcherServiceTest.createNewFiatCurrencyTokenRef(amountToIssue, key, VaultWatcherServiceTest.notary1, VaultWatcherServiceTest.issuer1, USD, observable, database)
        VaultWatcherServiceTest.createNewFiatCurrencyTokenRef(amountToIssue, key, VaultWatcherServiceTest.notary1, VaultWatcherServiceTest.issuer1, GBP, observable, database)
        database.transaction {
            vaultWatcherService.selectTokens(Holder.MappedIdentity(uuid), Amount(10, GBP), selectionId = "abc")
        }
    }

    @Test(expected = InsufficientNotLockedBalanceException::class)
    fun `insufficient balance selection - should throw InsufficientNotLockedBalanceException when there is not enough not locked tokens available`() {
        val (vaultObserver, observable) = getExternalIdVaultObserver()
        val vaultWatcherService = VaultWatcherService(vaultObserver, InMemorySelectionConfig.defaultConfig())
        val uuid = UUID.randomUUID()
        val key = services.keyManagementService.freshKey(uuid)

        // placing two states of 100 and 50 USD into the observer, then soft locking the 100-one.
        // The test should fail with InsufficientNotLockedBalanceException when trying to select 60 USD.
        val biggerStateAndRef = VaultWatcherServiceTest.createNewFiatCurrencyTokenRef(100, key, VaultWatcherServiceTest.notary1, VaultWatcherServiceTest.issuer1, USD, observable, database)
        VaultWatcherServiceTest.createNewFiatCurrencyTokenRef(50, key, VaultWatcherServiceTest.notary1, VaultWatcherServiceTest.issuer1, USD, observable, database)
        vaultWatcherService.lockTokensExternal(listOf(biggerStateAndRef), UUID.randomUUID().toString())

        database.transaction {
            vaultWatcherService.selectTokens(Holder.MappedIdentity(uuid), Amount(60, USD), selectionId = "abc")
        }
    }

    @Test(timeout = 300_000)
    fun `indexing and selection by public key`() {
        val (vaultObserver, observable) = getPublicKeyVaultObserver()
        val vaultWatcherService = VaultWatcherService(vaultObserver, InMemorySelectionConfig.defaultConfig())
        val selector = LocalTokenSelector(services, vaultWatcherService)
        val key1 = services.keyManagementService.freshKey()
        val key2 = services.keyManagementService.freshKey()
        val amountToIssue: Long = 100
        val stateAndRef1 = VaultWatcherServiceTest.createNewFiatCurrencyTokenRef(amountToIssue, key1, VaultWatcherServiceTest.notary1, VaultWatcherServiceTest.issuer1, GBP, observable, database)
        val stateAndRef2 = VaultWatcherServiceTest.createNewFiatCurrencyTokenRef(amountToIssue, key2, VaultWatcherServiceTest.notary1, VaultWatcherServiceTest.issuer1, GBP, observable, database)
        val selectedTokens1 = selector.selectTokens(holdingKey = key1, requiredAmount = Amount(5, GBP))
        selector.rollback()
        val selectedTokens2 = selector.selectTokens(holdingKey = key2, requiredAmount = Amount(10, GBP))
        assertThat(selectedTokens1).containsExactly(stateAndRef1)
        assertThat(selectedTokens2).containsExactly(stateAndRef2)
    }

    @Test(timeout = 300_000)
    fun `indexing and selection by external id`() {
        val (vaultObserver, observable) = getExternalIdVaultObserver()
        val vaultWatcherService = VaultWatcherService(vaultObserver, InMemorySelectionConfig.defaultConfig())
        val selector = LocalTokenSelector(services, vaultWatcherService)
        val uuid1 = UUID.randomUUID()
        val uuid2 = UUID.randomUUID()
        val key1 = services.keyManagementService.freshKey(uuid1)
        val key2 = services.keyManagementService.freshKey(uuid2)
        val amountToIssue: Long = 100
        val stateAndRef1 = VaultWatcherServiceTest.createNewFiatCurrencyTokenRef(amountToIssue, key1, VaultWatcherServiceTest.notary1, VaultWatcherServiceTest.issuer1, GBP, observable, database)
        val stateAndRef2 = VaultWatcherServiceTest.createNewFiatCurrencyTokenRef(amountToIssue, key2, VaultWatcherServiceTest.notary1, VaultWatcherServiceTest.issuer1, GBP, observable, database)
        val selectedTokens1 = selector.selectTokens(uuid1, Amount(5, GBP))
        selector.rollback()
        val selectedTokens2 = selector.selectTokens(uuid2, Amount(10, GBP))
        assertThat(selectedTokens1).containsExactly(stateAndRef1)
        assertThat(selectedTokens2).containsExactly(stateAndRef2)
    }

    @Test(timeout = 300_000)
    fun `indexing and selection by token only`() {
        val (vaultObserver, observable) = getTokenOnlyVaultObserver()
        val vaultWatcherService = VaultWatcherService(vaultObserver, InMemorySelectionConfig.defaultConfig())
        val selector = LocalTokenSelector(services, vaultWatcherService)
        val key = services.keyManagementService.freshKey()
        val amountToIssue: Long = 100
        val stateAndRef1 = VaultWatcherServiceTest.createNewFiatCurrencyTokenRef(amountToIssue, key, VaultWatcherServiceTest.notary1, VaultWatcherServiceTest.issuer1, GBP, observable, database)
        val stateAndRef2 = VaultWatcherServiceTest.createNewFiatCurrencyTokenRef(amountToIssue, key, VaultWatcherServiceTest.notary1, VaultWatcherServiceTest.issuer1, USD, observable, database)
        val selectedTokens1 = selector.selectTokens(Amount(5, GBP))
        selector.rollback()
        val selectedTokens2 = selector.selectTokens(Amount(10, USD))
        assertThat(selectedTokens1).containsExactly(stateAndRef1)
        assertThat(selectedTokens2).containsExactly(stateAndRef2)
    }

    private fun getExternalIdVaultObserver(): Pair<TokenObserver, PublishSubject<Vault.Update<FungibleToken>>> {
        val observable = PublishSubject.create<Vault.Update<FungibleToken>>()
        return Pair(TokenObserver(listOf(), uncheckedCast(observable), { stateAndRef, _ -> lookupExternalIdFromKey(stateAndRef.state.data.holder.owningKey, services) }), observable)
    }

    private fun getPublicKeyVaultObserver(): Pair<TokenObserver, PublishSubject<Vault.Update<FungibleToken>>> {
        val observable = PublishSubject.create<Vault.Update<FungibleToken>>()
        return Pair(TokenObserver(listOf(), uncheckedCast(observable)
                , { stateAndRef, _ -> Holder.KeyIdentity(stateAndRef.state.data.holder.owningKey) }), observable)
    }

    private fun getTokenOnlyVaultObserver(): Pair<TokenObserver, PublishSubject<Vault.Update<FungibleToken>>> {
        val observable = PublishSubject.create<Vault.Update<FungibleToken>>()
        return Pair(TokenObserver(listOf(), uncheckedCast(observable)
                , { _, _ -> Holder.TokenOnly() }), observable)
    }
}
