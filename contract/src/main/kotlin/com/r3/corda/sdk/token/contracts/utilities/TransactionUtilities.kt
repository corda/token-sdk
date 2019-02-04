package com.r3.corda.sdk.token.contracts.utilities

import com.r3.corda.sdk.token.contracts.states.OwnedTokenAmount
import com.r3.corda.sdk.token.contracts.types.EmbeddableToken
import com.r3.corda.sdk.token.contracts.types.Issued
import net.corda.core.contracts.Amount
import net.corda.core.contracts.Amount.Companion.sumOrNull
import net.corda.core.contracts.Amount.Companion.sumOrThrow
import net.corda.core.contracts.Amount.Companion.sumOrZero
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.StateAndRef
import net.corda.core.identity.Party
import net.corda.core.transactions.LedgerTransaction

/** Get single input/output from ledger transaction. */
inline fun <reified T : ContractState> LedgerTransaction.singleInput() = inputsOfType<T>().single()

inline fun <reified T : ContractState> LedgerTransaction.singleOutput() = outputsOfType<T>().single()

/**
 * Sums the issued token amounts in the list of owned token amounts. Note that all tokens must have the same issuer.
 * If not then filter by issuer before using this function.
 */
inline fun <reified T : EmbeddableToken> Iterable<OwnedTokenAmount<T>>.sumTokens(): Amount<Issued<T>> {
    return map { it.amount }.sumOrThrow()
}

/** Sums the owned token amounts states in the list, returning null if there are none. */
inline fun <reified T : EmbeddableToken> Iterable<OwnedTokenAmount<T>>.sumTokensOrNull(): Amount<Issued<T>>? {
    return map { it.amount }.sumOrNull()
}

/** Sums the cash states in the list, returning zero of the given currency+issuer if there are none. */
inline fun <reified T : EmbeddableToken> Iterable<OwnedTokenAmount<T>>.sumTokensOrZero(token: Issued<T>): Amount<Issued<T>> {
    return map { it.amount }.sumOrZero(token)
}

/** Sums the token amounts in the list of state and refs. */
inline fun <reified T : EmbeddableToken> Iterable<StateAndRef<OwnedTokenAmount<T>>>.sumTokenStateAndRefs(): Amount<Issued<T>> {
    return map { it.state.data.amount }.sumOrThrow()
}

/** Sums the owned token amount state and refs in the list, returning null if there are none. */
inline fun <reified T : EmbeddableToken> Iterable<StateAndRef<OwnedTokenAmount<T>>>.sumTokenStateAndRefsOrNull(): Amount<Issued<T>>? {
    return map { it.state.data.amount }.sumOrNull()
}

/** Sums the owned token amounts state and refs in the list, returning zero of the given currency+issuer if there are none. */
inline fun <reified T : EmbeddableToken> Iterable<StateAndRef<OwnedTokenAmount<T>>>.sumTokenStateAndRefsOrZero(token: Issued<T>): Amount<Issued<T>> {
    return map { it.state.data.amount }.sumOrZero(token)
}

/** Filters a list of tokens of the same type by issuer. */
inline fun <reified T : EmbeddableToken> Iterable<OwnedTokenAmount<T>>.filterTokensByIssuer(issuer: Party): List<OwnedTokenAmount<T>> {
    return filter { it.amount.token.issuer == issuer }
}

inline fun <reified T : EmbeddableToken> Iterable<StateAndRef<OwnedTokenAmount<T>>>.filterTokenStateAndRefsByIssuer(issuer: Party): List<StateAndRef<OwnedTokenAmount<T>>> {
    return filter { it.state.data.amount.token.issuer == issuer }
}