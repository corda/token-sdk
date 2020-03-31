package com.r3.corda.lib.tokens.contracts.types

import com.r3.corda.lib.tokens.contracts.states.EvolvableTokenType
import com.r3.corda.lib.tokens.contracts.states.FungibleToken
import com.r3.corda.lib.tokens.contracts.states.NonFungibleToken
import net.corda.core.contracts.LinearPointer

/**
 * To harness the power of [EvolvableTokenType]s, they cannot be directly embedded in [NonFungibleToken]s or
 * [FungibleToken]s. Instead, a [TokenPointer] is embedded. The pointer can be resolved inside the verify function to
 * obtain the data within the [EvolvableTokenType]. This way, the token token type can evolve independently from the
 * question of which [Party] owns some issued amount of it, as the data is held in a separate state object.
 *
 * @property pointer a [LinearPointer] which points to an [EvolvableTokenType].
 * @property displayTokenSize required for adding or subtracting [Amount]s of [TokenPointer] (which are acting as a
 * proxy for an [EvolvableTokenType]).
 * @param T the type of [EvolvableTokenType] which is being pointed to by this [TokenPointer].
 */
class TokenPointer<T : EvolvableTokenType>(
	val pointer: LinearPointer<T>,
	fractionDigits: Int
) : TokenType(pointer.pointer.id.toString(), fractionDigits) {
	/**
	 * The fully qualified class name for the [EvolvableTokenType] being pointed to.
	 */
	override val tokenClass: Class<*> get() = pointer.type

	override fun toString(): String = "TokenPointer($tokenClass, $tokenIdentifier)"
}










