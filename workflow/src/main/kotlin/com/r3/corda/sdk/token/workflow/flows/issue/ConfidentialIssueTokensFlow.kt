package com.r3.corda.sdk.token.workflow.flows.issue

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.sdk.token.contracts.states.AbstractToken
import com.r3.corda.sdk.token.contracts.types.TokenType
import com.r3.corda.sdk.token.workflow.flows.confidential.RequestConfidentialIdentityFlow
import net.corda.core.contracts.ContractState
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.flows.InitiatingFlow
import net.corda.core.transactions.SignedTransaction

enum class ActionRequest { NOTHING, ISSUE_NEW_KEY }

@InitiatingFlow
class ConfidentialIssueTokensFlow<T : TokenType>(
        val tokens: List<AbstractToken<T>>,
        val sessions: Set<FlowSession>
) : FlowLogic<SignedTransaction>() {
    @Suspendable
    override fun call(): SignedTransaction {
        // Get all proposed holders.
        val tokenHolders = tokens.flatMap(ContractState::participants).toSet()
        // Notify parties of confidential issuance.
        // Obtain a confidential identity from each holder.
        val anonymousParties = sessions.mapNotNull { session ->
            val counterparty = session.counterparty
            if (counterparty in tokenHolders) {
                session.send(ActionRequest.ISSUE_NEW_KEY)
                val partyAndCertificate = subFlow(RequestConfidentialIdentityFlow(session))
                Pair(counterparty, partyAndCertificate.party.anonymise())
            } else {
                session.send(ActionRequest.NOTHING)
                null
            }
        }.toMap()
        // Replace Party with AnonymousParty.
        val confidentialTokens = tokens.map { token ->
            val holder = token.holder
            val anonymousParty = anonymousParties[holder]
                    ?: throw IllegalStateException("Missing anonymous party for $holder.")
            token.withNewHolder(anonymousParty)
        }
        // Issue tokens.
        return subFlow(IssueTokensFlow(confidentialTokens, sessions.toList()))
    }
}