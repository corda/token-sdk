package com.r3.corda.sdk.token.contracts

import com.r3.corda.sdk.token.contracts.commands.Create
import com.r3.corda.sdk.token.contracts.commands.EvolvableTokenTypeCommand
import com.r3.corda.sdk.token.contracts.commands.Update
import com.r3.corda.sdk.token.contracts.states.EvolvableTokenType
import com.r3.corda.sdk.token.contracts.utilities.singleInput
import com.r3.corda.sdk.token.contracts.utilities.singleOutput
import net.corda.core.contracts.Contract
import net.corda.core.contracts.requireSingleCommand
import net.corda.core.transactions.LedgerTransaction

/**
 * When developers implement contracts for their own token types, they should sub-class this abstract class. It contains
 * some common logic applicable to most, if not all, evolvable tokens, around creation, updates and deletion of tokens.
 */
abstract class EvolvableTokenContract : Contract {

    override fun verify(tx: LedgerTransaction) {
        val command = tx.commands.requireSingleCommand<EvolvableTokenTypeCommand>()
        when (command.value) {
            is Create -> handleCreate(tx)
            is Update -> handleUpdate(tx)
        }
    }

    /** For CorDapp developers to implement. */
    abstract fun additionalCreateChecks(tx: LedgerTransaction)

    /** For CorDapp developers to implement. */
    abstract fun additionalUpdateChecks(tx: LedgerTransaction)

    private fun handleCreate(tx: LedgerTransaction) {
        // Check commands
        val command = tx.commands.requireSingleCommand<Create>()

        // Check inputs
        require(tx.inputs.isEmpty()) { "Create evolvable token transactions must not contain any inputs." }

        // Check outputs
        require(tx.outputs.size == 1) { "Create evolvable token transactions must contain exactly one output." }
        val token = tx.singleOutput<EvolvableTokenType>()
        require(token.participants.toSet().containsAll(token.maintainers.toSet())) {
            "All evolvable token maintainers must also be participants."
        }

        // Check signatures
        val maintainerKeys = token.maintainers.map { it.owningKey }
        require(command.signers.containsAll(maintainerKeys.toSet())) {
            "All evolvable token maintainers must sign the create evolvable token transaction."
        }
        require(command.signers.toSet() == maintainerKeys.toSet()) {
            "Only evolvable token maintainers may sign the create evolvable token transaction."
        }

        // Perform additional checks as implemented by subclasses
        additionalCreateChecks(tx)
    }

    private fun handleUpdate(tx: LedgerTransaction) {
        require(tx.inputs.size == 1) { "Update evolvable token transactions may only contain one input." }
        require(tx.outputs.size == 1) { "Update evolvable token transactions may only contain one output." }
        val input = tx.singleInput<EvolvableTokenType>()
        val output = tx.singleOutput<EvolvableTokenType>()
        val command = tx.commands.requireSingleCommand<Update>()
        require(input.linearId == output.linearId) { "The linear ID cannot change." }
        val maintainers = output.maintainers + input.maintainers
        require(command.signers.toSet() == maintainers.map { it.owningKey }.toSet()) {
            "The old and new maintainers all must sign the update evolvable token transaction."
        }
        require(output.participants.toSet().containsAll(output.maintainers.toSet())) {
            "All token maintainers must also be participants."
        }
        additionalUpdateChecks(tx)
    }

}