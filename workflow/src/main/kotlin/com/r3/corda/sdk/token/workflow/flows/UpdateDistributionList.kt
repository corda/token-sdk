package com.r3.corda.sdk.token.workflow.flows

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.sdk.token.contracts.states.EvolvableTokenType
import com.r3.corda.sdk.token.contracts.types.TokenPointer
import com.r3.corda.sdk.token.workflow.schemas.DistributionRecord
import com.r3.corda.sdk.token.workflow.utilities.addPartyToDistributionList
import com.r3.corda.sdk.token.workflow.utilities.getDistributionList
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.crypto.SignedData
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.flows.InitiatedBy
import net.corda.core.flows.InitiatingFlow
import net.corda.core.identity.Party
import net.corda.core.serialization.CordaSerializable
import net.corda.core.serialization.serialize
import net.corda.core.utilities.unwrap
import java.util.*
import javax.persistence.criteria.CriteriaQuery

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
            // Collect signatures from old and new parties
            val updateBytes = distributionListUpdate.serialize()
            val ourSig = serviceHub.keyManagementService.sign(updateBytes.bytes, oldParty.owningKey)
            val signedUpdate = SignedData(updateBytes, ourSig)
            // TODO This is naive quick fix approach for now, we should use data distribution groups
            maintainersSessions.forEach {
                it.send(signedUpdate)
            }
        }
    }

    @InitiatedBy(UpdateDistributionList.Initiator::class)
    class Responder(val otherSession: FlowSession) : FlowLogic<Unit>() {
        @Suspendable
        override fun call() {
            val distListUpdate = otherSession.receive<SignedData<DistributionListUpdate>>().unwrap {
                val update = it.verified()
                // Check that request is signed by the oldParty.
                check(update.oldParty.owningKey == it.sig.by) {
                    "Got distribution list update request signed by a wrong party."
                }
                // Check that the request comes from that party.
                check(update.oldParty == otherSession.counterparty) {
                    "Got distribution list update request from a counterparty: ${otherSession.counterparty} " +
                            "that isn't a signer of request: ${update.oldParty}."
                }
                update
            }
            // Check that newParty is well known party.
            serviceHub.identityService.wellKnownPartyFromAnonymous(distListUpdate.newParty)
                    ?: throw IllegalArgumentException("Don't know about party: ${distListUpdate.newParty}")
            if (getDistributionRecord(distListUpdate.linearId, distListUpdate.newParty).isEmpty()) {
                // Add new party to the dist list for this token.
                serviceHub.addPartyToDistributionList(distListUpdate.newParty, distListUpdate.linearId)
            }
        }

        @Suspendable
        private fun getDistributionRecord(linearId: UniqueIdentifier, party: Party): List<DistributionRecord> {
            return serviceHub.withEntityManager {
                val query: CriteriaQuery<DistributionRecord> = criteriaBuilder.createQuery(DistributionRecord::class.java)
                query.apply {
                    val root = from(DistributionRecord::class.java)
                    val linearIdEq = criteriaBuilder.equal(root.get<UUID>("linearId"), linearId.id)
                    val partyEq = criteriaBuilder.equal(root.get<Party>("party"), party)
                    where(criteriaBuilder.and(linearIdEq, partyEq))
                    select(root)
                }
                createQuery(query).resultList
            }
        }
    }
}
