package com.r3.corda.sdk.token.workflow.utilities

import com.r3.corda.sdk.token.contracts.schemas.PersistentFungibleToken
import com.r3.corda.sdk.token.contracts.schemas.PersistentNonFungibleToken
import com.r3.corda.sdk.token.contracts.states.FungibleToken
import com.r3.corda.sdk.token.contracts.states.NonFungibleToken
import com.r3.corda.sdk.token.contracts.types.TokenType
import com.r3.corda.sdk.token.workflow.schemas.DistributionRecord
import net.corda.core.contracts.Amount
import net.corda.core.contracts.LinearState
import net.corda.core.contracts.StateAndRef
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.node.ServiceHub
import net.corda.core.node.services.Vault
import net.corda.core.node.services.VaultService
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.node.services.vault.Sort
import net.corda.core.node.services.vault.SortAttribute
import net.corda.core.node.services.vault.builder
import java.util.*
import javax.persistence.criteria.CriteriaQuery

/** Miscellaneous helpers. */

// Grabs the latest version of a linear state for a specified linear ID.
inline fun <reified T : LinearState> VaultService.getLinearStateById(linearId: UniqueIdentifier): StateAndRef<T>? {
    val query = QueryCriteria.LinearStateQueryCriteria(linearId = listOf(linearId), status = Vault.StateStatus.UNCONSUMED, relevancyStatus = Vault.RelevancyStatus.ALL)
    return queryBy<T>(query).states.singleOrNull()
}

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

/** Utilities for getting tokens from the vault and performing miscellaneous queries. */

// TODO: Add queries for getting the balance of all tokens, not just relevant ones.
// TODO: Allow discrimination by issuer or a set of issuers.

// Returns all owned token amounts of a specified token.
// We need to discriminate on the token type as well as the symbol as different tokens might use the same symbols.
internal fun <T : TokenType> ownedTokenAmountCriteria(embeddableToken: T): QueryCriteria {
    val tokenClass = builder {
        PersistentFungibleToken::tokenClass.equal(embeddableToken.tokenClass)
    }
    val tokenClassCriteria = QueryCriteria.VaultCustomQueryCriteria(tokenClass)
    val tokenIdentifier = builder {
        PersistentFungibleToken::tokenIdentifier.equal(embeddableToken.tokenIdentifier)
    }
    val tokenIdentifierCriteria = QueryCriteria.VaultCustomQueryCriteria(tokenIdentifier)
    return tokenClassCriteria.and(tokenIdentifierCriteria)
}

// Sorts a query by state ref ascending.
internal fun sortByStateRefAscending(): Sort {
    val sortAttribute = SortAttribute.Standard(Sort.CommonStateAttribute.STATE_REF)
    return Sort(setOf(Sort.SortColumn(sortAttribute, Sort.Direction.ASC)))
}

// TODO: Merge this code with the code above.
private fun <T : TokenType> ownedTokenCriteria(embeddableToken: T): QueryCriteria {
    val tokenClass = builder {
        PersistentNonFungibleToken::tokenClass.equal(embeddableToken.tokenClass)
    }
    val tokenClassCriteria = QueryCriteria.VaultCustomQueryCriteria(tokenClass)
    val tokenIdentifier = builder {
        PersistentNonFungibleToken::tokenIdentifier.equal(embeddableToken.tokenIdentifier)
    }
    val tokenIdentifierCriteria = QueryCriteria.VaultCustomQueryCriteria(tokenIdentifier)
    return tokenClassCriteria.and(tokenIdentifierCriteria)
}

// For summing tokens of a specified type.
// NOTE: Issuer is ignored with this query criteria.
// NOTE: It only returns relevant states.
private fun <T : TokenType> sumTokenCriteria(embeddableToken: T): QueryCriteria {
    val sum = builder {
        val groups = listOf(PersistentFungibleToken::tokenClass, PersistentFungibleToken::tokenIdentifier)
        PersistentFungibleToken::amount.sum(groupByColumns = groups)
    }
    return QueryCriteria.VaultCustomQueryCriteria(sum, relevancyStatus = Vault.RelevancyStatus.RELEVANT)
}

// Abstracts away the nasty 'otherResults' part of the vault query API.
private fun <T : TokenType> rowsToAmount(embeddableToken: T, rows: Vault.Page<FungibleToken<T>>): Amount<T> {
    return if (rows.otherResults.isEmpty()) {
        Amount(0L, embeddableToken)
    } else {
        require(rows.otherResults.size == 3) { "Invalid number of rows returned by query." }
        // The class and identifier are also returned in indexes 1 and 2 but we can discard them.
        val quantity = rows.otherResults[0] as Long
        Amount(quantity, embeddableToken)
    }
}

/** General queries. */

// Get all owned token amounts for a specific token, ignoring the issuer.
fun <T : TokenType> VaultService.ownedTokenAmountsByToken(embeddableToken: T): Vault.Page<FungibleToken<T>> {
    return queryBy(ownedTokenAmountCriteria(embeddableToken))
}

// Get all owned tokens for a specific token, ignoring the issuer.
fun <T : TokenType> VaultService.ownedTokensByToken(embeddableToken: T): Vault.Page<NonFungibleToken<T>> {
    return queryBy(ownedTokenCriteria(embeddableToken))
}

/** TokenType balances. */

// We need to group the sum by the token class and token identifier.
fun <T : TokenType> VaultService.tokenBalance(embeddableToken: T): Amount<T> {
    val query = ownedTokenAmountCriteria(embeddableToken).and(sumTokenCriteria(embeddableToken))
    val result = queryBy<FungibleToken<T>>(query)
    return rowsToAmount(embeddableToken, result)
}
