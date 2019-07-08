package com.r3.corda.lib.tokens.workflows

import com.r3.corda.lib.tokens.contracts.states.FungibleToken
import com.r3.corda.lib.tokens.contracts.types.IssuedTokenType
import com.r3.corda.lib.tokens.contracts.types.TokenPointer
import com.r3.corda.lib.tokens.contracts.types.TokenType
import com.r3.corda.lib.tokens.contracts.utilities.heldBy
import com.r3.corda.lib.tokens.contracts.utilities.issuedBy
import com.r3.corda.lib.tokens.contracts.utilities.of
import com.r3.corda.lib.tokens.money.GBP
import com.r3.corda.lib.tokens.testing.states.House
import com.r3.corda.lib.tokens.workflows.utilities.heldBy
import net.corda.core.contracts.UniqueIdentifier
import org.junit.Test
import kotlin.test.assertEquals

class Examples : LedgerTestWithPersistence() {

    @Test
    fun `creating inlined token definition`() {
        // Some amount of GBP issued by the Bank of England and held by Alice.
        val tenPoundsIssuedByIssuerHeldByAlice: FungibleToken<TokenType> = 10.GBP issuedBy ISSUER.party heldBy ALICE.party
        // Test everything is assigned correctly.
        assertEquals(GBP, tenPoundsIssuedByIssuerHeldByAlice.amount.token.tokenType)
        assertEquals(1000, tenPoundsIssuedByIssuerHeldByAlice.amount.quantity)
        assertEquals(ISSUER.party, tenPoundsIssuedByIssuerHeldByAlice.amount.token.issuer)
        assertEquals(ALICE.party, tenPoundsIssuedByIssuerHeldByAlice.holder)
        println(tenPoundsIssuedByIssuerHeldByAlice)
    }

    @Test
    fun `evolvable token definition`() {
        val house = House("24 Leinster Gardens, Bayswater, London", 900_000.GBP, listOf(BOB.party), linearId = UniqueIdentifier())
        val housePointer: TokenPointer<House> = house.toPointer()
        val houseIssuedByBob: IssuedTokenType<TokenPointer<House>> = housePointer issuedBy BOB.party
        houseIssuedByBob heldBy ALICE.party

        // Now we want to do fractional ownership in this house...
        // Redeem the NonFungibleToken and reissue it as an FungibleToken
        100 of housePointer issuedBy BOB.party heldBy ALICE.party
    }

}
