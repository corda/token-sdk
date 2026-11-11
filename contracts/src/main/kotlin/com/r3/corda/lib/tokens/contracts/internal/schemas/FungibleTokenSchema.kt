package com.r3.corda.lib.tokens.contracts.internal.schemas

import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentState
import javax.persistence.Column
import javax.persistence.Convert
import javax.persistence.Entity
import javax.persistence.Index
import javax.persistence.Table
import jakarta.persistence.Column as JakartaColumn
import jakarta.persistence.Convert as JakartaConvert
import jakarta.persistence.Entity as JakartaEntity
import jakarta.persistence.Index as JakartaIndex
import jakarta.persistence.Table as JakartaTable

object FungibleTokenSchema

object FungibleTokenSchemaV1 : MappedSchema(
        schemaFamily = FungibleTokenSchema.javaClass,
        version = 1,
        mappedTypes = listOf(PersistentFungibleToken::class.java)
)

/**
 * The entity carries both the `javax.persistence` (Corda 4.14 and earlier, Hibernate 5) and the `jakarta.persistence` (Corda 4.15,
 * Hibernate 7) annotations, with identical attributes, so that one jar runs on both: Hibernate reads the set it understands and the JVM
 * drops the annotations whose classes are not on the node's classpath. Neither API jar is bundled with the CorDapp.
 */
@Entity
@JakartaEntity
@Table(name = "fungible_token", indexes = [
    Index(name = "amount_idx", columnList = "amount"),
    Index(name = "held_token_amount_idx", columnList = "token_class, token_identifier"),
    Index(name = "holding_key_idx", columnList = "holding_key")
])
@JakartaTable(name = "fungible_token", indexes = [
    JakartaIndex(name = "amount_idx", columnList = "amount"),
    JakartaIndex(name = "held_token_amount_idx", columnList = "token_class, token_identifier"),
    JakartaIndex(name = "holding_key_idx", columnList = "holding_key")
])
class PersistentFungibleToken(
        @Column(name = "issuer", nullable = false)
        @JakartaColumn(name = "issuer", nullable = false)
        var issuer: Party,

        @Column(name = "holder")
        @JakartaColumn(name = "holder")
        var holder: AbstractParty?,

        @Column(name = "amount", nullable = false)
        @JakartaColumn(name = "amount", nullable = false)
        var amount: Long,

        // The fully qualified class name of the class which implements the token tokenType.
        // This is either a fixed token or a evolvable token.
        @Column(name = "token_class", nullable = false)
        @JakartaColumn(name = "token_class", nullable = false)
        @Convert(converter = TokenClassConverter::class)
        @JakartaConvert(converter = JakartaTokenClassConverter::class)
        var tokenClass: Class<*>,

        // This can either be a symbol or a linearID depending on whether the token is evolvable or fixed.
        // Not all tokens will have identifiers if there is only one instance for a token class, for example.
        // It is expected that the combination of token_class and token_symbol will be enough to identity a unique
        // token.
        @Column(name = "token_identifier", nullable = true)
        @JakartaColumn(name = "token_identifier", nullable = true)
        var tokenIdentifier: String,

        @Column(name = "holding_key", nullable = true)
        @JakartaColumn(name = "holding_key", nullable = true)
        val owningKeyHash: String?
) : PersistentState()
