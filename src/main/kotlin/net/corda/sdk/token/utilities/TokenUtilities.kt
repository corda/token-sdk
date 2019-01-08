package net.corda.sdk.token.utilities

import net.corda.core.contracts.Amount
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.TransactionState
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.internal.uncheckedCast
import net.corda.sdk.token.states.OwnedToken
import net.corda.sdk.token.states.OwnedTokenAmount
import net.corda.sdk.token.types.EmbeddableToken
import net.corda.sdk.token.types.Issued
import java.math.BigDecimal

/** Helpers. */

// For parsing amount quantities of embeddable tokens that are not wrapped with an issuer. Like so: 1_000.GBP.
fun <T : EmbeddableToken> AMOUNT(amount: Int, token: T): Amount<T> = AMOUNT(amount.toLong(), token)
fun <T : EmbeddableToken> AMOUNT(amount: Long, token: T): Amount<T> = Amount.fromDecimal(BigDecimal.valueOf(amount), token)
fun <T : EmbeddableToken> AMOUNT(amount: Double, token: T): Amount<T> = Amount.fromDecimal(BigDecimal.valueOf(amount), token)

// As above but works with embeddable tokens wrapped with an issuer.
fun <T : EmbeddableToken> AMOUNT(amount: Int, token: Issued<T>): Amount<Issued<T>> = AMOUNT(amount.toLong(), token)
fun <T : EmbeddableToken> AMOUNT(amount: Long, token: Issued<T>): Amount<Issued<T>> = Amount.fromDecimal(BigDecimal.valueOf(amount), token)
fun <T : EmbeddableToken> AMOUNT(amount: Double, token: Issued<T>): Amount<Issued<T>> = Amount.fromDecimal(BigDecimal.valueOf(amount), token)

// For parsing amounts of embeddable tokens that are not wrapped with an issuer. Like so: 1_000 of token.
infix fun <T : EmbeddableToken> Int.of(token: T) = AMOUNT(this, token)
infix fun <T : EmbeddableToken> Long.of(token: T) = AMOUNT(this, token)
infix fun <T : EmbeddableToken> Double.of(token: T) = AMOUNT(this, token)

// As above but for tokens which are wrapped with an issuer.
infix fun <T : Issued<U>, U : EmbeddableToken> Int.of(token: T) = AMOUNT(this, token)

infix fun <T : Issued<U>, U : EmbeddableToken> Long.of(token: T) = AMOUNT(this, token)
infix fun <T : Issued<U>, U : EmbeddableToken> Double.of(token: T) = AMOUNT(this, token)

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

infix fun <T : ContractState> T._withNotary(notary: Party): TransactionState<T> = TransactionState(data = this, notary = notary)