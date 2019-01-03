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
@IdClass(DistributionRecordId::class)
@Table(name = "distribution_records")
class DistributionRecord(
        @Id @Column(name = "linearId") var linearId: UUID,
        @Id @Column(name = "party") var party: Party
)

data class DistributionRecordId(var linearId: UUID, var party: Party)