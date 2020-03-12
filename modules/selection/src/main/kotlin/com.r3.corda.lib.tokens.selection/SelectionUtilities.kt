package com.r3.corda.lib.tokens.selection

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.lib.tokens.contracts.internal.schemas.PersistentFungibleToken
import com.r3.corda.lib.tokens.contracts.types.TokenType
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.node.ServiceHub
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.node.services.vault.Sort
import net.corda.core.node.services.vault.SortAttribute
import net.corda.core.node.services.vault.builder
import net.corda.core.transactions.TransactionBuilder

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
 * This is a simple wrapping class for convenient access to the classless utility functions in this file from Java code.
 * This class is only syntactic sugar for Java developers and does not change or modify the functionality of any utility.
 */
class SelectionUtilities {
    companion object {

        /**
         * A static method to pipe the classless [tokenAmountWithIssuerCriteria] function.
         *
         * @param token A [TokenType] representing the type of token we wish to query for
         * @param issuer All returned tokens will have this [Party] as the issuer
         */
        @JvmStatic
        @JvmOverloads
        fun tokenAmountWithIssuerCriteria(token: TokenType, issuer: Party) =
                com.r3.corda.lib.tokens.selection.tokenAmountWithIssuerCriteria(token, issuer)

        /**
         * A static method to pipe the classless [tokenAmountCriteria] function.
         *
         * @param token A [TokenType] representing the type of token we wish to query for
         */
        @JvmStatic
        @JvmOverloads
        fun tokenAmountCriteria(token: TokenType) =
                com.r3.corda.lib.tokens.selection.tokenAmountCriteria(token)

        /**
         * A static method to pipe the classless [tokenAmountWithHolderCriteria] function.
         *
         * @param token A [TokenType] representing the type of token we wish to query for
         * @param holder All returned tokens will have this [Party] as the holder
         */
        @JvmStatic
        @JvmOverloads
        fun tokenAmountWithHolderCriteria(token: TokenType, holder: AbstractParty) =
                com.r3.corda.lib.tokens.selection.tokenAmountWithHolderCriteria(token, holder)
    }
}

class InsufficientBalanceException(message: String) : RuntimeException(message)
