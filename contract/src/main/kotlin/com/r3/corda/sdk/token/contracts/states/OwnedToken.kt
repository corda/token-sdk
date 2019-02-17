package com.r3.corda.sdk.token.contracts.states

import com.r3.corda.sdk.token.contracts.OwnedTokenContract
import com.r3.corda.sdk.token.contracts.commands.MoveTokenCommand
import com.r3.corda.sdk.token.contracts.schemas.OwnedTokenSchemaV1
import com.r3.corda.sdk.token.contracts.schemas.PersistentOwnedToken
import com.r3.corda.sdk.token.contracts.types.EmbeddableToken
import com.r3.corda.sdk.token.contracts.types.Issued
import net.corda.core.contracts.BelongsToContract
import net.corda.core.contracts.CommandAndState
import net.corda.core.identity.AbstractParty
import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentState
import net.corda.core.schemas.QueryableState

/**
 * This class is for handling the issuer / owner relationship for "non-fungible" token types. If the [EmbeddableToken]
 * is a [TokenPointer], then it allows the token can evolve independently of who owns it. There is no [Amount] property
 * in this class, as the assumption is there is only ever ONE of the [EmbeddableToken] provided. It is up to issuers to
 * ensure that only ONE of a non-fungible token is ever issued.
 *
 * All [EmbeddableToken]s are wrapped with an [Issued] class to add the issuer party. This is necessary so that the
 * [OwnedToken] represents a contract or agreement between an issuer and an owner. In effect, this token conveys a right
 * for the owner to make a claim on the issuer for whatever the [EmbeddableToken] represents.
 *
 * [OwnedToken] is open, so it can be extended to allow for additional functionality, if necessary.
 */
@BelongsToContract(OwnedTokenContract::class)
open class OwnedToken<T : EmbeddableToken>(
        val token: Issued<T>,
        override val owner: AbstractParty
) : AbstractOwnedToken(), QueryableState {
    /** Helper for changing the owner of the state. */
    override fun withNewOwner(newOwner: AbstractParty): CommandAndState {
        return CommandAndState(MoveTokenCommand(token), OwnedToken(token, newOwner))
    }

    override fun toString(): String = "$token owned by $ownerString"

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

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is OwnedToken<*>) return false

        if (token != other.token) return false
        if (owner != other.owner) return false

        return true
    }

    override fun hashCode(): Int {
        var result = token.hashCode()
        result = 31 * result + owner.hashCode()
        return result
    }
}