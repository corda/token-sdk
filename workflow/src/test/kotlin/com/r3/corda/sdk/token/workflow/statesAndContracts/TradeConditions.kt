package com.r3.corda.sdk.token.workflow.statesAndContracts

import net.corda.core.contracts.*
import net.corda.core.identity.AbstractParty
import net.corda.core.internal.castIfPossible
import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentState
import net.corda.core.schemas.QueryableState
import net.corda.core.serialization.CordaSerializable
import net.corda.core.transactions.LedgerTransaction
import java.util.*
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Table

@BelongsToContract(TradeConditionsContract::class)
data class TradeConditions(val owner: AbstractParty,
                           val conditions: String,
                           override val linearId: UniqueIdentifier) : LinearState, QueryableState {

    override val participants: List<AbstractParty>
        get() = listOf(owner)

    override fun generateMappedObject(schema: MappedSchema): PersistentState {
        return when (schema) {
            is TradeConditionsSchemaV1 -> TradeConditionsSchemaV1.TradeConditions(
                    this.owner,
                    this.conditions,
                    this.linearId.id
            )
            else -> throw IllegalArgumentException("The schema : $schema does not exist.")
        }
    }

    override fun supportedSchemas(): Iterable<MappedSchema> = listOf(TradeConditionsSchemaV1)
}

object TradeConditionsSchema

object TradeConditionsSchemaV1 : MappedSchema(
        schemaFamily = TradeConditionsSchema.javaClass,
        version = 1,
        mappedTypes = listOf(TradeConditions::class.java)
) {
    @Entity
    @Table(name = "trade_conditions")
    class TradeConditions(
            @Column(name = "owner", nullable = false)
            var owner: AbstractParty,

            @Column(name = "conditions", nullable = false)
            var conditions: String,

            @Column(name = "trade_conditions_id")
            var linearId: UUID
    ) : PersistentState()
}


class TradeConditionsContract : Contract {

    companion object {
        @JvmStatic
        val CONTRACT_ID = "com.r3.corda.sdk.token.workflow.statesAndContracts.TradeConditionsContract"
    }

    @CordaSerializable
    interface TradeConditionsCommand : CommandData

    @CordaSerializable
    class TradeCommand : TradeConditionsCommand, TypeOnlyCommandData()


    override fun verify(tx: LedgerTransaction) {

        val command = tx.commands.mapNotNull { (TradeConditionsCommand::class.java).castIfPossible(it.value) }.single()
        when (command) {
            is TradeCommand -> handleTradeCommand(tx)
        }
    }

    private fun handleTradeCommand(tx: LedgerTransaction) {
        val output = tx.outputsOfType<TradeConditions>().single()

        require(output.conditions.isNotEmpty()) {
            "Trade conditions must be non-empty."
        }
    }
}

