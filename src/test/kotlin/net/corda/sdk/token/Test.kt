package net.corda.sdk.token

import net.corda.core.contracts.Amount
import net.corda.core.contracts.LinearPointer
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.sdk.token.states.OwnedToken
import net.corda.sdk.token.states.OwnedTokenAmount
import net.corda.sdk.token.types.Issued
import net.corda.sdk.token.types.money.FiatCurrency
import net.corda.sdk.token.types.token.Token
import net.corda.testing.core.TestIdentity
import org.junit.Test
import java.math.BigDecimal

class Test {

    val ALICE = TestIdentity(CordaX500Name("Alice", "London", "GB"))
    val BOB = TestIdentity(CordaX500Name("Bob", "New York", "GB"))
    val BOE = TestIdentity(CordaX500Name("Bank of England", "London", "GB"))

    @Test
    fun `inlined token definition`() {
        // Inlined token for GBP stable coin.
        val pounds: FiatCurrency = GBP
        // Some amount of stable coin.
        val tenPounds: Amount<FiatCurrency> = 10.GBP
        // Some amount of stable coin issued by bank of england.
        val tenPoundsIssuedByBandOfEngland: OwnedTokenAmount<FiatCurrency> = tenPounds `issued by` BOE.party `owned by` ALICE.party
        println(tenPoundsIssuedByBandOfEngland)
    }

    @Test
    fun `evolvable token definition`() {
        // A token representing a house on ledger.
        data class House(
                val address: String,
                val valuation: Amount<FiatCurrency>,
                override val maintainer: Party,
                override val displayTokenSize: BigDecimal = BigDecimal.ZERO
        ) : Token.EvolvableDefinition(maintainer) {
            // This is non fungible for now but could be fungible in the future if needs be.

            override fun toPointer(): Token.Pointer<House> {
                return Token.Pointer(LinearPointer(linearId, House::class.java), displayTokenSize)
            }
        }

        val house = House("24 Leinster Gardens, Bayswater, London", 1_000_000.GBP, BOE.party)
        val housePointer: Token.Pointer<House> = house.toPointer()
        val houseIssuedByBob: Issued<Token> = housePointer `issued by` BOB.party
        val houseIssuedByBobOwnedByAlice: OwnedToken<Token> = houseIssuedByBob `owned by` ALICE.party

        // Now we want to do fractional ownership in this house...
        // Redeem the OwnedToken and reissue it as an OwnedTokenAmount
        // TODO: figure out how to do amounts of some evolvable token using teh linear ID.
        val oneHundredUnitsOfOwnershipInHouse = Amount(100L, houseIssuedByBob)
        val oneHundredUnitsOfOwnershipInHouseOwnedByAlice: OwnedTokenAmount<Token> = oneHundredUnitsOfOwnershipInHouse `owned by` ALICE.party
    }


}

