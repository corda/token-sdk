package com.r3.corda.lib.tokens.contracts.commands

import com.r3.corda.lib.tokens.contracts.AbstractTokenContract
import com.r3.corda.lib.tokens.contracts.states.FungibleToken
import com.r3.corda.lib.tokens.contracts.states.NonFungibleToken
import com.r3.corda.lib.tokens.contracts.types.IssuedTokenType
import com.r3.corda.lib.tokens.contracts.types.TokenType
import net.corda.core.contracts.Command
import net.corda.core.contracts.CommandData
import java.util.*

/**
 * Represents the base class for implementing token commands.
 *
 * A [TokenCommand] represents groups of input and/or output indexes of the specified [IssuedTokenType]. This needs to be done because if a transaction
 * contains more than one type of token, we need to handle inputs and outputs grouped by token type. Furthermore, we need to distinguish between the same
 * token issued by two different issuers as the same token issued by different issuers is not fungible, so one cannot add or subtract them.
 * This is why [IssuedTokenType] is used. The [IssuedTokenType] is also included in the [TokenType] so each command can be linked to a group.
 * The [AbstractTokenContract] doesn't allow a group of tokens without an associated [Command].
 *
 * @property token The issued token type associated with the current token command.
 * @property inputIndexes The indexes of the inputs associated with the current token command.
 * @property outputIndexes The indexes of the outputs associated with the current token command.
 */
abstract class TokenCommand(val token: IssuedTokenType, internal val inputIndexes: List<Int>, internal val outputIndexes: List<Int>) : CommandData {

	override fun equals(other: Any?): Boolean {
		return this === other
				|| other is TokenCommand
				&& other.javaClass == javaClass
				&& other.token == token
				&& other.inputIndexes == inputIndexes
				&& other.outputIndexes == outputIndexes
	}

	override fun hashCode(): Int {
		return Objects.hash(javaClass, token, inputIndexes, outputIndexes)
	}

	override fun toString(): String {
		return "${javaClass.name}(token=$token, inputIndexes=$inputIndexes, outputIndexes=$outputIndexes)"
	}
}

/**
 * Represents the token command used to issue instances of [FungibleToken] or [NonFungibleToken].
 *
 * @param token The issued token type associated with the current token command.
 * @param outputIndexes The indexes of the outputs associated with the current token command.
 */
class IssueTokenCommand(
	token: IssuedTokenType,
	outputIndexes: List<Int> = emptyList()
) : TokenCommand(token, emptyList(), outputIndexes.sorted())

/**
 * Represents the token command used to move instances of [FungibleToken] or [NonFungibleToken].
 *
 * @param token The issued token type associated with the current token command.
 * @param inputIndexes The indexes of the inputs associated with the current token command.
 * @param outputIndexes The indexes of the outputs associated with the current token command.
 */
class MoveTokenCommand(
	token: IssuedTokenType,
	inputIndexes: List<Int> = emptyList(),
	outputIndexes: List<Int> = emptyList()
) : TokenCommand(token, inputIndexes.sorted(), outputIndexes.sorted())

/**
 * Represents the token command used to redeem instances of [FungibleToken] or [NonFungibleToken].
 *
 * @param token The issued token type associated with the current token command.
 * @param inputIndexes The indexes of the inputs associated with the current token command.
 * @param outputIndexes The indexes of the outputs associated with the current token command.
 */
class RedeemTokenCommand(
	token: IssuedTokenType,
	inputIndexes: List<Int> = emptyList(),
	outputIndexes: List<Int> = emptyList()
) : TokenCommand(token, inputIndexes.sorted(), outputIndexes.sorted())
