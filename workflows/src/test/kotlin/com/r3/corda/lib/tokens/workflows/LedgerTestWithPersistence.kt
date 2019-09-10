package com.r3.corda.lib.tokens.workflows

import com.nhaarman.mockito_kotlin.whenever
import net.corda.core.identity.CordaX500Name
import net.corda.core.node.services.IdentityService
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.core.DUMMY_NOTARY_NAME
import net.corda.testing.core.SerializationEnvironmentRule
import net.corda.testing.core.TestIdentity
import net.corda.testing.internal.rigorousMock
import net.corda.testing.node.MockServices
import org.junit.Rule
import org.mockito.Mockito

abstract class LedgerTestWithPersistence {

    private val mockIdentityService = rigorousMock<IdentityService>().also {
        Mockito.doReturn(ALICE.party).whenever(it).partyFromKey(ALICE.publicKey)
        Mockito.doReturn(BOB.party).whenever(it).partyFromKey(BOB.publicKey)
        Mockito.doReturn(ISSUER.party).whenever(it).partyFromKey(ISSUER.publicKey)
        Mockito.doReturn(NOTARY.party).whenever(it).partyFromKey(NOTARY.publicKey)
        Mockito.doReturn(NOTARY.party).whenever(it).wellKnownPartyFromAnonymous(NOTARY.party)
        Mockito.doReturn(ALICE.party).whenever(it).wellKnownPartyFromAnonymous(ALICE.party)
        Mockito.doReturn(ISSUER.party).whenever(it).wellKnownPartyFromAnonymous(ISSUER.party)
        Mockito.doReturn(ISSUER.party).whenever(it).wellKnownPartyFromX500Name(ISSUER.party.name)
        Mockito.doReturn(NOTARY.party).whenever(it).wellKnownPartyFromX500Name(NOTARY.party.name)
        Mockito.doReturn(ALICE.party).whenever(it).wellKnownPartyFromX500Name(ALICE.party.name)

    }

    protected companion object {
        val NOTARY = TestIdentity(DUMMY_NOTARY_NAME, 20)
        val ISSUER = TestIdentity(CordaX500Name("ISSUER", "London", "GB"))
        val ALICE = TestIdentity(CordaX500Name("ALICE", "London", "GB"))
        val BOB = TestIdentity(CordaX500Name("BOB", "London", "GB"))
    }

    @Rule
    @JvmField
    val testSerialization = SerializationEnvironmentRule()

    private val aliceDbAndServices = MockServices.makeTestDatabaseAndMockServices(
            cordappPackages = listOf("com.r3.corda.lib.tokens"),
            initialIdentity = ALICE,
            identityService = mockIdentityService,
            networkParameters = testNetworkParameters(minimumPlatformVersion = 4)
    )

    protected val aliceDB = aliceDbAndServices.first
    protected val aliceServices = aliceDbAndServices.second

    private val bobDbAndServices = MockServices.makeTestDatabaseAndMockServices(
            cordappPackages = listOf("com.r3.corda.lib.tokens"),
            initialIdentity = BOB,
            identityService = mockIdentityService,
            networkParameters = testNetworkParameters(minimumPlatformVersion = 4)
    )

    protected val bobDB = aliceDbAndServices.first
    protected val bobServices = aliceDbAndServices.second

    private val issuerDbAndServices = MockServices.makeTestDatabaseAndMockServices(
            cordappPackages = listOf("com.r3.corda.lib.tokens"),
            initialIdentity = ISSUER,
            identityService = mockIdentityService,
            networkParameters = testNetworkParameters(minimumPlatformVersion = 4)
    )

    protected val issuerDB = aliceDbAndServices.first
    protected val issuerServices = aliceDbAndServices.second

}