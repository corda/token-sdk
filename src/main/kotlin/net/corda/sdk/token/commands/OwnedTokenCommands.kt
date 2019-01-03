package net.corda.sdk.token.commands

import net.corda.core.contracts.CommandData
import net.corda.sdk.token.types.token.EmbeddableToken

/**
 * Token commands are linked to groups of input and output tokens by the embeddable token type. This needs to be done
 * because if a transactions contains more than one type of token, we need to be able to attribute the correct command
 * to each group. The most simple way to do this is including the embeddable token in the command.
 */
interface OwnedTokenCommands<T : EmbeddableToken> : CommandData {
    val type: Class<T>
}

data class Issue<T : EmbeddableToken>(override val type: Class<T>) : OwnedTokenCommands<T>
data class Move<T : EmbeddableToken>(override val type: Class<T>) : OwnedTokenCommands<T>
data class Redeem<T : EmbeddableToken>(override val type: Class<T>) : OwnedTokenCommands<T>