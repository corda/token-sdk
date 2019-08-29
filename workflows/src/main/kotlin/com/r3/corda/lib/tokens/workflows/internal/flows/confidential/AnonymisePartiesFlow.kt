package com.r3.corda.lib.tokens.workflows.internal.flows.confidential

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.lib.ci.RequestKeyFlow
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.identity.AnonymousParty
import net.corda.core.identity.Party

/**
 * This flow notifies prospective token holders that they must generate a new key pair. As this is an in-line sub-flow,
 * we must pass it a list of sessions, which _may_ contain sessions for observers. As such, only the parties that need
 * to generate a new key are sent a [ActionRequest.CREATE_NEW_KEY] notification and everyone else is sent
 * [ActionRequest.DO_NOTHING].
 */
class AnonymisePartiesFlow(
        val parties: List<Party>,
        val sessions: List<FlowSession>
) : FlowLogic<Map<Party, AnonymousParty>>() {
    @Suspendable
    override fun call(): Map<Party, AnonymousParty> {
        val sessionParties = sessions.map(FlowSession::counterparty)
        require(sessionParties.containsAll(parties)) { "You must provide sessions for all parties." }
        return sessions.mapNotNull { session ->
            val party = session.counterparty
            if (party in parties) {
                session.send(ActionRequest.CREATE_NEW_KEY)
                val anonParty = subFlow(RequestKeyFlow(session))
                Pair(party, anonParty)
            } else {
                session.send(ActionRequest.DO_NOTHING)
                null
            }
        }.toMap()
    }
}