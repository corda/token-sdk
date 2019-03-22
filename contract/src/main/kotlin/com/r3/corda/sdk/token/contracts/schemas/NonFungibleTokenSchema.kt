package com.r3.corda.sdk.token.contracts.schemas

import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentState
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Index
import javax.persistence.Table

object NonFungibleTokenSchema

object NonFungibleTokenSchemaV1 : MappedSchema(
        schemaFamily = NonFungibleTokenSchema.javaClass,
        version = 1,
        mappedTypes = listOf(PersistentNonFungibleToken::class.java)
)

@Entity
@Table(name = "non_fungible_token", indexes = [
    Index(name = "owned_token_idx", columnList = "token_class, token_identifier")
])
class PersistentNonFungibleToken(
        @Column(name = "issuer", nullable = false)
        var issuer: Party,

        @Column(name = "holder", nullable = false)
        var holder: AbstractParty,

        // The fully qualified class name of the class which implements the token tokenType.
        // This is either a fixed token or a evolvable token.
        @Column(name = "token_class", nullable = false)
        var tokenClass: Class<*>,

        // This can either be a symbol or a linearID depending on whether the token is evolvable or fixed.
        // Not all tokens will have identifiers if there is only one instance for a token class, for example.
        // It is expected that the combination of token_class and token_symbol will be enough to identity a unique
        // token.
        @Column(name = "token_identifier", nullable = true)
        var tokenIdentifier: String

) : PersistentState()
