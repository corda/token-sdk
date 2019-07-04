@file:JvmName("TokenUtilities")

package com.r3.corda.lib.tokens.workflows.utilities

import com.r3.corda.lib.tokens.contracts.states.NonFungibleToken
import com.r3.corda.lib.tokens.contracts.types.IssuedTokenType
import com.r3.corda.lib.tokens.contracts.types.TokenType
import net.corda.core.identity.AbstractParty

/**
 * Creates a [NonFungibleToken] from an [IssuedTokenType].
 * E.g. IssuedTokenType<TokenType> -> NonFungibleToken.
 * This function must exist outside of the contracts module as creating a unique identifier is non deterministic.
 */
infix fun <T : TokenType> IssuedTokenType<T>.heldBy(owner: AbstractParty): NonFungibleToken = _heldBy(owner)

private infix fun <T : TokenType> IssuedTokenType<T>._heldBy(owner: AbstractParty): NonFungibleToken {
    return NonFungibleToken(this, owner, UniqueIdentifier())
}
