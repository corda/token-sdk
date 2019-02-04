package com.r3.corda.sdk.token.workflow.flows

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.sdk.token.workflow.schemas.DistributionRecord
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.StartableByRPC
import net.corda.core.identity.Party

/**
 * Simple flow to persist a new entity pertaining to a distribution record.
 * TODO: Add some error handling.
 */
@StartableByRPC
class AddPartyToDistributionList(val party: Party, val linearId: UniqueIdentifier) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        logger.info("Adding ${party.name} to distribution list for $linearId.")
        // Create an persist a new entity.
        val distributionRecord = DistributionRecord(linearId.id, party)
        serviceHub.withEntityManager { persist(distributionRecord) }
    }
}