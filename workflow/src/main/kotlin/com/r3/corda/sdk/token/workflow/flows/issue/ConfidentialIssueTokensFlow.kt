package com.r3.corda.sdk.token.workflow.flows.issue

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.sdk.token.contracts.states.AbstractToken
import com.r3.corda.sdk.token.contracts.types.TokenType
import com.r3.corda.sdk.token.workflow.flows.confidential.ConfidentialTokensFlow
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.flows.InitiatingFlow
import net.corda.core.transactions.SignedTransaction

@InitiatingFlow
class ConfidentialIssueTokensFlow<T : TokenType>(
        val tokens: List<AbstractToken<T>>,
        val sessions: Set<FlowSession>
) : FlowLogic<SignedTransaction>() {
    @Suspendable
    override fun call(): SignedTransaction {
        // Get all proposed holders.
        val confidentialTokens = subFlow(ConfidentialTokensFlow(tokens, sessions))
        // Issue tokens.
        return subFlow(IssueTokensFlow(confidentialTokens, sessions.toList()))
    }
}
