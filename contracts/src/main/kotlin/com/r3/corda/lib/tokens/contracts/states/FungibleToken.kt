package com.r3.corda.lib.tokens.contracts.states

import com.r3.corda.lib.tokens.contracts.FungibleTokenContract
import com.r3.corda.lib.tokens.contracts.internal.schemas.FungibleTokenSchemaV1
import com.r3.corda.lib.tokens.contracts.internal.schemas.PersistentFungibleToken
import com.r3.corda.lib.tokens.contracts.types.IssuedTokenType
import com.r3.corda.lib.tokens.contracts.types.TokenPointer
import com.r3.corda.lib.tokens.contracts.types.TokenType
import com.r3.corda.lib.tokens.contracts.utilities.getAttachmentIdForGenericParam
import com.r3.corda.lib.tokens.contracts.utilities.holderString
import net.corda.core.contracts.Amount
import net.corda.core.contracts.BelongsToContract
import net.corda.core.contracts.FungibleState
import net.corda.core.crypto.SecureHash
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentState
import net.corda.core.schemas.QueryableState

/**
 * This class is for handling the issuer and holder relationship for fungible token types. If the [TokenType] is a
 * [TokenPointer], then the token can evolve independently of who owns it. Otherwise, the token definition is inlined
 * into the [FungibleToken] state. This state object implements [FungibleState] as the expectation is that it contains
 * [Amount]s of an [IssuedTokenType] which can be split and merged. All [TokenType]s are wrapped with an [IssuedTokenType]
 * class to add the issuer party, this is necessary so that the [FungibleToken] represents an agreement between an issuer
 * of the [IssuedTokenType] and a holder of the [IssuedTokenType]. In effect, the [FungibleToken] conveys a right for the
 * holder to make a claim on the issuer for whatever the [IssuedTokenType] represents. The class is defined as open, so
 * it can be extended to add new functionality, like a whitelisted token, for example.
 *
 * @property amount the [Amount] of [IssuedTokenType] represented by this [FungibleToken].
 * @property holder the [AbstractParty] which has a claim on the issuer of the [IssuedTokenType].
 * @param T the [TokenType] this [FungibleToken] state is in respect of.
 */
@BelongsToContract(FungibleTokenContract::class)
class FungibleToken<T : TokenType>(
        override val amount: Amount<IssuedTokenType<T>>,
        override val holder: AbstractParty,
        override val tokenTypeJarHash: SecureHash? = amount.token.tokenType.getAttachmentIdForGenericParam()
) : FungibleState<IssuedTokenType<T>>, AbstractToken<T>, QueryableState {

    override val tokenType: T get() = amount.token.tokenType

    override val issuedTokenType: IssuedTokenType<T> get() = amount.token

    override val issuer: Party get() = amount.token.issuer

    override fun toString(): String = "$amount owned by $holderString"

    override fun withNewHolder(newHolder: AbstractParty): FungibleToken<T> {
        return FungibleToken(amount = amount, holder = newHolder, tokenTypeJarHash = tokenTypeJarHash)
    }

    override fun generateMappedObject(schema: MappedSchema): PersistentState = when (schema) {
        is FungibleTokenSchemaV1 -> PersistentFungibleToken(
                issuer = amount.token.issuer,
                holder = holder,
                amount = amount.quantity,
                tokenClass = amount.token.tokenType.tokenClass,
                tokenIdentifier = amount.token.tokenType.tokenIdentifier
        )
        else -> throw IllegalArgumentException("Unrecognised schema $schema")
    }

    override fun supportedSchemas() = listOf(FungibleTokenSchemaV1)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is FungibleToken<*>) return false
        if (amount != other.amount) return false
        if (holder != other.holder) return false
        return true
    }

    override fun hashCode(): Int {
        var result = amount.hashCode()
        result = 31 * result + holder.hashCode()
        return result
    }
}