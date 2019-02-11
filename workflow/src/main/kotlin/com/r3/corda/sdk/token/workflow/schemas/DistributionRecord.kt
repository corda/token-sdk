package com.r3.corda.sdk.token.workflow.schemas

import net.corda.core.identity.Party
import net.corda.core.schemas.MappedSchema
import java.util.*
import javax.persistence.*

object DistributionRecordFamily

object DistributionRecordSchema : MappedSchema(
        schemaFamily = DistributionRecordFamily.javaClass,
        version = 1,
        mappedTypes = listOf(DistributionRecord::class.java)
)

@Entity
@Table(name = "distribution_record", indexes = [Index(name = "dist_record_idx", columnList = "linear_id")])
class DistributionRecord(

        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        var id: Long,

        @Column(name = "linear_id", nullable = false)
        var linearId: UUID,

        @Column(name = "party", nullable = false)
        var party: Party

) {
    constructor(linearId: UUID, party: Party) : this(0, linearId, party)
}