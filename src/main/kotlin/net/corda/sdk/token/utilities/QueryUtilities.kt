package net.corda.sdk.token.utilities

import net.corda.core.contracts.LinearState
import net.corda.core.contracts.StateAndRef
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.node.ServiceHub
import net.corda.core.node.services.Vault
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.sdk.token.schemas.DistributionRecord
import java.util.*
import javax.persistence.criteria.CriteriaQuery

/** Utilities for getting tokens from the vault and performing miscellaneous queries. */

// Grabs the latest version of a linear state for a specified linear ID.
inline fun <reified T : LinearState> getLinearStateById(linearId: UniqueIdentifier, services: ServiceHub): StateAndRef<T>? {
    val query = QueryCriteria.LinearStateQueryCriteria(linearId = listOf(linearId), status = Vault.StateStatus.UNCONSUMED)
    return services.vaultService.queryBy<T>(query).states.singleOrNull()
}

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