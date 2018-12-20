package net.corda.sdk.token.states

import net.corda.core.contracts.CommandAndState
import net.corda.core.contracts.OwnableState
import net.corda.core.crypto.toStringShort
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.sdk.token.commands.OwnedTokenAmountCommands
import net.corda.sdk.token.types.Issued
import net.corda.sdk.token.types.token.Token

/**
 * This class is for handling the issuer / owner relationship for non-fungible token types. It allows the token
 * definition to evolve independently of who owns it, if necessary.
 */
data class OwnedToken<T : Token>(
        val token: Issued<T>,
        override val owner: AbstractParty
) : OwnableState {

    override val participants: List<AbstractParty> get() = listOf(owner)

    override fun withNewOwner(newOwner: AbstractParty): CommandAndState {
        return CommandAndState(OwnedTokenAmountCommands.Move(), OwnedToken(token, newOwner))
    }

    override fun toString(): String {
        val ownerString = (owner as? Party)?.name?.organisation ?: owner.owningKey.toStringShort().substring(0, 16)
        return "$token owned by $ownerString"
    }

}