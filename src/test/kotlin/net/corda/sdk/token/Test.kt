package net.corda.sdk.token

import net.corda.core.contracts.Amount
import net.corda.core.identity.CordaX500Name
import net.corda.sdk.token.states.OwnedTokenAmount
import net.corda.sdk.token.types.Issued
import net.corda.sdk.token.types.money.FiatCurrency
import net.corda.testing.core.TestIdentity
import org.junit.Test

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
        val tenPoundsIssuedByBandOfEngland: Amount<Issued<FiatCurrency>> = tenPounds `issued by` BOE.party
        val tenPoundsOwnedByAlice: OwnedTokenAmount<FiatCurrency> = tenPoundsIssuedByBandOfEngland `owned by` ALICE.party
        println(tenPoundsOwnedByAlice)
    }

}

