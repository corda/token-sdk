package com.r3.corda.sdk.token.workflow.flows.move

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.sdk.token.workflow.flows.finality.ObserverAwareFinalityFlow
import com.r3.corda.sdk.token.workflow.flows.internal.distribution.UpdateDistributionListFlow
import net.corda.core.flows.FinalityFlow
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker

/**
 * Abstract class for the move tokens flows family.
 * You must provide [participantSessions] and optional [observerSessions] for finalization. Override [addMove] to select
 * tokens to move. See helper functions in [MoveTokensUtilities] module.
 * The flow performs basic tasks, generates move transaction proposal for all the participants, collects signatures and
 * finalises transaction with observers if present.
 */
abstract class AbstractMoveTokensFlow : FlowLogic<SignedTransaction>() {
    abstract val participantSessions: List<FlowSession>
    abstract val observerSessions: List<FlowSession>

    companion object {
        object GENERATE : ProgressTracker.Step("Generating tokens to move.")
        object RECORDING : ProgressTracker.Step("Recording signed transaction.") {
            override fun childProgressTracker() = FinalityFlow.tracker()
        }

        object UPDATING : ProgressTracker.Step("Updating data distribution list.")

        fun tracker() = ProgressTracker(GENERATE, RECORDING, UPDATING)
    }

    override val progressTracker: ProgressTracker = tracker()

    /**
     * Add move of tokens to the [transactionBuilder]. Modifies the builder.
     */
    @Suspendable
    abstract fun addMove(transactionBuilder: TransactionBuilder)

    @Suspendable
    override fun call(): SignedTransaction {
        // Initialise the transaction builder with no notary.
        val transactionBuilder = TransactionBuilder()
        progressTracker.currentStep = GENERATE
        // Add all the specified inputs and outputs to the transaction.
        // The correct commands and signing keys are also added.
        addMove(transactionBuilder)
        progressTracker.currentStep = RECORDING
        // Create new participantSessions if this is started as a top level flow.
        val signedTransaction = subFlow(ObserverAwareFinalityFlow(transactionBuilder, participantSessions + observerSessions))
        progressTracker.currentStep = UPDATING
        // Update the distribution list.
        subFlow(UpdateDistributionListFlow(signedTransaction))
        // Return the newly created transaction.
        return signedTransaction
    }
}
