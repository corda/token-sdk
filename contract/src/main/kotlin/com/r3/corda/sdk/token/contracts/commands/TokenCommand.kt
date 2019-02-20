package com.r3.corda.sdk.token.contracts.commands

import com.r3.corda.sdk.token.contracts.types.EmbeddableToken
import com.r3.corda.sdk.token.contracts.types.IssuedToken
import net.corda.core.contracts.CommandData

/**
 * [TokenCommands] are linked to groups of input and output tokens, usually by the embeddable token type or some
 * [IssuedToken] token type. This needs to be done because if a transaction contains more than one type of token, we need to
 * be able to attribute the correct command to each group. The most simple way to do this is including an [IssuedToken]
 * [EmbeddableToken] in the command.
 */
interface TokenCommand<T : EmbeddableToken> : CommandData {
    val token: IssuedToken<T>
}

data class IssueTokenCommand<T : EmbeddableToken>(override val token: IssuedToken<T>) : TokenCommand<T>
data class MoveTokenCommand<T : EmbeddableToken>(override val token: IssuedToken<T>) : TokenCommand<T>
data class RedeemTokenCommand<T : EmbeddableToken>(override val token: IssuedToken<T>) : TokenCommand<T>