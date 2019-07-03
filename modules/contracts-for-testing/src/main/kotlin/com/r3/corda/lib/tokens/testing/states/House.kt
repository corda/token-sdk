package com.r3.corda.lib.tokens.testing.states

import com.r3.corda.lib.tokens.contracts.states.EvolvableTokenType
import com.r3.corda.lib.tokens.money.FiatCurrency
import com.r3.corda.lib.tokens.testing.contracts.HouseContract
import net.corda.core.contracts.Amount
import net.corda.core.contracts.BelongsToContract
import net.corda.core.contracts.Contract
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.Party
import net.corda.core.transactions.LedgerTransaction

// A token representing a house on ledger.
@BelongsToContract(HouseContract::class)
data class House(
        val address: String,
        val valuation: Amount<FiatCurrency>,
        override val maintainers: List<Party>,
        override val fractionDigits: Int = 5,
        override val linearId: UniqueIdentifier
) : EvolvableTokenType()

class Test : Contract {
    override fun verify(tx: LedgerTransaction) {

    }
}