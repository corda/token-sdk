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
import net.corda.core.identity.Party
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

// TODO Revisit this API and add documentation.
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

// Gets the distribution record for a particular token and party.
fun getDistributionRecord(serviceHub: ServiceHub, linearId: UniqueIdentifier, party: Party): List<DistributionRecord> {
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

/** Utilities for getting tokens from the vault and performing miscellaneous queries. */

// TODO: Add queries for getting the balance of all tokens, not just relevant ones.
// TODO: Allow discrimination by issuer or a set of issuers.

// Returns all owned token amounts of a specified token with given issuer.
// We need to discriminate on the token type as well as the symbol as different tokens might use the same symbols.
fun <T: TokenType> tokenAmountWithIssuerCriteria(token: T, issuer: Party): QueryCriteria {
    val issuerCriteria = QueryCriteria.VaultCustomQueryCriteria(builder {
        PersistentFungibleToken::issuer.equal(issuer)
    })
    return ownedTokenAmountCriteria(token).and(issuerCriteria)
}

// Returns all owned token amounts of a specified token.
// We need to discriminate on the token type as well as the symbol as different tokens might use the same symbols.
// TODO should be called token amount criteria (there is no owner selection)
fun <T : TokenType> ownedTokenAmountCriteria(token: T): QueryCriteria {
    val tokenClass = builder {
        PersistentFungibleToken::tokenClass.equal(token.tokenClass)
    }
    val tokenClassCriteria = QueryCriteria.VaultCustomQueryCriteria(tokenClass)
    val tokenIdentifier = builder {
        PersistentFungibleToken::tokenIdentifier.equal(token.tokenIdentifier)
    }
    val tokenIdentifierCriteria = QueryCriteria.VaultCustomQueryCriteria(tokenIdentifier)
    return tokenClassCriteria.and(tokenIdentifierCriteria)
}

// Sorts a query by state ref ascending.
fun sortByStateRefAscending(): Sort {
    val sortAttribute = SortAttribute.Standard(Sort.CommonStateAttribute.STATE_REF)
    return Sort(setOf(Sort.SortColumn(sortAttribute, Sort.Direction.ASC)))
}

// TODO: Merge this code with the code above.
fun <T : TokenType> ownedTokenCriteria(token: T): QueryCriteria {
    val tokenClass = builder {
        PersistentNonFungibleToken::tokenClass.equal(token.tokenClass)
    }
    val tokenClassCriteria = QueryCriteria.VaultCustomQueryCriteria(tokenClass)
    val tokenIdentifier = builder {
        PersistentNonFungibleToken::tokenIdentifier.equal(token.tokenIdentifier)
    }
    val tokenIdentifierCriteria = QueryCriteria.VaultCustomQueryCriteria(tokenIdentifier)
    return tokenClassCriteria.and(tokenIdentifierCriteria)
}

// For summing tokens of a specified type.
// NOTE: Issuer is ignored with this query criteria.
// NOTE: It only returns relevant states.
fun sumTokenCriteria(): QueryCriteria {
    val sum = builder {
        val groups = listOf(PersistentFungibleToken::tokenClass, PersistentFungibleToken::tokenIdentifier)
        PersistentFungibleToken::amount.sum(groupByColumns = groups)
    }
    return QueryCriteria.VaultCustomQueryCriteria(sum, relevancyStatus = Vault.RelevancyStatus.RELEVANT)
}

// Abstracts away the nasty 'otherResults' part of the vault query API.
fun <T : TokenType> rowsToAmount(token: T, rows: Vault.Page<FungibleToken<T>>): Amount<T> {
    return if (rows.otherResults.isEmpty()) {
        Amount(0L, token)
    } else {
        require(rows.otherResults.size == 3) { "Invalid number of rows returned by query." }
        // The class and identifier are also returned in indexes 1 and 2 but we can discard them.
        val quantity = rows.otherResults[0] as Long
        Amount(quantity, token)
    }
}

/** General queries. */

// Get all owned token amounts for a specific token, ignoring the issuer.
fun <T : TokenType> VaultService.ownedTokenAmountsByToken(token: T): Vault.Page<FungibleToken<T>> {
    return queryBy(ownedTokenAmountCriteria(token))
}

// Get all owned tokens for a specific token, ignoring the issuer.
fun <T : TokenType> VaultService.ownedTokensByToken(token: T): Vault.Page<NonFungibleToken<T>> {
    return queryBy(ownedTokenCriteria(token))
}

/** TokenType balances. */

// We need to group the sum by the token class and token identifier.
fun <T : TokenType> VaultService.tokenBalance(token: T): Amount<T> {
    val query = ownedTokenAmountCriteria(token).and(sumTokenCriteria())
    val result = queryBy<FungibleToken<T>>(query)
    return rowsToAmount(token, result)
}

// We need to group the sum by the token class and token identifier takes issuer into consideration.
fun <T : TokenType> VaultService.tokenBalanceForIssuer(token: T, issuer: Party): Amount<T> {
    val query = tokenAmountWithIssuerCriteria(token, issuer).and(sumTokenCriteria())
    val result = queryBy<FungibleToken<T>>(query)
    return rowsToAmount(token, result)
}

// TODO Add function to return balances grouped by issuers?

/* Queries with criteria. Eg. with issuer etc. */

// Get NonFungibleToken with issuer.
fun <T : TokenType> VaultService.ownedTokensByTokenIssuer(token: T, issuer: Party): Vault.Page<NonFungibleToken<T>> {
    val issuerCriteria = QueryCriteria.VaultCustomQueryCriteria(builder {
        PersistentNonFungibleToken::issuer.equal(issuer)
    })
    return queryBy(ownedTokenCriteria(token).and(issuerCriteria))
}
