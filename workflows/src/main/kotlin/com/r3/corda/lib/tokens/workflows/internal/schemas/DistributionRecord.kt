package com.r3.corda.lib.tokens.workflows.internal.schemas

import net.corda.core.identity.Party
import net.corda.core.schemas.MappedSchema
import net.corda.core.serialization.CordaSerializable
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.annotations.TypeDef
import org.hibernate.type.UUIDCharType
import java.sql.Types
import java.util.UUID
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.GeneratedValue
import javax.persistence.Id
import javax.persistence.Index
import javax.persistence.Table
import jakarta.persistence.Column as JakartaColumn
import jakarta.persistence.Entity as JakartaEntity
import jakarta.persistence.GeneratedValue as JakartaGeneratedValue
import jakarta.persistence.Id as JakartaId
import jakarta.persistence.Index as JakartaIndex
import jakarta.persistence.Table as JakartaTable
import org.hibernate.annotations.Target as HibernateTarget

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
 *
 * `linear_id` is a `VARCHAR` holding the 36-character string of the [UUID] (Hibernate 5's `uuid-char`), and both Hibernate versions must
 * map it as such. Corda Enterprise turns on nationalised character data on Oracle and SQL Server, which maps any other string-valued
 * attribute (a JPA `AttributeConverter` to `String` included) as `NVARCHAR`: Hibernate 5 then fails the node's schema validation
 * against the `VARCHAR` column, and Hibernate 7 binds `NVARCHAR` values, which stops Oracle from using `dist_record_idx`. So the
 * attribute carries both Hibernate mappings, each in annotations that the other Hibernate version does not have, which the JVM drops:
 * - Hibernate 5: `@Target(LinearIdType)` binds it as a [LinearIdType], which the class's `@TypeDef` maps to `uuid-char`. (Hibernate 5's
 *   `@Type(type = "uuid-char")` cannot be used: Hibernate 7 has an `@Type` of its own, and fails on a class that carries Hibernate 5's.)
 * - Hibernate 7: `@JdbcTypeCode(Types.VARCHAR)`, the mapping Corda 4.15 gives `uuid-char` when it converts a CorDapp built for 4.14.
 */
@CordaSerializable
@Entity
@JakartaEntity
@Table(name = "distribution_record", indexes = [Index(name = "dist_record_idx", columnList = "linear_id")])
@JakartaTable(name = "distribution_record", indexes = [JakartaIndex(name = "dist_record_idx", columnList = "linear_id")])
@TypeDef(typeClass = UUIDCharType::class, defaultForType = DistributionRecord.LinearIdType::class)
class DistributionRecord(

        @Id
        @JakartaId
        @GeneratedValue
        @JakartaGeneratedValue
        var id: Long,

        @Column(name = "linear_id", nullable = false)
        @JakartaColumn(name = "linear_id", nullable = false)
        @HibernateTarget(LinearIdType::class)
        @JdbcTypeCode(Types.VARCHAR)
        var linearId: UUID,

        @Column(name = "party", nullable = false)
        @JakartaColumn(name = "party", nullable = false)
        var party: Party

) {
    constructor(linearId: UUID, party: Party) : this(0, linearId, party)

    /** Never instantiated: only the key of the Hibernate 5 type definition of [linearId] (see [DistributionRecord]). */
    class LinearIdType private constructor()
}
