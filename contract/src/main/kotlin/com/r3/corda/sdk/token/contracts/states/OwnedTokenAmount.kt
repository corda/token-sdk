package com.r3.corda.sdk.token.contracts.states

import com.r3.corda.sdk.token.contracts.OwnedTokenAmountContract
import com.r3.corda.sdk.token.contracts.commands.MoveTokenCommand
import com.r3.corda.sdk.token.contracts.schemas.OwnedTokenAmountSchemaV1
import com.r3.corda.sdk.token.contracts.schemas.PersistentOwnedTokenAmount
import com.r3.corda.sdk.token.contracts.types.EmbeddableToken
import com.r3.corda.sdk.token.contracts.types.Issued
import net.corda.core.contracts.Amount
import net.corda.core.contracts.BelongsToContract
import net.corda.core.contracts.CommandAndState
import net.corda.core.contracts.FungibleState
import net.corda.core.identity.AbstractParty
import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentState
import net.corda.core.schemas.QueryableState

/**
 * This class is for handling the issuer / owner relationship for "non-fungible" token types. If the [EmbeddableToken]
 * is a [TokenPointer], then it allows the token can evolve independently of who owns it. This state object implements
 * [FungibleState] as the expectation is that it contains amounts of a token type which can be split and merged.
 *
 * All [EmbeddableToken]s are wrapped with an [Issued] class to add the issuer party. This is necessary so that the
 * [OwnedToken] represents a contract or agreement between an issuer and an owner. In effect, this token conveys a right
 * for the owner to make a claim on the issuer for whatever the [EmbeddableToken] represents.
 *
 * The class is open, so it can be extended to add new functionality, like a whitelisted token, for example.
 */
@BelongsToContract(OwnedTokenAmountContract::class)
open class OwnedTokenAmount<T : EmbeddableToken>(
        override val amount: Amount<Issued<T>>,
        override val owner: AbstractParty
) : FungibleState<Issued<T>>, AbstractOwnedToken(), QueryableState {
    /** Helper for changing the owner of the state. */
    override fun withNewOwner(newOwner: AbstractParty): CommandAndState {
        return CommandAndState(MoveTokenCommand(amount.token), OwnedTokenAmount(amount, newOwner))
    }

    override fun toString(): String = "$amount owned by $ownerString"

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

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is OwnedTokenAmount<*>) return false

        if (amount != other.amount) return false
        if (owner != other.owner) return false

        return true
    }

    override fun hashCode(): Int {
        var result = amount.hashCode()
        result = 31 * result + owner.hashCode()
        return result
    }
}

