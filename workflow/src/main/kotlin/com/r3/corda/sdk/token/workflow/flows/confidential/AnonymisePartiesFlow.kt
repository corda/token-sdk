package com.r3.corda.sdk.token.workflow.flows.confidential

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.sdk.token.workflow.flows.internal.confidential.ActionRequest
import com.r3.corda.sdk.token.workflow.flows.internal.confidential.RequestConfidentialIdentityFlow
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.AnonymousParty
import net.corda.core.identity.Party

class AnonymisePartiesFlow(
        val parties: List<AbstractParty>,
        val sessions: List<FlowSession>
) : FlowLogic<Map<Party, AnonymousParty>>() {
    @Suspendable
    override fun call(): Map<Party, AnonymousParty> {
        // TODO: Need more checking here - that list of session parties is equal to list of parties supplied.
        // TODO: This code will require updating to support the use of accounts.
        // for now, it is assumed that confidential identities will represent legal identities, only.
        return sessions.mapNotNull { session ->
            val party = session.counterparty
            if (party in parties) {
                session.send(ActionRequest.CREATE_NEW_KEY)
                val partyAndCertificate = subFlow(RequestConfidentialIdentityFlow(session))
                Pair(party, partyAndCertificate.party.anonymise())
            } else {
                session.send(ActionRequest.DO_NOTHING)
                null
            }
        }.toMap()
    }
}