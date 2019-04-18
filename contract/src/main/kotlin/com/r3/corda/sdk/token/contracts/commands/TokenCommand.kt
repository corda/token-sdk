package com.r3.corda.sdk.token.contracts.commands

import com.r3.corda.sdk.token.contracts.states.FungibleToken
import com.r3.corda.sdk.token.contracts.states.NonFungibleToken
import com.r3.corda.sdk.token.contracts.types.IssuedTokenType
import com.r3.corda.sdk.token.contracts.types.TokenType
import net.corda.core.contracts.CommandData

/**
 * [TokenCommand]s are linked to groups of input and output tokens by the [IssuedTokenType]. This needs to be done
 * because if a transaction contains more than one type of token, we need to handle inputs and outputs grouped by token
 * type. Furthermore, we need to distinguish between the same token issued by two different issuers as the same token
 * issued by different issuers is not fungible, so one cannot add or subtract them. This is why [IssuedTokenType] is
 * used. The [IssuedTokenType] is also included in the [TokenType] so each command can be linked to a group. The
 * [AbstractTokenContract] doesn't allow a group of tokens without a [Command].
 *
 * @property token the group of [IssuedTokenType]s this command should be tied to.
 * @param T the [TokenType].
 */
interface TokenCommand<T : TokenType> : CommandData {
    val token: IssuedTokenType<T>
}

/**
 * Used when issuing [FungibleToken]s or [NonFungibleToken]s.
 *
 * @property token the group of [IssuedTokenType]s this command should be tied to.
 * @param T the [TokenType].
 */
data class IssueTokenCommand<T : TokenType>(override val token: IssuedTokenType<T>) : TokenCommand<T>

/**
 * Used when moving [FungibleToken]s or [NonFungibleToken]s.
 *
 * @property token the group of [IssuedTokenType]s this command should be tied to.
 * @param T the [TokenType].
 */
data class MoveTokenCommand<T : TokenType>(override val token: IssuedTokenType<T>) : TokenCommand<T>

/**
 * Used when redeeming [FungibleToken]s or [NonFungibleToken]s.
 *
 * @property token the group of [IssuedTokenType]s this command should be tied to.
 * @param T the [TokenType].
 */
data class RedeemTokenCommand<T : TokenType>(override val token: IssuedTokenType<T>) : TokenCommand<T>