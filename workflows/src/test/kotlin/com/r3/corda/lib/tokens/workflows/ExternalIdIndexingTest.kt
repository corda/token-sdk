package com.r3.corda.lib.tokens.workflows

import com.r3.corda.lib.tokens.contracts.states.FungibleToken
import com.r3.corda.lib.tokens.money.GBP
import com.r3.corda.lib.tokens.money.USD
import com.r3.corda.lib.tokens.selection.InsufficientBalanceException
import com.r3.corda.lib.tokens.selection.memory.internal.Holder
import com.r3.corda.lib.tokens.selection.memory.internal.lookupExternalIdFromKey
import com.r3.corda.lib.tokens.selection.memory.services.TokenObserver
import com.r3.corda.lib.tokens.selection.memory.services.VaultWatcherService
import net.corda.core.contracts.Amount
import net.corda.core.contracts.StateAndRef
import net.corda.core.identity.CordaX500Name
import net.corda.core.internal.uncheckedCast
import net.corda.core.node.services.Vault
import net.corda.nodeapi.internal.persistence.CordaPersistence
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.core.TestIdentity
import net.corda.testing.node.MockServices
import org.hamcrest.CoreMatchers
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import rx.subjects.PublishSubject
import java.util.*

class ExternalIdIndexingTest {
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

    private fun getExternalIdVaultObserver(): Pair<TokenObserver, PublishSubject<Vault.Update<FungibleToken>>> {
        val observable = PublishSubject.create<Vault.Update<FungibleToken>>()
        return Pair(TokenObserver(listOf(), uncheckedCast(observable),
                { stateAndRef, appServiceHub -> lookupExternalIdFromKey(stateAndRef.state.data.holder.owningKey, appServiceHub) }), observable)
    }

    @Test
    fun `different tokens selected for different identities`() {
        val (VaultObserver, observable) = getExternalIdVaultObserver()

        val vaultWatcherService = VaultWatcherService(VaultObserver, services)
        val uuid1 = UUID.randomUUID()
        val uuid2 = UUID.randomUUID()
        val key1 = services.keyManagementService.freshKey(uuid1)
        val key2 = services.keyManagementService.freshKey(uuid2)
        val amountToIssue: Long = 100
        val stateAndRef1 = VaultWatcherServiceTest.createNewFiatCurrencyTokenRef(amountToIssue, key1, VaultWatcherServiceTest.notary1, VaultWatcherServiceTest.issuer1, GBP, observable, database)
        val stateAndRef2 = VaultWatcherServiceTest.createNewFiatCurrencyTokenRef(amountToIssue, key2, VaultWatcherServiceTest.notary1, VaultWatcherServiceTest.issuer1, GBP, observable, database)
        val selectedTokens1 = vaultWatcherService.selectTokens(Holder.MappedIdentity(uuid1), Amount(5, GBP), selectionId = "abc")
        val selectedTokens2 = vaultWatcherService.selectTokens(Holder.MappedIdentity(uuid2), Amount(5, GBP), selectionId = "abc")
        Assert.assertThat(selectedTokens1, CoreMatchers.`is`(CoreMatchers.equalTo(listOf<StateAndRef<FungibleToken>>(stateAndRef1))))
        Assert.assertThat(selectedTokens2, CoreMatchers.`is`(CoreMatchers.equalTo(listOf<StateAndRef<FungibleToken>>(stateAndRef2))))
    }

    @Test(expected = InsufficientBalanceException::class)
    fun `spend other token type from same uuid`() {
        val (VaultObserver, observable) = getExternalIdVaultObserver()

        val vaultWatcherService = VaultWatcherService(VaultObserver, services)
        val uuid = UUID.randomUUID()
        val key = services.keyManagementService.freshKey(uuid)
        val amountToIssue: Long = 5
        VaultWatcherServiceTest.createNewFiatCurrencyTokenRef(amountToIssue, key, VaultWatcherServiceTest.notary1, VaultWatcherServiceTest.issuer1, USD, observable, database)
        VaultWatcherServiceTest.createNewFiatCurrencyTokenRef(amountToIssue, key, VaultWatcherServiceTest.notary1, VaultWatcherServiceTest.issuer1, GBP, observable, database)
        database.transaction {
            vaultWatcherService.selectTokens(Holder.MappedIdentity(uuid), Amount(10, GBP), selectionId = "abc")
        }
    }
}
