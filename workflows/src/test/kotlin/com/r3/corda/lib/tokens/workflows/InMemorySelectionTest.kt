package com.r3.corda.lib.tokens.workflows

import com.r3.corda.lib.tokens.contracts.states.FungibleToken
import com.r3.corda.lib.tokens.money.GBP
import com.r3.corda.lib.tokens.money.USD
import com.r3.corda.lib.tokens.selection.InsufficientBalanceException
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
import net.corda.testing.core.TestIdentity
import net.corda.testing.node.MockServices
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Test
import rx.subjects.PublishSubject
import java.util.*

class InMemorySelectionTest {
    private lateinit var services: MockServices
    private lateinit var database: CordaPersistence

    @Before
    fun setup() {
        val mockDbAndServices = MockServices.makeTestDatabaseAndPersistentServices(
                cordappPackages = listOf("com.r3.corda.lib.tokens.workflows"),
                initialIdentity = TestIdentity(CordaX500Name("Test", "London", "GB")),
                networkParameters = testNetworkParameters(minimumPlatformVersion = 4),
                moreIdentities = emptySet(),
                moreKeys = emptySet()
        )
        services = mockDbAndServices.second
        database = mockDbAndServices.first
    }

    @Test(expected = InsufficientBalanceException::class)
    fun `external id indexing - spend other token type from same uuid`() {
        val (vaultObserver, observable) = getExternalIdVaultObserver()

        val vaultWatcherService = VaultWatcherService(vaultObserver, services)
        val uuid = UUID.randomUUID()
        val key = services.keyManagementService.freshKey(uuid)
        val amountToIssue: Long = 5
        VaultWatcherServiceTest.createNewFiatCurrencyTokenRef(amountToIssue, key, VaultWatcherServiceTest.notary1, VaultWatcherServiceTest.issuer1, USD, observable, database)
        VaultWatcherServiceTest.createNewFiatCurrencyTokenRef(amountToIssue, key, VaultWatcherServiceTest.notary1, VaultWatcherServiceTest.issuer1, GBP, observable, database)
        database.transaction {
            vaultWatcherService.selectTokens(Holder.MappedIdentity(uuid), Amount(10, GBP), selectionId = "abc")
        }
    }

    @Test
    fun `indexing and selection by public key`() {
        val (vaultObserver, observable) = getPublicKeyVaultObserver()
        val vaultWatcherService = VaultWatcherService(vaultObserver, services)
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

    @Test
    fun `indexing and selection by external id`() {
        val (vaultObserver, observable) = getExternalIdVaultObserver()
        val vaultWatcherService = VaultWatcherService(vaultObserver, services)
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

    @Test
    fun `indexing and selection by token only`() {
        val (vaultObserver, observable) = getTokenOnlyVaultObserver()
        val vaultWatcherService = VaultWatcherService(vaultObserver, services)
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
        return Pair(TokenObserver(listOf(), uncheckedCast(observable)
        ) { stateAndRef, appServiceHub, _ -> lookupExternalIdFromKey(stateAndRef.state.data.holder.owningKey, appServiceHub) }, observable)
    }

    private fun getPublicKeyVaultObserver(): Pair<TokenObserver, PublishSubject<Vault.Update<FungibleToken>>> {
        val observable = PublishSubject.create<Vault.Update<FungibleToken>>()
        return Pair(TokenObserver(listOf(), uncheckedCast(observable)
        ) { stateAndRef, _, _ -> Holder.KeyIdentity(stateAndRef.state.data.holder.owningKey) }, observable)
    }

    private fun getTokenOnlyVaultObserver(): Pair<TokenObserver, PublishSubject<Vault.Update<FungibleToken>>> {
        val observable = PublishSubject.create<Vault.Update<FungibleToken>>()
        return Pair(TokenObserver(listOf(), uncheckedCast(observable)
        ) { _, _, _ -> Holder.TokenOnly() }, observable)
    }
}
