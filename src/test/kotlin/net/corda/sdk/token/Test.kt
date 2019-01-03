package net.corda.sdk.token

import com.nhaarman.mockito_kotlin.whenever
import net.corda.core.contracts.Amount
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.node.services.api.IdentityServiceInternal
import net.corda.sdk.token.states.OwnedToken
import net.corda.sdk.token.states.OwnedTokenAmount
import net.corda.sdk.token.types.Issued
import net.corda.sdk.token.types.money.FiatCurrency
import net.corda.sdk.token.types.money.GBP
import net.corda.sdk.token.types.token.EmbeddableToken
import net.corda.sdk.token.types.token.EvolvableToken
import net.corda.sdk.token.types.token.TokenPointer
import net.corda.sdk.token.utilities.issuedBy
import net.corda.sdk.token.utilities.of
import net.corda.sdk.token.utilities.ownedBy
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.core.DUMMY_NOTARY_NAME
import net.corda.testing.core.SerializationEnvironmentRule
import net.corda.testing.core.TestIdentity
import net.corda.testing.internal.rigorousMock
import net.corda.testing.node.MockServices
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.doReturn
import java.math.BigDecimal
import kotlin.test.assertEquals

class Test {

    private val mockIdentityService = rigorousMock<IdentityServiceInternal>().also {
        doReturn(ALICE.party).whenever(it).partyFromKey(ALICE.publicKey)
        doReturn(BOB.party).whenever(it).partyFromKey(BOB.publicKey)
        doReturn(ISSUER.party).whenever(it).partyFromKey(ISSUER.publicKey)
        doReturn(NOTARY.party).whenever(it).partyFromKey(NOTARY.publicKey)
        doReturn(NOTARY.party).whenever(it).wellKnownPartyFromAnonymous(NOTARY.party)
        doReturn(ALICE.party).whenever(it).wellKnownPartyFromAnonymous(ALICE.party)
        doReturn(ISSUER.party).whenever(it).wellKnownPartyFromAnonymous(ISSUER.party)
        doReturn(ISSUER.party).whenever(it).wellKnownPartyFromX500Name(ISSUER.party.name)
        doReturn(NOTARY.party).whenever(it).wellKnownPartyFromX500Name(NOTARY.party.name)
        doReturn(ALICE.party).whenever(it).wellKnownPartyFromX500Name(ALICE.party.name)

    }

    private companion object {
        val NOTARY = TestIdentity(DUMMY_NOTARY_NAME, 20)
        val ISSUER = TestIdentity(CordaX500Name("ISSUER", "London", "GB"))
        val ALICE = TestIdentity(CordaX500Name("ALICE", "London", "GB"))
        val BOB = TestIdentity(CordaX500Name("BOB", "London", "GB"))
    }

    @Rule
    @JvmField
    val testSerialization = SerializationEnvironmentRule()

    private val aliceDbAndServices = MockServices.makeTestDatabaseAndMockServices(
            cordappPackages = listOf("net.corda.sdk.token"),
            initialIdentity = ALICE,
            identityService = mockIdentityService,
            networkParameters = testNetworkParameters(minimumPlatformVersion = 4)
    )

    val aliceDB = aliceDbAndServices.first
    val aliceServices = aliceDbAndServices.second

    private val bobDbAndServices = MockServices.makeTestDatabaseAndMockServices(
            cordappPackages = listOf("net.corda.sdk.token"),
            initialIdentity = BOB,
            identityService = mockIdentityService,
            networkParameters = testNetworkParameters(minimumPlatformVersion = 4)
    )

    val bobDB = aliceDbAndServices.first
    val bobServices = aliceDbAndServices.second

    private val issuerDbAndServices = MockServices.makeTestDatabaseAndMockServices(
            cordappPackages = listOf("net.corda.sdk.token"),
            initialIdentity = ISSUER,
            identityService = mockIdentityService,
            networkParameters = testNetworkParameters(minimumPlatformVersion = 4)
    )

    val issuerDB = aliceDbAndServices.first
    val issuerServices = aliceDbAndServices.second

    @Test
    fun `creating inlined token definition`() {
        // Some amount of GBP issued by the Bank of England and owned by Alice.
        val tenPoundsIssuedByIssuerOwnedByAlice: OwnedTokenAmount<FiatCurrency> = 10.GBP issuedBy ISSUER.party ownedBy ALICE.party
        // Test everything is assigned correctly.
        assertEquals(GBP, tenPoundsIssuedByIssuerOwnedByAlice.amount.token.product)
        assertEquals(1000, tenPoundsIssuedByIssuerOwnedByAlice.amount.quantity)
        assertEquals(ISSUER.party, tenPoundsIssuedByIssuerOwnedByAlice.amount.token.issuer)
        assertEquals(ALICE.party, tenPoundsIssuedByIssuerOwnedByAlice.owner)
        println(tenPoundsIssuedByIssuerOwnedByAlice)
    }

    @Test
    fun `evolvable token definition`() {
        // A token representing a house on ledger.
        data class House(
                val address: String,
                val valuation: Amount<FiatCurrency>,
                override val maintainer: Party,
                override val displayTokenSize: BigDecimal = BigDecimal.ZERO,
                override val linearId: UniqueIdentifier = UniqueIdentifier()
        ) : EvolvableToken()

        val house = House("24 Leinster Gardens, Bayswater, London", 1_000_000.GBP, BOB.party)
        val housePointer: TokenPointer<House> = house.toPointer()

        // TODO: Make types more specific here.
        val houseIssuedByBob: Issued<EmbeddableToken> = housePointer issuedBy BOB.party
        val houseIssuedByBobOwnedByAlice: OwnedToken<EmbeddableToken> = houseIssuedByBob ownedBy ALICE.party

        // Now we want to do fractional ownership in this house...
        // Redeem the OwnedToken and reissue it as an OwnedTokenAmount
        val oneHundredUnitsOfHouse: OwnedTokenAmount<TokenPointer<House>> = 100 of housePointer issuedBy BOB.party ownedBy ALICE.party
    }

}
