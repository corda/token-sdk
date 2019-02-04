package com.r3.corda.sdk.token.workflow

import com.r3.corda.sdk.token.contracts.states.OwnedToken
import com.r3.corda.sdk.token.contracts.states.OwnedTokenAmount
import com.r3.corda.sdk.token.contracts.types.Issued
import com.r3.corda.sdk.token.contracts.types.TokenPointer
import com.r3.corda.sdk.token.contracts.utilities.issuedBy
import com.r3.corda.sdk.token.contracts.utilities.of
import com.r3.corda.sdk.token.contracts.utilities.ownedBy
import com.r3.corda.sdk.token.money.FiatCurrency
import com.r3.corda.sdk.token.money.GBP
import com.r3.corda.sdk.token.workflow.statesAndContracts.House
import org.junit.Test
import kotlin.test.assertEquals

class Examples : LedgerTestWithPersistence() {

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
        val house = House("24 Leinster Gardens, Bayswater, London", 900_000.GBP, listOf(BOB.party))
        val housePointer: TokenPointer<House> = house.toPointer()
        val houseIssuedByBob: Issued<TokenPointer<House>> = housePointer issuedBy BOB.party
        val houseIssuedByBobOwnedByAlice: OwnedToken<TokenPointer<House>> = houseIssuedByBob ownedBy ALICE.party

        // Now we want to do fractional ownership in this house...
        // Redeem the OwnedToken and reissue it as an OwnedTokenAmount
        val oneHundredUnitsOfHouse: OwnedTokenAmount<TokenPointer<House>> = 100 of housePointer issuedBy BOB.party ownedBy ALICE.party
    }

}
