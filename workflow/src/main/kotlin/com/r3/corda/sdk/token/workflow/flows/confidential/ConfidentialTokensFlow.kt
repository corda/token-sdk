package com.r3.corda.sdk.token.workflow.flows.confidential

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.sdk.token.contracts.states.AbstractToken
import com.r3.corda.sdk.token.contracts.types.TokenType
import net.corda.core.contracts.ContractState
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.flows.InitiatedBy
import net.corda.core.flows.InitiatingFlow
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.AnonymousParty
import net.corda.core.identity.Party
import net.corda.core.serialization.CordaSerializable
import net.corda.core.utilities.unwrap

@CordaSerializable
enum class ActionRequest { NOTHING, ISSUE_NEW_KEY }

class ConfidentialTokensFlow<T : TokenType>(
        val tokens: List<AbstractToken<T>>,
        val sessions: Set<FlowSession>
) : FlowLogic<List<AbstractToken<T>>>() {
    @Suspendable
    override fun call(): List<AbstractToken<T>> {
        val tokenHolders = tokens.flatMap(ContractState::participants).toSet()
        val anonymousParties = subFlow(AnonymisePartiesFlow(tokenHolders, sessions))
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

@InitiatingFlow
class AnonymisePartiesFlow(
        val parties: Set<AbstractParty>,
        val sessions: Set<FlowSession>
) : FlowLogic<Map<Party, AnonymousParty>>() {
    @Suspendable
    override fun call(): Map<Party, AnonymousParty> {
        // Notify parties of confidential issuance.
        // Obtain a confidential identity from each holder.
        val anonymousParties = sessions.mapNotNull { session ->
            val counterparty = session.counterparty
            if (counterparty in parties) {
                session.send(ActionRequest.ISSUE_NEW_KEY)
                val partyAndCertificate = subFlow(RequestConfidentialIdentityFlow(session))
                Pair(counterparty, partyAndCertificate.party.anonymise())
            } else {
                session.send(ActionRequest.NOTHING)
                null
            }
        }.toMap()
        return anonymousParties
    }
}

@InitiatedBy(AnonymisePartiesFlow::class)
class AnonymisePartiesFlowHandler(val otherSession: FlowSession) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        val action = otherSession.receive<ActionRequest>().unwrap { it }
        if (action == ActionRequest.ISSUE_NEW_KEY) {
            subFlow(RequestConfidentialIdentityFlowHandler(otherSession))
        }
    }
}