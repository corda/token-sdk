package com.r3.corda.sdk.token.workflow.flows

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.sdk.token.contracts.commands.Update
import com.r3.corda.sdk.token.contracts.states.EvolvableTokenType
import com.r3.corda.sdk.token.workflow.schemas.DistributionRecord
import com.r3.corda.sdk.token.workflow.utilities.getDistributionList
import net.corda.core.contracts.StateAndRef
import net.corda.core.flows.FinalityFlow
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.StartableByRPC
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder

@InitiatingFlow
@StartableByRPC
class UpdateEvolvableToken(
        val old: StateAndRef<EvolvableTokenType>,
        val new: EvolvableTokenType
) : FlowLogic<SignedTransaction>() {
    @Suspendable
    override fun call(): SignedTransaction {
        // Create a transaction which updates the ledger with the new evolvable token.
        // The parties listed as maintainers in the old state should be the signers.
        val signingKeys = old.state.data.maintainers.map { it.owningKey }
        val utx: TransactionBuilder = TransactionBuilder(notary = old.state.notary).apply {
            addCommand(data = Update(), keys = signingKeys)
            addInputState(old)
            addOutputState(state = new, contract = old.state.contract)
        }
        // Sign the transaction. Only Concrete Parties should be used here.
        val stx: SignedTransaction = serviceHub.signInitialTransaction(utx)
        // Get the list of parties on the distribution list for this evolvable token.
        val updateSubscribers: List<DistributionRecord> = getDistributionList(serviceHub, new.linearId)
        val sessions = updateSubscribers.map { initiateFlow(it.party) }
        // Send to all on the list. This could take a while if there are many subscribers!
        return subFlow(FinalityFlow(transaction = stx, sessions = sessions))
    }
}