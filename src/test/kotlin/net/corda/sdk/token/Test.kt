package net.corda.sdk.token

import net.corda.core.contracts.Amount
import net.corda.core.contracts.LinearPointer
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
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
        val tenPoundsIssuedByBandOfEngland: Amount<Issued<FiatCurrency>> = tenPounds `issued by` BOE.party
        val tenPoundsOwnedByAlice: OwnedTokenAmount<FiatCurrency> = tenPoundsIssuedByBandOfEngland `owned by` ALICE.party
        println(tenPoundsOwnedByAlice)
    }

    data class Equity(
            val creator: Party,
            val someProperty: String,
            override val linearId: UniqueIdentifier = UniqueIdentifier()
    ) : Token.EvolvableDefinition {
        override val participants: List<AbstractParty> get() = listOf(creator)
        override val displayTokenSize: BigDecimal get() = BigDecimal.ONE
        override fun toPointer(): Token.Pointer<Equity> {
            return Token.Pointer(LinearPointer(linearId, Equity::class.java), displayTokenSize)
        }
    }

    @Test
    fun `evolvable token definition`() {
        val someStock: Equity = Equity(ALICE.party, "ROJ")
        val someStockPointer: Token.Pointer<Equity> = someStock.toPointer()
        val someIssuedStock: Issued<Token> = someStockPointer `issued by` ALICE.party
        val amountOfSomeIssuedStock: Amount<Issued<Token>> = Amount(100L, someIssuedStock)
        val amountOfSomeIssuedStockOwnedByAlice: OwnedTokenAmount<Token> = amountOfSomeIssuedStock `owned by` BOB.party
        println(amountOfSomeIssuedStockOwnedByAlice)
    }


}

