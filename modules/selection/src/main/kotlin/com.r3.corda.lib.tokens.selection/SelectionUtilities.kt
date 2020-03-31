@file:JvmName("SelectionUtilities")
package com.r3.corda.lib.tokens.selection

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.lib.tokens.contracts.internal.schemas.PersistentFungibleToken
import com.r3.corda.lib.tokens.contracts.types.TokenType
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.node.services.vault.Sort
import net.corda.core.node.services.vault.SortAttribute
import net.corda.core.node.services.vault.builder

// TODO clean up the module structure of token-sdk, because these function and types (eg PartyAndAmount) should be separate from workflows
// Sorts a query by state ref ascending.
internal fun sortByStateRefAscending(): Sort {
    val sortAttribute = SortAttribute.Standard(Sort.CommonStateAttribute.STATE_REF)
    return Sort(setOf(Sort.SortColumn(sortAttribute, Sort.Direction.ASC)))
}

// Returns all held token amounts of a specified token with given issuer.
// We need to discriminate on the token type as well as the symbol as different tokens might use the same symbols.
@Suspendable
fun tokenAmountWithIssuerCriteria(token: TokenType, issuer: Party): QueryCriteria {
    val issuerCriteria = QueryCriteria.VaultCustomQueryCriteria(builder {
        PersistentFungibleToken::issuer.equal(issuer)
    })
    return tokenAmountCriteria(token).and(issuerCriteria)
}

// Returns all held token amounts of a specified token.
// We need to discriminate on the token type as well as the symbol as different tokens might use the same symbols.
@Suspendable
fun tokenAmountCriteria(token: TokenType): QueryCriteria {
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

@Suspendable
fun tokenAmountWithHolderCriteria(token: TokenType, holder: AbstractParty): QueryCriteria {
    val issuerCriteria = QueryCriteria.VaultCustomQueryCriteria(builder {
        PersistentFungibleToken::holder.equal(holder)
    })
    return tokenAmountCriteria(token).and(issuerCriteria)
}

/**
 * An exception that is thrown where the specified criteria returns an amount of tokens
 * that is not sufficient for the specified spend.
 *
 * @param message The exception message that should be thrown in this context
 */
class InsufficientBalanceException(message: String) : RuntimeException(message)
