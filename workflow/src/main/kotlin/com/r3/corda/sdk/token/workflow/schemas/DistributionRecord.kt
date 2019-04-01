package com.r3.corda.sdk.token.workflow.schemas

import net.corda.core.identity.Party
import net.corda.core.schemas.MappedSchema
import org.hibernate.annotations.Type
import java.util.*
import javax.persistence.Column
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