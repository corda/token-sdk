package com.r3.corda.lib.tokens.contracts.utilities

import com.r3.corda.lib.tokens.contracts.states.FungibleToken
import com.r3.corda.lib.tokens.contracts.types.IssuedTokenType
import com.r3.corda.lib.tokens.contracts.types.TokenPointer
import com.r3.corda.lib.tokens.contracts.types.TokenType
import net.corda.core.contracts.Amount
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.StateAndRef
import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.sha256
import net.corda.core.identity.Party
import net.corda.core.internal.location
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
fun Iterable<FungibleToken>.sumTokenStatesOrThrow(): Amount<IssuedTokenType> {
	return map { it.amount }.sumTokensOrThrow()
}

/** Sums the held token amounts states in the list, returning null if there are none. */
fun Iterable<FungibleToken>.sumTokenStatesOrNull(): Amount<IssuedTokenType>? {
	return map { it.amount }.sumIssuedTokensOrNull()
}

/** Sums the cash states in the list, returning zero of the given currency+issuer if there are none. */
fun Iterable<FungibleToken>.sumTokenStatesOrZero(
	token: IssuedTokenType
): Amount<IssuedTokenType> {
	return map { it.amount }.sumIssuedTokensOrZero(token)
}

/** Sums the token amounts in the list of state and refs. */
fun Iterable<StateAndRef<FungibleToken>>.sumTokenStateAndRefs(): Amount<IssuedTokenType> {
	return map { it.state.data.amount }.sumTokensOrThrow()
}

/** Sums the held token amount state and refs in the list, returning null if there are none. */
fun Iterable<StateAndRef<FungibleToken>>.sumTokenStateAndRefsOrNull(): Amount<IssuedTokenType>? {
	return map { it.state.data.amount }.sumIssuedTokensOrNull()
}

/**
 * Sums the held token amounts state and refs in the list, returning zero of the given currency+issuer if there are
 * none.
 */
fun Iterable<StateAndRef<FungibleToken>>.sumTokenStateAndRefsOrZero(
	token: IssuedTokenType
): Amount<IssuedTokenType> {
	return map { it.state.data.amount }.sumIssuedTokensOrZero(token)
}

/** Filters a list of tokens of the same type by issuer. */
fun Iterable<FungibleToken>.filterTokensByIssuer(issuer: Party): List<FungibleToken> {
	return filter { it.amount.token.issuer == issuer }
}

/** Filters a list of token state and refs with the same token type by issuer. */
fun Iterable<StateAndRef<FungibleToken>>.filterTokenStateAndRefsByIssuer(
	issuer: Party
): List<StateAndRef<FungibleToken>> {
	return filter { it.state.data.amount.token.issuer == issuer }
}

// Utilities for ensuring that the JAR which implements the specified TokenType is added to the transaction.

internal val attachmentCache = HashMap<Class<*>, SecureHash>()
internal val NULL_SECURE_HASH = SecureHash.zeroHash

/**
 * If the [TokenType] is not a [TokenPointer] this function discovers the JAR which implements the receiving [TokenType].
 */
fun TokenType.getAttachmentIdForGenericParam(): SecureHash? {
	val computedValue = synchronized(attachmentCache) {
		val startingPoint = if (this is IssuedTokenType) {
			this.tokenType.javaClass
		} else {
			this.javaClass
		}
		attachmentCache.computeIfAbsent(startingPoint) { clazz ->
			var classToSearch: Class<*> = clazz
			while (classToSearch != this.tokenClass && classToSearch != TokenPointer::class.java) {
				classToSearch = this.tokenClass
			}
			if (classToSearch.location == TokenType::class.java.location) {
				TokenUtilities.logger.debug("${this.javaClass} is provided by tokens-sdk")
				NULL_SECURE_HASH
			} else {
				val hash = classToSearch.location.readBytes().sha256()
				TokenUtilities.logger.debug(
					"looking for jar which provides: $classToSearch FOUND AT: " +
							"${classToSearch.location.path} with hash $hash"
				)
				hash
			}
		}
	}
	return if (computedValue == NULL_SECURE_HASH) {
		null
	} else {
		computedValue
	}
}
