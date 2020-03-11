package com.r3.corda.lib.tokens.contracts.internal.schemas

import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentState
import java.security.PublicKey
import javax.persistence.*

object FungibleTokenSchema

object FungibleTokenSchemaV1 : MappedSchema(
        schemaFamily = FungibleTokenSchema.javaClass,
        version = 1,
        mappedTypes = listOf(PersistentFungibleToken::class.java)
)

object FungibleTokenOwnerSchemaV1 : MappedSchema(
        schemaFamily = FungibleTokenSchema.javaClass,
        version = 1,
        mappedTypes = listOf(PersistentFungibleTokenOwner::class.java)
)

@Entity
@Table(name = "fungible_token", indexes = [
    Index(name = "amount_idx", columnList = "amount"),
    Index(name = "held_token_amount_idx", columnList = "token_class, token_identifier")
])
class PersistentFungibleToken(
        @Column(name = "issuer", nullable = false)
        var issuer: Party,

        @Column(name = "holder")
        var holder: AbstractParty?,

        @Column(name = "amount", nullable = false)
        var amount: Long,

        // The fully qualified class name of the class which implements the token tokenType.
        // This is either a fixed token or a evolvable token.
        @Column(name = "token_class", nullable = false)
        @Convert(converter = TokenClassConverter::class)
        var tokenClass: Class<*>,

        // This can either be a symbol or a linearID depending on whether the token is evolvable or fixed.
        // Not all tokens will have identifiers if there is only one instance for a token class, for example.
        // It is expected that the combination of token_class and token_symbol will be enough to identity a unique
        // token.
        @Column(name = "token_identifier", nullable = true)
        var tokenIdentifier: String
) : PersistentState()

@Entity
@Table(name = "fungible_token_owner", indexes = [Index(name = "owning_key_idx", columnList = "holding_key")])
class PersistentFungibleTokenOwner(
        @Column(name = "holding_key")
        val owningKeyHash: String
) : PersistentState()
