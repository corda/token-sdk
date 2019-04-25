package com.r3.corda.sdk.token.workflow.flows

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.sdk.token.contracts.states.EvolvableTokenType
import com.r3.corda.sdk.token.contracts.types.TokenPointer
import com.r3.corda.sdk.token.workflow.utilities.addPartyToDistributionList
import com.r3.corda.sdk.token.workflow.utilities.getDistributionRecord
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.flows.InitiatedBy
import net.corda.core.flows.InitiatingFlow
import net.corda.core.identity.Party
import net.corda.core.serialization.CordaSerializable
import net.corda.core.utilities.unwrap

object UpdateDistributionList {

    @CordaSerializable
    data class DistributionListUpdate(val oldParty: Party, val newParty: Party, val linearId: UniqueIdentifier)

    // TODO It's ineffective if we are heavily using confidential identities, because we contact maintainers every time we do move
    // tokens with token pointers.
    @InitiatingFlow
    class Initiator<T : EvolvableTokenType>(
            val tokenPointer: TokenPointer<T>,
            val oldParty: Party,
            val newParty: Party
    ) : FlowLogic<Unit>() {
        @Suspendable
        override fun call() {
            val evolvableToken = tokenPointer.pointer.resolve(serviceHub).state.data
            val distributionListUpdate = DistributionListUpdate(oldParty, newParty, evolvableToken.linearId)
            val maintainers = evolvableToken.maintainers
            val maintainersSessions = maintainers.map { initiateFlow(it) }
            // TODO This is naive quick fix approach for now, we should use data distribution groups
            maintainersSessions.forEach {
                it.send(distributionListUpdate)
            }
        }
    }

    @InitiatedBy(UpdateDistributionList.Initiator::class)
    class Responder(val otherSession: FlowSession) : FlowLogic<Unit>() {
        @Suspendable
        override fun call() {
            val distListUpdate = otherSession.receive<DistributionListUpdate>().unwrap {
                // Check that the request comes from that party.
                check(it.oldParty == otherSession.counterparty) {
                    "Got distribution list update request from a counterparty: ${otherSession.counterparty} " +
                            "that isn't a signer of request: ${it.oldParty}."
                }
                it
            }
            // Check that newParty is well known party.
            serviceHub.identityService.requireWellKnownPartyFromAnonymous(distListUpdate.newParty)
            if (getDistributionRecord(serviceHub, distListUpdate.linearId, distListUpdate.newParty).isEmpty()) {
                // Add new party to the dist list for this token.
                serviceHub.addPartyToDistributionList(distListUpdate.newParty, distListUpdate.linearId)
            }
        }
    }
}
