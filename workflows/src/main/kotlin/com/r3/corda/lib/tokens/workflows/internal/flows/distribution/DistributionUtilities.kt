package com.r3.corda.lib.tokens.workflows.internal.flows.distribution

import com.r3.corda.lib.tokens.workflows.internal.schemas.DistributionRecord
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.Party
import net.corda.core.node.ServiceHub
import net.corda.core.serialization.CordaSerializable
import java.util.*
import javax.persistence.criteria.CriteriaQuery

@CordaSerializable
data class DistributionListUpdate(val sender: Party, val receiver: Party, val linearId: UniqueIdentifier)

// Gets the distribution list for a particular token.
fun getDistributionList(services: ServiceHub, linearId: UniqueIdentifier): List<DistributionRecord> {
    return services.withEntityManager {
        val query: CriteriaQuery<DistributionRecord> = criteriaBuilder.createQuery(DistributionRecord::class.java)
        query.apply {
            val root = from(DistributionRecord::class.java)
            where(criteriaBuilder.equal(root.get<UUID>("linearId"), linearId.id))
            select(root)
        }
        createQuery(query).resultList
    }
}

// Gets the distribution record for a particular token and party.
fun getDistributionRecord(serviceHub: ServiceHub, linearId: UniqueIdentifier, party: Party): DistributionRecord? {
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
    }.singleOrNull()
}

fun hasDistributionRecord(serviceHub: ServiceHub, linearId: UniqueIdentifier, party: Party): Boolean {
    return getDistributionRecord(serviceHub, linearId, party) != null
}
