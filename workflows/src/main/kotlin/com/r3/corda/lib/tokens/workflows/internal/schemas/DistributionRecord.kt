package com.r3.corda.lib.tokens.workflows.internal.schemas

import net.corda.core.identity.Party
import net.corda.core.schemas.MappedSchema
import net.corda.core.serialization.CordaSerializable
import org.hibernate.annotations.Type
import java.util.*
import javax.persistence.*

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
        @Type(type = "uuid-char")
        var linearId: UUID,

        @Column(name = "party", nullable = false)
        var party: Party

) {
    constructor(linearId: UUID, party: Party) : this(0, linearId, party)
}