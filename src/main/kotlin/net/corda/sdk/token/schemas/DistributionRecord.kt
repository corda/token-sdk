package net.corda.sdk.token.schemas

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
@Table(name = "distribution_records")
class DistributionRecord(
        @Id @GeneratedValue @Column(name = "id", unique = true, nullable = false) val id: Long,
        @Column(name = "linear_id") var linearId: UUID,
        @Column(name = "party") var party: Party
) {
    constructor(linearId: UUID, party: Party) : this(0, linearId, party)
}