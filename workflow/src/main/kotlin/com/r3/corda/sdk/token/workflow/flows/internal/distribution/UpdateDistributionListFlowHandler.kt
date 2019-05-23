package com.r3.corda.sdk.token.workflow.flows.internal.distribution

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.sdk.token.workflow.utilities.addPartyToDistributionList
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.flows.InitiatedBy
import net.corda.core.utilities.unwrap

@InitiatedBy(UpdateDistributionListFlow::class)
class UpdateDistributionListFlowHandler(val otherSession: FlowSession) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        val distListUpdate = otherSession.receive<DistributionListUpdate>().unwrap {
            // Check that the request comes from that party.
            check(it.sender == otherSession.counterparty) {
                "Got distribution list update request from a counterparty: ${otherSession.counterparty} " +
                        "that isn't a signer of request: ${it.sender}."
            }
            it
        }
        // Check that receiver is well known party.
        serviceHub.identityService.requireWellKnownPartyFromAnonymous(distListUpdate.receiver)
        addPartyToDistributionList(serviceHub, distListUpdate.receiver, distListUpdate.linearId)
    }
}