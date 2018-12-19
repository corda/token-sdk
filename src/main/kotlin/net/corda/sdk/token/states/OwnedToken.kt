package net.corda.sdk.token.states

import net.corda.core.contracts.CommandAndState
import net.corda.core.contracts.OwnableState
import net.corda.core.identity.AbstractParty
import net.corda.sdk.token.commands.OwnedTokenAmountCommands
import net.corda.sdk.token.types.Issued
import net.corda.sdk.token.types.token.Token

data class OwnedToken<T : Token>(
        val token: Issued<T>,
        override val owner: AbstractParty
) : OwnableState {
    override val participants: List<AbstractParty> get() = listOf(owner)
    override fun withNewOwner(newOwner: AbstractParty): CommandAndState {
        return CommandAndState(OwnedTokenAmountCommands.Move(), OwnedToken(token, newOwner))
    }
}