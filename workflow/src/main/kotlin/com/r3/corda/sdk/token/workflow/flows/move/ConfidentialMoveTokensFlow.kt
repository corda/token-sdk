package com.r3.corda.sdk.token.workflow.flows.move

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.sdk.token.contracts.states.AbstractToken
import com.r3.corda.sdk.token.contracts.types.TokenType
import com.r3.corda.sdk.token.workflow.flows.issue.Kerfuffle
import com.r3.corda.sdk.token.workflow.utilities.getPreferredNotary
import net.corda.core.contracts.StateAndRef
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder

class ConfidentialMoveTokensFlow<T : TokenType>(
        val inputs: List<StateAndRef<AbstractToken<T>>>,
        val outputs: List<AbstractToken<T>>,
        val sessions: Set<FlowSession>) : FlowLogic<SignedTransaction>() {
    @Suspendable
    override fun call(): SignedTransaction {
        val transactionBuilder = TransactionBuilder(getPreferredNotary(serviceHub))
        // Get all proposed holders.
        val confidentialTokens = subFlow(Kerfuffle(outputs, sessions))
        addMoveTokens(inputs, confidentialTokens, transactionBuilder)
        return subFlow(FinalizeMoveTokensFlow(transactionBuilder, sessions.toList()))
    }
}
