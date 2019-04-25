package com.r3.corda.sdk.token.contracts.states

import com.r3.corda.sdk.token.contracts.NonFungibleTokenContract
import com.r3.corda.sdk.token.contracts.schemas.NonFungibleTokenSchemaV1
import com.r3.corda.sdk.token.contracts.schemas.PersistentNonFungibleToken
import com.r3.corda.sdk.token.contracts.types.FixedTokenType
import com.r3.corda.sdk.token.contracts.types.IssuedTokenType
import com.r3.corda.sdk.token.contracts.types.TokenPointer
import com.r3.corda.sdk.token.contracts.types.TokenType
import com.r3.corda.sdk.token.contracts.utilities.holderString
import net.corda.core.contracts.Amount
import net.corda.core.contracts.BelongsToContract
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentState
import net.corda.core.schemas.QueryableState

/**
 * This class is for handling the issuer and holder relationship for non-fungible token types. Non-fungible tokens
 * cannot be split and merged, as they are considered unique at the ledger level. If the [TokenType] is a
 * [TokenPointer], then the token can evolve independently of who holds it. Otherwise, if a [FixedTokenType] is used
 * then the [TokenType] is in-lined into the [NonFungibleToken] and it cannot change. There is no [Amount] property in
 * this class, as the assumption is there is only ever ONE of the [IssuedTokenType] provided. It is up to issuers to
 * ensure that only ONE of a non-fungible token ever issued. All [TokenType]s are wrapped with an [IssuedTokenType]
 * class to add the issuer [Party]. This is necessary so that the [NonFungibleToken] represents an agreement between the
 * issuer and holder. In effect, the [NonFungibleToken] conveys a right for the holder to make a claim on the issuer for
 * whatever the [IssuedTokenType] represents. [NonFungibleToken] is open, so it can be extended to allow for additional
 * functionality, if necessary.
 *
 * @property token the [IssuedTokenType] which this [NonFungibleToken] is in respect of.
 * @property holder the [AbstractParty] which holds the [IssuedTokenType].
 * @param T the [TokenType].
 */
@BelongsToContract(NonFungibleTokenContract::class)
open class NonFungibleToken<T : TokenType>(
        val token: IssuedTokenType<T>,
        override val holder: AbstractParty
) : AbstractToken<T>, QueryableState {

    override val issuedTokenType: IssuedTokenType<T> get() = token

    override fun toString(): String = "$token owned by $holderString"

    override fun withNewHolder(newHolder: AbstractParty): NonFungibleToken<T> {
        return NonFungibleToken(token = token, holder = newHolder)
    }

    override fun generateMappedObject(schema: MappedSchema): PersistentState = when (schema) {
        is NonFungibleTokenSchemaV1 -> PersistentNonFungibleToken(
                issuer = token.issuer,
                holder = holder,
                tokenClass = token.tokenType.tokenClass,
                tokenIdentifier = token.tokenType.tokenIdentifier
        )
        else -> throw IllegalArgumentException("Unrecognised schema $schema")
    }

    override fun supportedSchemas() = listOf(NonFungibleTokenSchemaV1)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is NonFungibleToken<*>) return false

        if (token != other.token) return false
        if (holder != other.holder) return false

        return true
    }

    override fun hashCode(): Int {
        var result = token.hashCode()
        result = 31 * result + holder.hashCode()
        return result
    }
}