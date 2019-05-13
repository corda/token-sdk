package com.r3.corda.sdk.token.contracts.utilities

import com.r3.corda.sdk.token.contracts.states.AbstractToken
import com.r3.corda.sdk.token.contracts.states.EvolvableTokenType
import com.r3.corda.sdk.token.contracts.states.FungibleToken
import com.r3.corda.sdk.token.contracts.states.NonFungibleToken
import com.r3.corda.sdk.token.contracts.types.IssuedTokenType
import com.r3.corda.sdk.token.contracts.types.TokenType
import net.corda.core.contracts.Amount
import net.corda.core.contracts.TransactionState
import net.corda.core.crypto.toStringShort
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.AnonymousParty
import net.corda.core.identity.Party

// ------------------------------------------------------
// Creates a tokens from (amounts of) issued token types.
// ------------------------------------------------------

/**
 * Creates a [FungibleToken] from an an amount of [IssuedTokenType].
 * E.g. Amount<IssuedTokenType<TokenType>> -> FungibleToken<TokenType>.
 */
infix fun <T : TokenType> Amount<IssuedTokenType<T>>.heldBy(owner: AbstractParty): FungibleToken<T> = _heldBy(owner)

internal infix fun <T : TokenType> Amount<IssuedTokenType<T>>._heldBy(owner: AbstractParty): FungibleToken<T> {
    return FungibleToken(this, owner)
}

/**
 * Creates a [NonFungibleToken] from an [IssuedTokenType].
 * E.g. IssuedTokenType<TokenType> -> NonFungibleToken<TokenType>.
 */
infix fun <T : TokenType> IssuedTokenType<T>.heldBy(owner: AbstractParty): NonFungibleToken<T> = _heldBy(owner)

internal infix fun <T : TokenType> IssuedTokenType<T>._heldBy(owner: AbstractParty): NonFungibleToken<T> {
    return NonFungibleToken(this, owner)
}

// --------------------------
// Add a a notary to a token.
// --------------------------

/** Adds a notary [Party] to an [AbstractToken], by wrapping it in a [TransactionState]. */
infix fun <T : AbstractToken<*>> T.withNotary(notary: Party): TransactionState<T> = _withNotary(notary)

internal infix fun <T : AbstractToken<*>> T._withNotary(notary: Party): TransactionState<T> {
    return TransactionState(data = this, notary = notary)
}

/** Adds a notary [Party] to an [EvolvableTokenType], by wrapping it in a [TransactionState]. */
infix fun <T : EvolvableTokenType> T.withNotary(notary: Party): TransactionState<T> = _withNotary(notary)

internal infix fun <T : EvolvableTokenType> T._withNotary(notary: Party): TransactionState<T> {
    return TransactionState(data = this, notary = notary)
}

/**
 * Converts [holder] into a more friendly string. It uses only the x500 organisation for [Party] objects and
 * shortens the public key for [AnonymousParty]s to the first 16 characters.
 * */
val AbstractToken<*>.holderString: String
    get() =
        (holder as? Party)?.name?.organisation ?: holder.owningKey.toStringShort().substring(0, 16)
