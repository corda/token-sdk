package com.r3.corda.sdk.token.workflow.utilities

import com.r3.corda.sdk.token.workflow.schemas.DistributionRecord
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.Party
import net.corda.core.node.ServiceHub

/**
 * Utility function to persist a new entity pertaining to a distribution record.
 * TODO: Add some error handling.
 * TODO: Don't duplicate pairs of linearId and party.
 */
fun ServiceHub.addPartyToDistributionList(party: Party, linearId: UniqueIdentifier) {
    // Create an persist a new entity.
    val distributionRecord = DistributionRecord(linearId.id, party)
    withEntityManager { persist(distributionRecord) }
}