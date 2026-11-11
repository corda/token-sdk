package com.r3.corda.lib.tokens.workflows.internal.schemas

import net.corda.core.identity.Party
import net.corda.core.schemas.MappedSchema
import net.corda.core.serialization.CordaSerializable
import java.util.UUID
import javax.persistence.Column
import javax.persistence.Convert
import javax.persistence.Entity
import javax.persistence.GeneratedValue
import javax.persistence.Id
import javax.persistence.Index
import javax.persistence.Table
import jakarta.persistence.Column as JakartaColumn
import jakarta.persistence.Convert as JakartaConvert
import jakarta.persistence.Entity as JakartaEntity
import jakarta.persistence.GeneratedValue as JakartaGeneratedValue
import jakarta.persistence.Id as JakartaId
import jakarta.persistence.Index as JakartaIndex
import jakarta.persistence.Table as JakartaTable

object DistributionRecordSchema

object DistributionRecordSchemaV1 : MappedSchema(
        schemaFamily = DistributionRecordSchema.javaClass,
        version = 1,
        mappedTypes = listOf(DistributionRecord::class.java)
)

/**
 * The entity carries both the `javax.persistence` (Corda 4.14 and earlier, Hibernate 5) and the `jakarta.persistence` (Corda 4.15,
 * Hibernate 7) annotations, with identical attributes, so that one jar runs on both: Hibernate reads the set it understands and the JVM
 * drops the annotations whose classes are not on the node's classpath. Neither API jar is bundled with the CorDapp.
 */
@CordaSerializable
@Entity
@JakartaEntity
@Table(name = "distribution_record", indexes = [Index(name = "dist_record_idx", columnList = "linear_id")])
@JakartaTable(name = "distribution_record", indexes = [JakartaIndex(name = "dist_record_idx", columnList = "linear_id")])
class DistributionRecord(

        @Id
        @JakartaId
        @GeneratedValue
        @JakartaGeneratedValue
        var id: Long,

        @Column(name = "linear_id", nullable = false)
        @JakartaColumn(name = "linear_id", nullable = false)
        @Convert(converter = UUIDConverter::class)
        @JakartaConvert(converter = JakartaUUIDConverter::class)
        var linearId: UUID,

        @Column(name = "party", nullable = false)
        @JakartaColumn(name = "party", nullable = false)
        var party: Party

) {
    constructor(linearId: UUID, party: Party) : this(0, linearId, party)
}

/**
 * Stores a [UUID] as its 36-character string, the same format Hibernate 5's `uuid-char` type used, so that existing rows stay readable.
 * One implementation per persistence API, as a class implementing both interfaces could not be loaded on a node that has only one of
 * them on its classpath.
 */
class UUIDConverter : javax.persistence.AttributeConverter<UUID, String> {
    override fun convertToDatabaseColumn(attribute: UUID?): String? = uuidToDatabaseColumn(attribute)
    override fun convertToEntityAttribute(dbData: String?): UUID? = uuidFromDatabaseColumn(dbData)
}

class JakartaUUIDConverter : jakarta.persistence.AttributeConverter<UUID, String> {
    override fun convertToDatabaseColumn(attribute: UUID?): String? = uuidToDatabaseColumn(attribute)
    override fun convertToEntityAttribute(dbData: String?): UUID? = uuidFromDatabaseColumn(dbData)
}

private fun uuidToDatabaseColumn(attribute: UUID?): String? = attribute?.toString()

private fun uuidFromDatabaseColumn(dbData: String?): UUID? = dbData?.let(UUID::fromString)
