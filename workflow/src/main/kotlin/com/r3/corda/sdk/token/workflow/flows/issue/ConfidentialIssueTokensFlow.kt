package com.r3.corda.sdk.token.workflow.flows.issue

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.sdk.token.contracts.states.AbstractToken
import com.r3.corda.sdk.token.contracts.types.TokenType
import com.r3.corda.sdk.token.workflow.flows.confidential.ConfidentialTokensFlow
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.transactions.SignedTransaction

/**
 * A flow for issuing tokens to confidential keys.
 */
class ConfidentialIssueTokensFlow<T : TokenType>(
        val tokens: List<AbstractToken<T>>,
        val participantSessions: List<FlowSession>,
        val observerSessions: List<FlowSession>
) : FlowLogic<SignedTransaction>() {
    @Suspendable
    override fun call(): SignedTransaction {
        // Request new keys pairs from all proposed token holders.
        val confidentialTokens = subFlow(ConfidentialTokensFlow(tokens, participantSessions))
        // Issue tokens using the existing participantSessions.
        return subFlow(IssueTokensFlow(confidentialTokens, participantSessions, observerSessions))
    }
}
