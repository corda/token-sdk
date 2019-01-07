package net.corda.sdk.token.states

import net.corda.core.contracts.BelongsToContract
import net.corda.core.contracts.CommandAndState
import net.corda.core.contracts.OwnableState
import net.corda.core.crypto.toStringShort
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.sdk.token.commands.Move
import net.corda.sdk.token.contracts.OwnedTokenContract
import net.corda.sdk.token.types.EmbeddableToken
import net.corda.sdk.token.types.Issued

/**
 * This class is for handling the issuer / owner relationship for non-fungible token types. It allows the token
 * definition to evolve independently of who owns it, if necessary.
 */
@BelongsToContract(OwnedTokenContract::class)
data class OwnedToken<T : EmbeddableToken>(val token: Issued<T>, override val owner: AbstractParty) : OwnableState {

    /** The current [owner] is always the sole participant. */
    override val participants: List<AbstractParty> get() = listOf(owner)

    /** Helper for changing the owner of the state. */
    override fun withNewOwner(newOwner: AbstractParty): CommandAndState {
        return CommandAndState(Move(token), OwnedToken(token, newOwner))
    }

    override fun toString(): String {
        val ownerString = (owner as? Party)?.name?.organisation ?: owner.owningKey.toStringShort().substring(0, 16)
        return "$token owned by $ownerString"
    }

}