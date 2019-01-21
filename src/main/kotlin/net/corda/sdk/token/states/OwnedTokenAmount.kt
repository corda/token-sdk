package net.corda.sdk.token.states

import net.corda.core.contracts.Amount
import net.corda.core.contracts.BelongsToContract
import net.corda.core.contracts.CommandAndState
import net.corda.core.contracts.FungibleState
import net.corda.core.crypto.toStringShort
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentState
import net.corda.core.schemas.QueryableState
import net.corda.sdk.token.commands.Move
import net.corda.sdk.token.contracts.OwnedTokenAmountContract
import net.corda.sdk.token.persistence.schemas.OwnedTokenAmountSchemaV1
import net.corda.sdk.token.persistence.schemas.PersistentOwnedTokenAmount
import net.corda.sdk.token.types.AbstractOwnedToken
import net.corda.sdk.token.types.EmbeddableToken
import net.corda.sdk.token.types.Issued

/**
 * This class is for handling the issuer / owner relationship for fungible token types. It allows the token definition
 * to evolve independently of who owns it, if necessary.
 * TODO: Consider whether this should be open? For example: should owner whitelist state functionality be separate.
 */
@BelongsToContract(OwnedTokenAmountContract::class)
data class OwnedTokenAmount<T : EmbeddableToken>(
        override val amount: Amount<Issued<T>>,
        override val owner: AbstractParty
) : FungibleState<Issued<T>>, AbstractOwnedToken(), QueryableState {

    /** Helper for changing the owner of the state. */
    override fun withNewOwner(newOwner: AbstractParty): CommandAndState {
        return CommandAndState(Move(amount.token), OwnedTokenAmount(amount, newOwner))
    }

    override fun toString(): String {
        val ownerString = (owner as? Party)?.name?.organisation ?: owner.owningKey.toStringShort().substring(0, 16)
        return "$amount owned by $ownerString"
    }

    override fun generateMappedObject(schema: MappedSchema): PersistentState = when (schema) {
        is OwnedTokenAmountSchemaV1 -> PersistentOwnedTokenAmount(
                issuer = amount.token.issuer,
                owner = owner,
                amount = amount.quantity,
                tokenClass = tokenClass(amount.token.product),
                tokenIdentifier = tokenIdentifier(amount.token.product)
        )
        else -> throw IllegalArgumentException("Unrecognised schema $schema")
    }

    override fun supportedSchemas() = listOf(OwnedTokenAmountSchemaV1)

}