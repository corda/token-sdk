package net.corda.sdk.token

import net.corda.core.contracts.Amount
import net.corda.core.contracts.LinearPointer
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.sdk.token.states.Token
import net.corda.sdk.token.types.Issuable
import net.corda.sdk.token.types.Redeemable
import net.corda.sdk.token.types.TokenType
import net.corda.testing.core.TestIdentity
import org.junit.Test
import java.math.BigDecimal
import java.security.PublicKey
import java.util.*

class Test {

    val ALICE = TestIdentity(CordaX500Name("Alice", "London", "GB"))
    val BOB = TestIdentity(CordaX500Name("Bob", "New York", "GB"))
    val BOE = TestIdentity(CordaX500Name("Bank of England", "London", "GB"))

    data class CentralBankReserves(
            val currency: Currency,
            override val issuer: Party,
            override val linearId: UniqueIdentifier = UniqueIdentifier()
    ) : TokenType.EvolvableDefinition(), Issuable, Redeemable {
        override val displayTokenSize: BigDecimal get() = BigDecimal.ONE.scaleByPowerOfTen(-currency.defaultFractionDigits)
        override val participants: List<AbstractParty> get() = listOf(issuer)
        override val exitKeys: Set<PublicKey> get() = setOf(issuer.owningKey)
        override fun toPointer(): TokenType.Pointer<CentralBankReserves> {
            return TokenType.Pointer(LinearPointer(linearId, CentralBankReserves::class.java), displayTokenSize)
        }
    }

    @Test
    fun test() {
        val gbpStableCoin: CentralBankReserves = CentralBankReserves(GBP, BOE.party)
        val tenPoundsOfStableCoin: Amount<TokenType.Pointer<CentralBankReserves>> = Amount(10L, gbpStableCoin.toPointer())
        val token: Token<TokenType.Pointer<CentralBankReserves>> = Token(tenPoundsOfStableCoin, BOB.party)
    }

}

