package com.r3.corda.sdk.token.workflow.flows.issue

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.sdk.token.contracts.states.AbstractToken
import com.r3.corda.sdk.token.contracts.types.TokenType
import com.r3.corda.sdk.token.workflow.flows.confidential.RequestConfidentialIdentityFlow
import com.r3.corda.sdk.token.workflow.flows.confidential.RequestConfidentialIdentityFlowHandler
import net.corda.core.contracts.ContractState
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.flows.InitiatingFlow
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.unwrap

enum class ActionRequest { NOTHING, ISSUE_NEW_KEY }

@InitiatingFlow
class ConfidentialIssueTokensFlow<T : TokenType>(
        val tokens: List<AbstractToken<T>>,
        val sessions: Set<FlowSession>
) : FlowLogic<SignedTransaction>() {
    @Suspendable
    override fun call(): SignedTransaction {
        // Get all proposed holders.
        val confidentialTokens = subFlow(Kerfuffle(tokens, sessions))
        // Issue tokens.
        return subFlow(IssueTokensFlow(confidentialTokens, sessions.toList()))
    }
}

// TODO rename it's just PoC
class Kerfuffle<T: TokenType>(
        val tokens: List<AbstractToken<T>>,
        val sessions: Set<FlowSession>
): FlowLogic<List<AbstractToken<T>>>() {
    @Suspendable
    override fun call(): List<AbstractToken<T>> {
        val tokenHolders = tokens.flatMap(ContractState::participants).toSet()        // Notify parties of confidential issuance.
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
        return confidentialTokens
    }
}

// TODO rename it's just PoC
class KerfuffleHandler(val otherSession: FlowSession): FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        val action = otherSession.receive<ActionRequest>().unwrap { it }
        if (action == ActionRequest.ISSUE_NEW_KEY) {
            subFlow(RequestConfidentialIdentityFlowHandler(otherSession))
        }
    }
}