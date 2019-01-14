package net.corda.sdk.token.schemas

import net.corda.core.identity.Party
import net.corda.core.schemas.MappedSchema
import java.util.*
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Id
import javax.persistence.Table

object DistributionRecordFamily

object DistributionRecordSchema : MappedSchema(
        schemaFamily = DistributionRecordFamily.javaClass,
        version = 1,
        mappedTypes = listOf(DistributionRecord::class.java)
)

@Entity
@Table(name = "distribution_records")
class DistributionRecord(
        @Id
        var id: Long,

        @Column(name = "linear_id", nullable = false)
        var linearId: UUID,

        @Column(name = "party", nullable = false)
        var party: Party
) {
    constructor(linearId: UUID, party: Party) : this(0, linearId, party)
}