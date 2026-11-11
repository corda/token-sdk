package com.r3.corda.lib.tokens.workflows.internal.schemas

import net.corda.core.identity.Party
import net.corda.core.schemas.MappedSchema
import net.corda.core.serialization.CordaSerializable
import java.util.UUID
import javax.persistence.AttributeConverter
import javax.persistence.Column
import javax.persistence.Convert
import javax.persistence.Entity
import javax.persistence.GeneratedValue
import javax.persistence.Id
import javax.persistence.Index
import javax.persistence.Table

object DistributionRecordSchema

object DistributionRecordSchemaV1 : MappedSchema(
        schemaFamily = DistributionRecordSchema.javaClass,
        version = 1,
        mappedTypes = listOf(DistributionRecord::class.java)
)

@CordaSerializable
@Entity
@Table(name = "distribution_record", indexes = [Index(name = "dist_record_idx", columnList = "linear_id")])
class DistributionRecord(

        @Id
        @GeneratedValue
        var id: Long,

        @Column(name = "linear_id", nullable = false)
        @Convert(converter = UUIDConverter::class)
        var linearId: UUID,

        @Column(name = "party", nullable = false)
        var party: Party

) {
    constructor(linearId: UUID, party: Party) : this(0, linearId, party)
}

/**
 * Stores a [UUID] as its 36-character string, the same format Hibernate 5's `uuid-char` type used, so that existing rows stay readable.
 * A JPA converter rather than a Hibernate `@Type`, so that the SDK does not depend on Hibernate.
 */
class UUIDConverter : AttributeConverter<UUID, String> {
    override fun convertToDatabaseColumn(attribute: UUID?): String? = attribute?.toString()
    override fun convertToEntityAttribute(dbData: String?): UUID? = dbData?.let(UUID::fromString)
}
