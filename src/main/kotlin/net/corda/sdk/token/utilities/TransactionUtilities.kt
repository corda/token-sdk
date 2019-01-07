package net.corda.sdk.token.utilities

import net.corda.core.contracts.Amount
import net.corda.core.contracts.Amount.Companion.sumOrNull
import net.corda.core.contracts.Amount.Companion.sumOrThrow
import net.corda.core.contracts.Amount.Companion.sumOrZero
import net.corda.core.contracts.ContractState
import net.corda.core.transactions.LedgerTransaction
import net.corda.sdk.token.states.OwnedTokenAmount
import net.corda.sdk.token.types.EmbeddableToken
import net.corda.sdk.token.types.Issued

/** Get single input/output from ledger transaction. */
inline fun <reified T : ContractState> LedgerTransaction.singleInput() = inputsOfType<T>().single()

inline fun <reified T : ContractState> LedgerTransaction.singleOutput() = outputsOfType<T>().single()

/** Sums the token amounts in the list. */
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
