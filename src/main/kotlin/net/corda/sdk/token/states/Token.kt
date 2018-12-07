package net.corda.sdk.token.states

import net.corda.core.contracts.Amount
import net.corda.core.contracts.CommandAndState
import net.corda.core.contracts.FungibleState
import net.corda.core.contracts.OwnableState
import net.corda.core.identity.AbstractParty
import net.corda.sdk.token.commands.TokenCommands
import net.corda.sdk.token.types.TokenType

data class Token<T : TokenType>(
        override val amount: Amount<T>,
        override val owner: AbstractParty
) : FungibleState<T>, OwnableState {
    override val participants: List<AbstractParty> get() = listOf(owner)
    override fun withNewOwner(newOwner: AbstractParty) = CommandAndState(TokenCommands.Move(), Token(amount, newOwner))
}