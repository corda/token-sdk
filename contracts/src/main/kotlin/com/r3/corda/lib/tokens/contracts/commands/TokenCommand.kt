package com.r3.corda.lib.tokens.contracts.commands

import com.r3.corda.lib.tokens.contracts.states.FungibleToken
import com.r3.corda.lib.tokens.contracts.states.NonFungibleToken
import com.r3.corda.lib.tokens.contracts.types.IssuedTokenType
import com.r3.corda.lib.tokens.contracts.types.TokenType
import net.corda.core.contracts.CommandData


/**
 * [TokenCommand]s are linked to groups of input and output tokens by the [IssuedTokenType]. This needs to be done
 * because if a transaction contains more than one type of token, we need to handle inputs and outputs grouped by token
 * type. Furthermore, we need to distinguish between the same token issued by two different issuers as the same token
 * issued by different issuers is not fungible, so one cannot add or subtract them. This is why [IssuedTokenType] is
 * used. The [IssuedTokenType] is also included in the [TokenType] so each command can be linked to a group. The
 * [AbstractTokenContract] doesn't allow a group of tokens without an associated [Command].
 *
 * @property token the group of [IssuedTokenType]s this command should be tied to.
 */
abstract class TokenCommand(open val token: IssuedTokenType, internal val inputIndicies: List<Int> = listOf(), internal val outputIndicies: List<Int> = listOf()) : CommandData {
    fun inputIndicies(): List<Int> {
        return inputIndicies.sortedBy { it }
    }

    fun outputIndicies(): List<Int> {
        return outputIndicies.sortedBy { it }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as TokenCommand

        if (token != other.token) return false
        if (inputIndicies != other.inputIndicies) return false
        if (outputIndicies != other.outputIndicies) return false

        return true
    }

    override fun hashCode(): Int {
        var result = token.hashCode()
        result = 31 * result + inputIndicies.hashCode()
        result = 31 * result + outputIndicies.hashCode()
        return result
    }

    override fun toString(): String {
        return "${this.javaClass.name}(token=$token, inputIndicies=$inputIndicies, outputIndicies=$outputIndicies)"
    }

}

/**
 * Used when issuing [FungibleToken]s or [NonFungibleToken]s.
 *
 * @property token the group of [IssuedTokenType]s this command should be tied to.
 * @property outputs the output state indices this command applies to.
 */
class IssueTokenCommand(override val token: IssuedTokenType, val outputs: List<Int> = listOf()) : TokenCommand(outputIndicies = outputs, token = token)

/**
 * Used when moving [FungibleToken]s or [NonFungibleToken]s.
 *
 * @property token the group of [IssuedTokenType]s this command should be tied to.
 * @property inputs the input state indices this command applies to.
 * @property outputs the output state indices this command applies to.
 */
class MoveTokenCommand(override val token: IssuedTokenType, val inputs: List<Int> = listOf(), val outputs: List<Int> = listOf()) : TokenCommand(inputIndicies = inputs, outputIndicies = outputs, token = token)

/**
 * Used when redeeming [FungibleToken]s or [NonFungibleToken]s.
 *
 * @property token the group of [IssuedTokenType]s this command should be tied to.
 * @property inputs the input state indices this command applies to.
 * @property outputs the output state indices this command applies to.
 */
class RedeemTokenCommand(override val token: IssuedTokenType, val inputs: List<Int> = listOf(), val outputs: List<Int> = listOf()) : TokenCommand(inputIndicies = inputs, outputIndicies = outputs, token = token)