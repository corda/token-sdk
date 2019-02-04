package com.r3.corda.sdk.token.contracts.schemas

import net.corda.core.schemas.MappedSchema

/**
 * Here, the idea is that we can add schemas for commonly used evolvable token types.
 */
object TokenSchema

object TokenSchemaV1 : MappedSchema(
        schemaFamily = TokenSchema.javaClass,
        version = 1,
        mappedTypes = listOf()
)




