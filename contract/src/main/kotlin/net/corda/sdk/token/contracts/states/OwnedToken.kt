package net.corda.sdk.token.contracts.states

import net.corda.core.contracts.BelongsToContract
import net.corda.core.contracts.CommandAndState
import net.corda.core.crypto.toStringShort
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentState
import net.corda.core.schemas.QueryableState
import net.corda.sdk.token.contracts.OwnedTokenContract
import net.corda.sdk.token.contracts.commands.Move
import net.corda.sdk.token.contracts.schemas.OwnedTokenSchemaV1
import net.corda.sdk.token.contracts.schemas.PersistentOwnedToken
import net.corda.sdk.token.contracts.types.AbstractOwnedToken
import net.corda.sdk.token.contracts.types.EmbeddableToken
import net.corda.sdk.token.contracts.types.Issued

/**
 * This class is for handling the issuer / owner relationship for non-fungible token types. It allows the token
 * definition to evolve independently of who owns it, if necessary.
 */
@BelongsToContract(OwnedTokenContract::class)
data class OwnedToken<T : EmbeddableToken>(val token: Issued<T>, override val owner: AbstractParty) : AbstractOwnedToken(), QueryableState {

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

    override fun generateMappedObject(schema: MappedSchema): PersistentState = when (schema) {
        is OwnedTokenSchemaV1 -> PersistentOwnedToken(
                issuer = token.issuer,
                owner = owner,
                tokenClass = tokenClass(token.product),
                tokenIdentifier = tokenIdentifier(token.product)
        )
        else -> throw IllegalArgumentException("Unrecognised schema $schema")
    }

    override fun supportedSchemas() = listOf(OwnedTokenSchemaV1)

}