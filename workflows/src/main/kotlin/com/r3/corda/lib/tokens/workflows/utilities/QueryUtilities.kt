package com.r3.corda.lib.tokens.workflows.utilities

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.lib.tokens.contracts.internal.schemas.PersistentFungibleToken
import com.r3.corda.lib.tokens.contracts.internal.schemas.PersistentNonFungibleToken
import com.r3.corda.lib.tokens.contracts.states.FungibleToken
import com.r3.corda.lib.tokens.contracts.states.NonFungibleToken
import com.r3.corda.lib.tokens.contracts.types.TokenType
import net.corda.core.contracts.Amount
import net.corda.core.contracts.LinearState
import net.corda.core.contracts.StateAndRef
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.node.services.Vault
import net.corda.core.node.services.VaultService
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.node.services.vault.Sort
import net.corda.core.node.services.vault.SortAttribute
import net.corda.core.node.services.vault.builder

// TODO Revisit this API and add documentation.
/** Miscellaneous helpers. */

// Grabs the latest version of a linear state for a specified linear ID.
inline fun <reified T : LinearState> VaultService.getLinearStateById(linearId: UniqueIdentifier): StateAndRef<T>? {
    val query = QueryCriteria.LinearStateQueryCriteria(
            linearId = listOf(linearId),
            status = Vault.StateStatus.UNCONSUMED,
            relevancyStatus = Vault.RelevancyStatus.ALL
    )
    return queryBy<T>(query).states.singleOrNull()
}

/** Utilities for getting tokens from the vault and performing miscellaneous queries. */

// TODO: Add queries for getting the balance of all tokens, not just relevant ones.
// TODO: Allow discrimination by issuer or a set of issuers.

// Returns all held token amounts of a specified token with given issuer.
// We need to discriminate on the token type as well as the symbol as different tokens might use the same symbols.
@Suspendable
fun <T : TokenType> tokenAmountWithIssuerCriteria(token: T, issuer: Party): QueryCriteria {
    val issuerCriteria = QueryCriteria.VaultCustomQueryCriteria(builder {
        PersistentFungibleToken::issuer.equal(issuer)
    })
    return tokenAmountCriteria(token).and(issuerCriteria)
}

fun <T : TokenType> heldTokenAmountCriteria(token: T, holder: AbstractParty): QueryCriteria {
    val holderCriteria = QueryCriteria.VaultCustomQueryCriteria(builder {
        PersistentFungibleToken::holder.equal(holder)
    })
    return tokenAmountCriteria(token).and(holderCriteria)
}

// Returns all held token amounts of a specified token.
// We need to discriminate on the token type as well as the symbol as different tokens might use the same symbols.
// TODO should be called token amount criteria (there is no owner selection)
@Suspendable
fun <T : TokenType> tokenAmountCriteria(token: T): QueryCriteria {
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
fun <T : TokenType> heldTokenCriteria(token: T): QueryCriteria {
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

// Get all held token amounts for a specific token, ignoring the issuer.
fun <T : TokenType> VaultService.heldAmountsByToken(token: T): Vault.Page<FungibleToken<T>> {
    return queryBy(tokenAmountCriteria(token))
}

// Get all held tokens for a specific token, ignoring the issuer.
fun <T : TokenType> VaultService.heldTokensByToken(token: T): Vault.Page<NonFungibleToken<T>> {
    return queryBy(heldTokenCriteria(token))
}

/** TokenType balances. */

// We need to group the sum by the token class and token identifier.
fun <T : TokenType> VaultService.tokenBalance(token: T): Amount<T> {
    val query = tokenAmountCriteria(token).and(sumTokenCriteria())
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
fun <T : TokenType> VaultService.heldTokensByTokenIssuer(token: T, issuer: Party): Vault.Page<NonFungibleToken<T>> {
    val issuerCriteria = QueryCriteria.VaultCustomQueryCriteria(builder {
        PersistentNonFungibleToken::issuer.equal(issuer)
    })
    return queryBy(heldTokenCriteria(token).and(issuerCriteria))
}
