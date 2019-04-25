package com.r3.corda.sdk.token.contracts

import com.r3.corda.sdk.token.contracts.commands.Create
import com.r3.corda.sdk.token.contracts.commands.EvolvableTokenTypeCommand
import com.r3.corda.sdk.token.contracts.commands.Update
import com.r3.corda.sdk.token.contracts.states.EvolvableTokenType
import com.r3.corda.sdk.token.contracts.utilities.singleInput
import com.r3.corda.sdk.token.contracts.utilities.singleOutput
import net.corda.core.contracts.Contract
import net.corda.core.contracts.requireSingleCommand
import net.corda.core.identity.AbstractParty
import net.corda.core.transactions.LedgerTransaction

/**
 * When developers implement contracts for their own token types, they should sub-class this abstract class. It contains
 * some common logic applicable to most, if not all, evolvable tokens, around creation, updates and deletion of tokens.
 */
abstract class EvolvableTokenContract : Contract {

    override fun verify(tx: LedgerTransaction) {
        // Only allow a single command.
        require(tx.commandsOfType<EvolvableTokenTypeCommand>().size == 1) {
            "Evolvable token transactions support exactly one command only."
        }
        // Dispatch based on command.
        val command = tx.commands.requireSingleCommand<EvolvableTokenTypeCommand>()
        when (command.value) {
            is Create -> verifyCreate(tx)
            is Update -> verifyUpdate(tx)
        }
    }

    /** For CorDapp developers to implement. */
    abstract fun additionalCreateChecks(tx: LedgerTransaction)

    /** For CorDapp developers to implement. */
    abstract fun additionalUpdateChecks(tx: LedgerTransaction)

    private fun verifyCreate(tx: LedgerTransaction) {
        // Check commands.
        val command = tx.commands.requireSingleCommand<Create>()

        // Check inputs.
        require(tx.inputs.isEmpty()) { "Create evolvable token transactions must not contain any inputs." }

        // Check outputs.
        require(tx.outputs.size == 1) { "Create evolvable token transactions must contain exactly one output." }
        val output = tx.singleOutput<EvolvableTokenType>()

        // Normalise participants and maintainers for ease of reference.
        val participants = output.participants.toSet()
        val maintainers = output.maintainers.toSet()
        val maintainerKeys = maintainers.map(AbstractParty::owningKey).toSet()

        // Check participants.
        require(participants.containsAll(maintainers)) { "All evolvable token maintainers must also be participants." }

        // Check signatures.
        require(command.signers.toSet().containsAll(maintainerKeys)) {
            "All evolvable token maintainers must sign the create evolvable token transaction."
        }
        require(command.signers.toSet() == maintainerKeys) {
            "Only evolvable token maintainers may sign the create evolvable token transaction."
        }

        // Perform additional checks as implemented by subclasses.
        additionalCreateChecks(tx)
    }

    private fun verifyUpdate(tx: LedgerTransaction) {
        // Check commands.
        val command = tx.commands.requireSingleCommand<Update>()

        // Check inputs.
        require(tx.inputs.size == 1) { "Update evolvable token transactions must contain exactly one input." }
        val input = tx.singleInput<EvolvableTokenType>()

        // Check participants.
        require(input.participants.containsAll(input.maintainers)) {
            "All evolvable token maintainers must also be participants."
        }

        // Check outputs.
        require(tx.outputs.size == 1) { "Update evolvable token transactions must contain exactly one output." }
        val output = tx.singleOutput<EvolvableTokenType>()

        // Check participants.
        require(output.participants.containsAll(output.maintainers)) {
            "All evolvable token maintainers must also be participants."
        }

        // Normalise participants and maintainers for ease of reference.
        val maintainers = (input.maintainers + output.maintainers).toSet()
        val maintainerKeys = maintainers.map(AbstractParty::owningKey).toSet()

        // Check signatures.
        require(command.signers.toSet().containsAll(maintainerKeys)) {
            "All evolvable token maintainers (from inputs and outputs) must sign the update evolvable token transaction."
        }
        require(command.signers.toSet() == maintainerKeys) {
            "Only evolvable token maintainers (from inputs and outputs) may sign the update evolvable token transaction."
        }

        // Verify linear IDs.
        require(input.linearId == output.linearId) {
            "The Linear ID of the evolvable token cannot change during an update."
        }

        // TODO: Determine whether displayTokenSize should be updatable?

        // Perform additional checks as implemented by subclasses.
        additionalUpdateChecks(tx)
    }

}