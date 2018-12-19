package net.corda.sdk.token.states

import net.corda.core.contracts.*
import net.corda.core.crypto.toStringShort
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.sdk.token.commands.OwnedTokenAmountCommands
import net.corda.sdk.token.contracts.OwnedTokenAmountContract
import net.corda.sdk.token.types.Issued
import net.corda.sdk.token.types.token.Token

@BelongsToContract(OwnedTokenAmountContract::class)
data class OwnedTokenAmount<T : Token>(
        override val amount: Amount<Issued<T>>,
        override val owner: AbstractParty
) : FungibleState<Issued<T>>, OwnableState {
    override val participants: List<AbstractParty> get() = listOf(owner)

    override fun toString(): String {
        val ownerString = (owner as? Party)?.name?.organisation ?: owner.owningKey.toStringShort().substring(0, 16)
        return "$amount owned by $ownerString"
    }

    override fun withNewOwner(newOwner: AbstractParty): CommandAndState {
        return CommandAndState(OwnedTokenAmountCommands.Move(), OwnedTokenAmount(amount, newOwner))
    }

}