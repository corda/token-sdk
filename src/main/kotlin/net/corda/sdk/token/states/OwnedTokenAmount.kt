package net.corda.sdk.token.states

import net.corda.core.contracts.*
import net.corda.core.crypto.toStringShort
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.sdk.token.commands.Move
import net.corda.sdk.token.contracts.OwnedTokenAmountContract
import net.corda.sdk.token.types.EmbeddableToken
import net.corda.sdk.token.types.Issued

/**
 * This class is for handling the issuer / owner relationship for fungible token types. It allows the token definition
 * to evolve independently of who owns it, if necessary.
 */
@BelongsToContract(OwnedTokenAmountContract::class)
data class OwnedTokenAmount<T : EmbeddableToken>(
        override val amount: Amount<Issued<T>>,
        override val owner: AbstractParty
) : FungibleState<Issued<T>>, OwnableState {

    /** The current [owner] is always the sole participant. */
    override val participants: List<AbstractParty> get() = listOf(owner)

    /** Helper for changing the owner of the state. */
    override fun withNewOwner(newOwner: AbstractParty): CommandAndState {
        return CommandAndState(Move(amount.token), OwnedTokenAmount(amount, newOwner))
    }

    override fun toString(): String {
        val ownerString = (owner as? Party)?.name?.organisation ?: owner.owningKey.toStringShort().substring(0, 16)
        return "$amount owned by $ownerString"
    }

}