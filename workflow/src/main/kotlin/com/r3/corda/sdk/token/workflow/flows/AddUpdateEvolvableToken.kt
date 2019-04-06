package com.r3.corda.sdk.token.workflow.flows

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.sdk.token.contracts.commands.Update
import com.r3.corda.sdk.token.contracts.states.EvolvableTokenType
import net.corda.core.contracts.StateAndRef
import net.corda.core.flows.FlowLogic
import net.corda.core.identity.Party
import net.corda.core.transactions.TransactionBuilder

/**
 * Helper flow for assembling an [Update] transaction for [EvolvableToken]s. This accepts a [TransactionBuilder] as well
 * as old and new states, and adds commands, inputs, and outputs to the transaction. Returns the [TransactionBuilder]
 * to allow for continued assembly of the transaction, as needed.
 */
class AddUpdateEvolvableToken(
        val transactionBuilder: TransactionBuilder,
        val oldStateAndRef: StateAndRef<EvolvableTokenType>,
        val newState: EvolvableTokenType
) : FlowLogic<TransactionBuilder>() {

    @Suspendable
    override fun call(): TransactionBuilder {
        return transactionBuilder.apply {
            addCommand(data = Update(), keys = signingKeys)
            addInputState(oldStateAndRef)
            addOutputState(state = newState, contract = oldStateAndRef.state.contract)
        }
    }

    private val oldState get() = oldStateAndRef.state.data

    private val maintainers get(): Set<Party> = (oldState.maintainers + newState.maintainers).toSet()

    private val signingKeys get() = maintainers.map { it.owningKey }

}