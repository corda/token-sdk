package com.r3.corda.sdk.token.test.statesAndContracts

import com.r3.corda.sdk.token.contracts.EvolvableTokenContract
import com.r3.corda.sdk.token.contracts.states.EvolvableTokenType
import com.r3.corda.sdk.token.money.FiatCurrency
import net.corda.core.contracts.Amount
import net.corda.core.contracts.BelongsToContract
import net.corda.core.contracts.Contract
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.Party
import net.corda.core.transactions.LedgerTransaction
import java.math.BigDecimal

@BelongsToContract(HouseContract::class)
data class House(
        val address: String,
        val valuation: Amount<FiatCurrency>,
        override val maintainers: List<Party>,
        override val displayTokenSize: BigDecimal = BigDecimal.TEN,
        override val linearId: UniqueIdentifier = UniqueIdentifier()
) : EvolvableTokenType()

// TODO: When contract scanning bug is fixed then this does not need to implement Contract.
class HouseContract : EvolvableTokenContract(), Contract {

    override fun additionalCreateChecks(tx: LedgerTransaction) {
        // Not much to do for this example token.
        val newHouse = tx.outputStates.single() as House
        newHouse.apply {
            require(valuation > Amount.zero(valuation.token)) { "Valuation must be greater than zero." }
        }
    }

    override fun additionalUpdateChecks(tx: LedgerTransaction) {
        val oldHouse = tx.inputStates.single() as House
        val newHouse = tx.outputStates.single() as House
        require(oldHouse.address == newHouse.address) { "The address cannot change." }
        require(newHouse.valuation > Amount.zero(newHouse.valuation.token)) { "Valuation must be greater than zero." }
    }

}