package net.corda.sdk.token

import net.corda.core.contracts.Amount
import net.corda.sdk.token.states.OwnedToken
import net.corda.sdk.token.states.OwnedTokenAmount
import net.corda.sdk.token.statesAndContracts.House
import net.corda.sdk.token.types.EmbeddableToken
import net.corda.sdk.token.types.Issued
import net.corda.sdk.token.types.TokenPointer
import net.corda.sdk.token.types.money.FiatCurrency
import net.corda.sdk.token.types.money.GBP
import net.corda.sdk.token.utilities.issuedBy
import net.corda.sdk.token.utilities.of
import net.corda.sdk.token.utilities.ownedBy
import org.junit.Test
import kotlin.test.assertEquals

class Examples : LedgerTestWithPersistence() {

    @Test
    fun `creating inlined token definition`() {
        // Some amount of GBP issued by the Bank of England and owned by Alice.
        val tenPoundsIssuedByIssuerOwnedByAlice: OwnedTokenAmount<FiatCurrency> = 10.GBP issuedBy ISSUER.party ownedBy ALICE.party
        val gbp: FiatCurrency = GBP
        val token: Issued<EmbeddableToken> = GBP issuedBy ISSUER.party
        val tokenAmount: Amount<Issued<EmbeddableToken>> = 1_000_000 of token
        val ownedToken: OwnedTokenAmount<EmbeddableToken> = tokenAmount ownedBy ALICE.party
        // Test everything is assigned correctly.
        assertEquals(GBP, tenPoundsIssuedByIssuerOwnedByAlice.amount.token.product)
        assertEquals(1000, tenPoundsIssuedByIssuerOwnedByAlice.amount.quantity)
        assertEquals(ISSUER.party, tenPoundsIssuedByIssuerOwnedByAlice.amount.token.issuer)
        assertEquals(ALICE.party, tenPoundsIssuedByIssuerOwnedByAlice.owner)
        println(tenPoundsIssuedByIssuerOwnedByAlice)
    }

    @Test
    fun `evolvable token definition`() {
        val house = House("24 Leinster Gardens, Bayswater, London", 1_000_000.GBP, listOf(BOB.party))
        val housePointer: TokenPointer<House> = house.toPointer()

        // TODO: Make types more specific here.
        val houseIssuedByBob: Issued<EmbeddableToken> = housePointer issuedBy BOB.party
        val houseIssuedByBobOwnedByAlice: OwnedToken<EmbeddableToken> = houseIssuedByBob ownedBy ALICE.party

        // Now we want to do fractional ownership in this house...
        // Redeem the OwnedToken and reissue it as an OwnedTokenAmount
        val oneHundredUnitsOfHouse: OwnedTokenAmount<TokenPointer<House>> = 100 of housePointer issuedBy BOB.party ownedBy ALICE.party
    }

}
