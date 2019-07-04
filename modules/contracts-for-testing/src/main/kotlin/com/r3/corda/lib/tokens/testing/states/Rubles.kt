package com.r3.corda.lib.tokens.testing.states

import com.r3.corda.lib.tokens.contracts.FungibleTokenContract
import com.r3.corda.lib.tokens.contracts.states.FungibleToken
import com.r3.corda.lib.tokens.contracts.types.IssuedTokenType
import com.r3.corda.lib.tokens.contracts.types.TokenType
import net.corda.core.contracts.Amount
import net.corda.core.contracts.BelongsToContract
import net.corda.core.contracts.Contract
import net.corda.core.identity.AbstractParty
import net.corda.core.transactions.LedgerTransaction

@BelongsToContract(FungibleTokenContract::class)
open class RubleToken(override val amount: Amount<IssuedTokenType<RUB>>,
                      override val holder: AbstractParty) : FungibleToken<RUB>(amount, holder)

open class Ruble : TokenType("рубль", 0) {
    override fun toString(): String {
        return "Ruble(tokenIdentifier: ${tokenIdentifier}, fractionDigits: ${fractionDigits})"
    }
}

object RUB : Ruble()


open class THING : TokenType("PTK", 0) {
    override fun toString(): String {
        return "THING(tokenIdentifier: ${tokenIdentifier}, fractionDigits: ${fractionDigits})"
    }

}

object PTK : THING()


class Appartment(val id: String = "Foo") : TokenType(id, 0)


@BelongsToContract(DodgeTokenContract::class)
open class DodgeToken(override val amount: Amount<IssuedTokenType<RUB>>,
                      override val holder: AbstractParty) : FungibleToken<RUB>(amount, holder)

open class DodgeTokenContract : Contract {
    override fun verify(tx: LedgerTransaction) {
    }
}