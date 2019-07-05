package com.r3.corda.lib.tokens.workflows.internal.flows.distribution

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.lib.tokens.contracts.states.AbstractToken
import com.r3.corda.lib.tokens.contracts.types.TokenPointer
import com.r3.corda.lib.tokens.workflows.internal.schemas.DistributionRecord
import com.r3.corda.lib.tokens.workflows.utilities.addPartyToDistributionList
import com.r3.corda.lib.tokens.workflows.utilities.requireKnownConfidentialIdentity
import com.r3.corda.lib.tokens.workflows.utilities.toParty
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.flows.FlowLogic
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

@Suspendable
fun FlowLogic<*>.addToDistributionList(tokens: List<AbstractToken>) {
    tokens.forEach { token ->
        val tokenType = token.tokenType as? TokenPointer<*> ?: throw IllegalStateException()
        val pointer = tokenType.pointer.pointer
        val holder = token.holder.toParty(serviceHub)
        addPartyToDistributionList(holder, pointer)
    }
}

@Suspendable
fun FlowLogic<*>.updateDistributionList(tokens: List<AbstractToken>) {
    for (token in tokens) {
        val tokenType = token.tokenType as? TokenPointer<*> ?: throw IllegalStateException()
        val holderParty = serviceHub.identityService.requireKnownConfidentialIdentity(token.holder)
        val evolvableToken = tokenType.pointer.resolve(serviceHub).state.data
        val distributionListUpdate = DistributionListUpdate(ourIdentity, holderParty, evolvableToken.linearId)
        val maintainers = evolvableToken.maintainers
        val maintainersSessions = maintainers.map(::initiateFlow)
        maintainersSessions.forEach {
            it.send(distributionListUpdate)
        }
    }
}