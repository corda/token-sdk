package com.r3.corda.sdk.token.workflow.flows.issue

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.sdk.token.contracts.states.AbstractToken
import com.r3.corda.sdk.token.contracts.types.TokenType
import com.r3.corda.sdk.token.workflow.flows.confidential.ConfidentialTokensFlow
import com.r3.corda.sdk.token.workflow.utilities.sessionsForParicipants
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.flows.InitiatingFlow
import net.corda.core.transactions.SignedTransaction

@InitiatingFlow
class ConfidentialIssueTokensFlow<T : TokenType>(
        val tokens: List<AbstractToken<T>>,
        val existingSessions: Set<FlowSession>
) : FlowLogic<SignedTransaction>() {
    @Suspendable
    override fun call(): SignedTransaction {
        val sessions = if (existingSessions.isEmpty()) sessionsForParicipants(tokens, emptySet()) else existingSessions
        // Get all proposed holders.
        val confidentialTokens = subFlow(ConfidentialTokensFlow(tokens, sessions))
        // Issue tokens.
        return subFlow(IssueTokensFlow(confidentialTokens, sessions.toList()))
    }
}
