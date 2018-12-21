package net.corda.sdk.token

import net.corda.core.contracts.Amount
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.sdk.token.states.OwnedToken
import net.corda.sdk.token.states.OwnedTokenAmount
import net.corda.sdk.token.types.Issued
import net.corda.sdk.token.types.money.FiatCurrency
import net.corda.sdk.token.types.token.EmbeddableToken
import net.corda.sdk.token.types.token.EvolvableToken
import net.corda.sdk.token.types.token.TokenPointer
import net.corda.testing.core.TestIdentity
import org.junit.Test
import java.math.BigDecimal
import kotlin.test.assertEquals

class Test {

    val ALICE = TestIdentity(CordaX500Name("Alice", "London", "GB"))
    val BOB = TestIdentity(CordaX500Name("Bob", "New York", "GB"))

    @Test
    fun `creating inlined token definition`() {
        // Some amount of GBP issued by the Bank of England and owned by Alice.
        val tenPoundsIssuedByBobOwnedByAlice = 10.GBP issuedBy BOB.party ownedBy ALICE.party
        // Test everything is assigned correctly.
        assertEquals(GBP, tenPoundsIssuedByBobOwnedByAlice.amount.token.product)
        assertEquals(1000, tenPoundsIssuedByBobOwnedByAlice.amount.quantity)
        assertEquals(BOB.party, tenPoundsIssuedByBobOwnedByAlice.amount.token.issuer)
        assertEquals(ALICE.party, tenPoundsIssuedByBobOwnedByAlice.owner)
        println(tenPoundsIssuedByBobOwnedByAlice)
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
        val oneUnitsOfHouse: OwnedTokenAmount<TokenPointer<House>> = 100 of housePointer issuedBy BOB.party ownedBy ALICE.party
    }

}

