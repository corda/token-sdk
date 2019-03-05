package com.r3.corda.sdk.token.contracts.utilities

import com.r3.corda.sdk.token.contracts.states.FungibleToken
import com.r3.corda.sdk.token.contracts.types.IssuedTokenType
import com.r3.corda.sdk.token.contracts.types.TokenType
import net.corda.core.contracts.Amount
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.StateAndRef
import net.corda.core.identity.Party
import net.corda.core.transactions.LedgerTransaction

// Transaction helpers.

/** Get single input/output from ledger transaction. */
inline fun <reified T : ContractState> LedgerTransaction.singleInput() = inputsOfType<T>().single()
inline fun <reified T : ContractState> LedgerTransaction.singleOutput() = outputsOfType<T>().single()

// State summing utilities.

/**
 * Sums the [IssuedTokenType] amounts in the list of [FungibleToken]s. Note that all tokens must have the same issuer
 * otherwise this function will throw an [IllegalArgumentException]. If issuers differ then filter the list before using
 * this function.
 */
fun <T : TokenType> Iterable<FungibleToken<T>>.sumTokenStatesOrThrow(): Amount<IssuedTokenType<T>> {
    return map { it.amount }.sumTokensOrThrow()
}

/** Sums the owned token amounts states in the list, returning null if there are none. */
fun <T : TokenType> Iterable<FungibleToken<T>>.sumTokenStatesOrNull(): Amount<IssuedTokenType<T>>? {
    return map { it.amount }.sumIssuedTokensOrNull()
}

/** Sums the cash states in the list, returning zero of the given currency+issuer if there are none. */
fun <T : TokenType> Iterable<FungibleToken<T>>.sumTokenStatesOrZero(
        token: IssuedTokenType<T>
): Amount<IssuedTokenType<T>> {
    return map { it.amount }.sumIssuedTokensOrZero(token)
}

/** Sums the token amounts in the list of state and refs. */
fun <T : TokenType> Iterable<StateAndRef<FungibleToken<T>>>.sumTokenStateAndRefs(): Amount<IssuedTokenType<T>> {
    return map { it.state.data.amount }.sumTokensOrThrow()
}

/** Sums the owned token amount state and refs in the list, returning null if there are none. */
fun <T : TokenType> Iterable<StateAndRef<FungibleToken<T>>>.sumTokenStateAndRefsOrNull(): Amount<IssuedTokenType<T>>? {
    return map { it.state.data.amount }.sumIssuedTokensOrNull()
}

/**
 * Sums the owned token amounts state and refs in the list, returning zero of the given currency+issuer if there are
 * none.
 */
fun <T : TokenType> Iterable<StateAndRef<FungibleToken<T>>>.sumTokenStateAndRefsOrZero(
        token: IssuedTokenType<T>
): Amount<IssuedTokenType<T>> {
    return map { it.state.data.amount }.sumIssuedTokensOrZero(token)
}

/** Filters a list of tokens of the same type by issuer. */
fun <T : TokenType> Iterable<FungibleToken<T>>.filterTokensByIssuer(issuer: Party): List<FungibleToken<T>> {
    return filter { it.amount.token.issuer == issuer }
}

/** Filters a list of token state and refs with the same token type by issuer. */
fun <T : TokenType> Iterable<StateAndRef<FungibleToken<T>>>.filterTokenStateAndRefsByIssuer(
        issuer: Party
): List<StateAndRef<FungibleToken<T>>> {
    return filter { it.state.data.amount.token.issuer == issuer }
}