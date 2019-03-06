package com.r3.corda.sdk.token.contracts.schemas

import net.corda.core.schemas.MappedSchema

/**
 * Here, schemas can be added for commonly used [EvolvableTokenType]s.
 */
object TokenSchema

object TokenSchemaV1 : MappedSchema(
        schemaFamily = TokenSchema.javaClass,
        version = 1,
        mappedTypes = listOf()
)
