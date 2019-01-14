package net.corda.sdk.token.statesAndContracts

import net.corda.core.contracts.Amount
import net.corda.core.contracts.BelongsToContract
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.Party
import net.corda.core.transactions.LedgerTransaction
import net.corda.sdk.token.contracts.EvolvableTokenContract
import net.corda.sdk.token.types.EvolvableToken
import net.corda.sdk.token.types.money.FiatCurrency
import java.math.BigDecimal

// A token representing a house on ledger.
@BelongsToContract(HouseContract::class)
data class House(
        val address: String,
        val valuation: Amount<FiatCurrency>,
        override val maintainers: List<Party>,
        override val displayTokenSize: BigDecimal = BigDecimal.ONE,
        override val linearId: UniqueIdentifier = UniqueIdentifier()
) : EvolvableToken()

class HouseContract : EvolvableTokenContract() {

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

    override fun additionalDeleteChecks(tx: LedgerTransaction) {
        // TODO: Add some checks.
    }
}