package com.r3.corda.sdk.token.contracts.utilities

import com.r3.corda.sdk.token.contracts.states.OwnedToken
import com.r3.corda.sdk.token.contracts.states.OwnedTokenAmount
import com.r3.corda.sdk.token.contracts.types.EmbeddableToken
import com.r3.corda.sdk.token.contracts.types.Issued
import net.corda.core.contracts.Amount
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.TransactionState
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.internal.uncheckedCast
import java.math.BigDecimal

/** Helpers for composing tokens with issuers, owners and amounts. */

// For parsing amount quantities of embeddable tokens that are not wrapped with an issuer. Like so: 1_000.GBP.
fun <T : EmbeddableToken> AMOUNT(amount: Int, token: T): Amount<T> = AMOUNT(amount.toLong(), token)

fun <T : EmbeddableToken> AMOUNT(amount: Long, token: T): Amount<T> = Amount.fromDecimal(BigDecimal.valueOf(amount), token)
fun <T : EmbeddableToken> AMOUNT(amount: Double, token: T): Amount<T> = Amount.fromDecimal(BigDecimal.valueOf(amount), token)
fun <T : EmbeddableToken> AMOUNT(amount: BigDecimal, token: T): Amount<T> = Amount.fromDecimal(amount, token)

// As above but works with embeddable tokens wrapped with an issuer.
fun <T : EmbeddableToken> AMOUNT(amount: Int, token: Issued<T>): Amount<Issued<T>> = AMOUNT(amount.toLong(), token)

fun <T : EmbeddableToken> AMOUNT(amount: Long, token: Issued<T>): Amount<Issued<T>> = Amount.fromDecimal(BigDecimal.valueOf(amount), token)
fun <T : EmbeddableToken> AMOUNT(amount: Double, token: Issued<T>): Amount<Issued<T>> = Amount.fromDecimal(BigDecimal.valueOf(amount), token)
fun <T : EmbeddableToken> AMOUNT(amount: BigDecimal, token: Issued<T>): Amount<Issued<T>> = Amount.fromDecimal(amount, token)

// For parsing amounts of embeddable tokens that are not wrapped with an issuer. Like so: 1_000 of token.
infix fun <T : EmbeddableToken> Int.of(token: T) = AMOUNT(this, token)

infix fun <T : EmbeddableToken> Long.of(token: T) = AMOUNT(this, token)
infix fun <T : EmbeddableToken> Double.of(token: T) = AMOUNT(this, token)
infix fun <T : EmbeddableToken> BigDecimal.of(token: T) = AMOUNT(this, token)

// As above but for tokens which are wrapped with an issuer. Like so: 1_000 of issuedToken.
infix fun <T : Issued<U>, U : EmbeddableToken> Int.of(token: T) = AMOUNT(this, token)

infix fun <T : Issued<U>, U : EmbeddableToken> Long.of(token: T) = AMOUNT(this, token)
infix fun <T : Issued<U>, U : EmbeddableToken> Double.of(token: T) = AMOUNT(this, token)
infix fun <T : Issued<U>, U : EmbeddableToken> BigDecimal.of(token: T) = AMOUNT(this, token)

// For wrapping amounts of a fixed token definition with an issuer: Amount<Token> -> Amount<Issued<Token>>.
// Note: these helpers are not compatible with EvolvableDefinitions, only EmbeddableDefinitions.
infix fun <T : EmbeddableToken> Amount<T>.issuedBy(issuer: Party): Amount<Issued<T>> = _issuedBy(issuer)

infix fun <T : EmbeddableToken> T._issuedBy(issuer: Party) = Issued(issuer, this)
infix fun <T : EmbeddableToken> Amount<T>._issuedBy(issuer: Party): Amount<Issued<T>> {
    return Amount(quantity, displayTokenSize, uncheckedCast(token.issuedBy(issuer)))
}

// For wrapping Tokens with an issuer: Token -> Issued<Token>
infix fun <T : EmbeddableToken> T.issuedBy(issuer: Party) = _issuedBy(issuer)

// For adding ownership information to a Token. Wraps an amount of some Issued Token with an OwnedTokenAmount state.
infix fun <T : EmbeddableToken> Amount<Issued<T>>.ownedBy(owner: AbstractParty) = _ownedBy(owner)

infix fun <T : EmbeddableToken> Amount<Issued<T>>._ownedBy(owner: AbstractParty) = OwnedTokenAmount(this, owner)

// As above but wraps the token with an OwnedToken state.
infix fun <T : EmbeddableToken> Issued<T>.ownedBy(owner: AbstractParty) = _ownedBy(owner)

infix fun <T : EmbeddableToken> Issued<T>._ownedBy(owner: AbstractParty) = OwnedToken(this, owner)

// Add a notary to an evolvable token.
infix fun <T : ContractState> T.withNotary(notary: Party): TransactionState<T> = _withNotary(notary)

infix fun <T : ContractState> T._withNotary(notary: Party): TransactionState<T> {
    return TransactionState(data = this, notary = notary)
}

/** Helpers for summing [Amount]s of tokens. */

// If the given iterable of [Amount]s yields any elements, sum them, throwing an [IllegalArgumentException] if
// any of the token types are mismatched; if the iterator yields no elements, return null.
fun <T : EmbeddableToken> Iterable<Amount<T>>.sumOrNull() = if (!iterator().hasNext()) null else sumOrThrow()

// Sums the amounts yielded by the given iterable, throwing an [IllegalArgumentException] if any of the token
// types are mismatched.
fun <T : EmbeddableToken> Iterable<Amount<T>>.sumOrThrow() = reduce { left, right -> left + right }

// If the given iterable of [Amount]s yields any elements, sum them, throwing an [IllegalArgumentException] if
// any of the token types are mismatched; if the iterator yields no elements, return a zero amount of the given
// token type.
fun <T : EmbeddableToken> Iterable<Amount<T>>.sumOrZero(token: T): Amount<T> {
    return if (iterator().hasNext()) sumOrThrow() else Amount.zero(token)
}

/**
 * Strips the issuer and returns an [Amount] of the raw token directly. This is useful when you are mixing code that
 * cares about specific issuers with code that will accept any, or which is imposing issuer constraints via some
 * other mechanism and the additional type safety is not wanted.
 */
fun <T : EmbeddableToken> Amount<Issued<T>>.withoutIssuer(): Amount<T> {
    return Amount(quantity, displayTokenSize, token.product)
}