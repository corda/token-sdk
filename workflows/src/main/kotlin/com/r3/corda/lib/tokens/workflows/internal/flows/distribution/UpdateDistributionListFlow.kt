package com.r3.corda.lib.tokens.workflows.internal.flows.distribution

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.lib.tokens.contracts.commands.IssueTokenCommand
import com.r3.corda.lib.tokens.contracts.commands.MoveTokenCommand
import com.r3.corda.lib.tokens.contracts.states.AbstractToken
import com.r3.corda.lib.tokens.contracts.types.TokenPointer
import net.corda.core.contracts.Command
import net.corda.core.contracts.TransactionState
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.InitiatingFlow
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.ProgressTracker

// TODO: Handle updates of the distribution list for observers.
@InitiatingFlow
class UpdateDistributionListFlow(val signedTransaction: SignedTransaction) : FlowLogic<Unit>() {

    companion object {
        object ADD_DIST_LIST : ProgressTracker.Step("Adding to distribution list.")
        object UPDATE_DIST_LIST : ProgressTracker.Step("Updating distribution list.")

        fun tracker() = ProgressTracker(ADD_DIST_LIST, UPDATE_DIST_LIST)
    }

    override val progressTracker: ProgressTracker = tracker()

    @Suspendable
    override fun call() {
        val tx = signedTransaction.tx
        val tokensWithTokenPointers: List<AbstractToken> = tx.outputs
                .map(TransactionState<*>::data)
                .filterIsInstance<AbstractToken>()
                .filter { it.tokenType is TokenPointer<*> } // IntelliJ bug?? Check is not always true!
        // There are no evolvable tokens so we don't need to update any distribution lists. Otherwise, carry on.
        if (tokensWithTokenPointers.isEmpty()) return
        val issueCmds: List<IssueTokenCommand> = tx.commands
                .map(Command<*>::value)
                .filterIsInstance<IssueTokenCommand>()
                .filter { it.token.tokenType is TokenPointer<*> }
        val moveCmds: List<MoveTokenCommand> = tx.commands
                .map(Command<*>::value)
                .filterIsInstance<MoveTokenCommand>()
                .filter { it.token.tokenType is TokenPointer<*> }
        if (issueCmds.isNotEmpty()) {
            // If it's an issue transaction then the party calling this flow will be the issuer and they just need to
            // update their local distribution list with the parties that have been just issued tokens.
            val issueTypes: List<TokenPointer<*>> = issueCmds.map { it.token.tokenType }.mapNotNull { it as? TokenPointer<*> }
            progressTracker.currentStep = ADD_DIST_LIST
            val issueStates: List<AbstractToken> = tokensWithTokenPointers.filter {
                it.tokenType in issueTypes
            }
            addToDistributionList(issueStates)
        }
        if (moveCmds.isNotEmpty()) {
            // If it's a move then we need to call back to the issuer to update the distribution lists with the new
            // token holders.
            val moveTypes = moveCmds.map { it.token.tokenType }
            progressTracker.currentStep = UPDATE_DIST_LIST
            val moveStates = tokensWithTokenPointers.filter { it.tokenType in moveTypes }
            updateDistributionList(moveStates)
        }
    }
}