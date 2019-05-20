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

abstract class AbstractMoveTokensFlow : FlowLogic<SignedTransaction>() {

    abstract val participantSessions: List<FlowSession>
    abstract val observerSessions: List<FlowSession>

    companion object {
        object GENERATE : ProgressTracker.Step("Generating tokensToIssue move.")
        object SIGNING : ProgressTracker.Step("Signing transaction proposal.")
        object RECORDING : ProgressTracker.Step("Recording signed transaction.") {
            override fun childProgressTracker() = FinalityFlow.tracker()
        }

        fun tracker() = ProgressTracker(GENERATE, SIGNING, RECORDING)
    }

    override val progressTracker: ProgressTracker = tracker()

    @Suspendable
    abstract fun addMove(transactionBuilder: TransactionBuilder)

    @Suspendable
    override fun call(): SignedTransaction {
        // Initialise the transaction builder with no notary.
        val transactionBuilder = TransactionBuilder()
        // Add all the specified inputs and outputs to the transaction.
        // The correct commands and signing keys are also added.
        addMove(transactionBuilder)
        // Create new participantSessions if this is started as a top level flow.
        val signedTransaction = subFlow(ObserverAwareFinalityFlow(transactionBuilder, participantSessions + observerSessions))
        // Update the distribution list.
        subFlow(UpdateDistributionListFlow(signedTransaction))
        // Return the newly created transaction.
        return signedTransaction
    }
}
