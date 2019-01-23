package net.corda.sdk.token.contracts.commands

import net.corda.core.contracts.CommandData

/**
 * Token commands are linked to groups of input and output tokens, usually by the embeddable token type or some Issued
 * token type. This needs to be done because if a transactions contains more than one type of token, we need to be able
 * to attribute the correct command to each group. The most simple way to do this is including the embeddable token in
 * the command.
 */
interface OwnedTokenCommand<T : Any> : CommandData {
    val token: T
}

data class Issue<T : Any>(override val token: T) : OwnedTokenCommand<T>
data class Move<T : Any>(override val token: T) : OwnedTokenCommand<T>
data class Redeem<T : Any>(override val token: T) : OwnedTokenCommand<T>