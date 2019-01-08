package net.corda.sdk.token.contracts

import net.corda.core.contracts.Contract
import net.corda.core.contracts.requireSingleCommand
import net.corda.core.transactions.LedgerTransaction
import net.corda.sdk.token.commands.TokenCommand
import net.corda.sdk.token.types.EvolvableToken
import net.corda.sdk.token.utilities.singleInput
import net.corda.sdk.token.utilities.singleOutput

/**
 * When developers implement contracts for their own token types, they should sub-class this abstract class. It contains
 * some common logic applicable to most, if not all, evolvable tokens, around creation, updates and deletion of tokens.
 */
abstract class EvolvableTokenContract : Contract {

    override fun verify(tx: LedgerTransaction) {
        val command = tx.commands.requireSingleCommand<TokenCommand>()
        when (command.value) {
            is TokenCommand.Create -> handleCreate(tx)
            is TokenCommand.Update -> handleUpdate(tx)
            is TokenCommand.Delete -> handleDelete(tx)
        }
    }

    /** For CorDapp developers to implement. */
    abstract fun additionalCreateChecks(tx: LedgerTransaction)

    /** For CorDapp developers to implement. */
    abstract fun additionalUpdateChecks(tx: LedgerTransaction)

    /** For CorDapp developers to implement. */
    abstract fun additionalDeleteChecks(tx: LedgerTransaction)

    private fun handleCreate(tx: LedgerTransaction) {
        require(tx.outputs.size == 1) { "Create evolvable token transactions may only contain one output." }
        require(tx.inputs.isEmpty()) { "Create evolvable token transactions must not contain any inputs." }
        val token = tx.singleOutput<EvolvableToken>()
        val command = tx.commands.requireSingleCommand<TokenCommand.Create>()
        require(command.signers.toSet() == token.maintainers.toSet()) {
            "The token maintainers only must sign the create evolvable token transaction."
        }
        additionalCreateChecks(tx)
    }

    private fun handleUpdate(tx: LedgerTransaction) {
        require(tx.inputs.size == 1) { "Update evolvable token transactions may only contain one input." }
        require(tx.outputs.size == 1) { "Update evolvable token transactions may only contain one output." }
        val input = tx.singleInput<EvolvableToken>()
        val output = tx.singleOutput<EvolvableToken>()
        val command = tx.commands.requireSingleCommand<TokenCommand.Update>()
        require(input.linearId != output.linearId) { "The linear ID cannot change." }
        require(command.signers.toSet() == output.maintainers.toSet() + input.maintainers.toSet()) {
            "The old and new maintainers all must sign the update evolvable token transaction."
        }
        additionalUpdateChecks(tx)
    }

    private fun handleDelete(tx: LedgerTransaction) {
        require(tx.inputs.size == 1) { "Delete evolvable token transactions may only contain one input." }
        require(tx.outputs.isEmpty()) { "Delete evolvable token transactions must not contain outputs." }
        val token = tx.singleInput<EvolvableToken>()
        val command = tx.commands.requireSingleCommand<TokenCommand.Delete>()
        require(command.signers.toSet() == token.maintainers.toSet()) {
            "The token maintainers only must sign the delete evolvable token transaction."
        }
        additionalDeleteChecks(tx)
    }

}