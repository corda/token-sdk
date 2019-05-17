package com.r3.corda.sdk.token.workflow.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.requireThat
import net.corda.core.flows.*
import net.corda.core.node.StatesToRecord
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.ProgressTracker
import net.corda.core.utilities.unwrap

@InitiatedBy(UpdateEvolvableToken::class)
class UpdateEvolvableTokenResponder(val otherSession: FlowSession) : FlowLogic<SignedTransaction>() {

    companion object {
        object PREPARING : ProgressTracker.Step("Preparing for evolvable token update transaction.")

        object SIGNING : ProgressTracker.Step("Signing transaction proposal.") {
            override fun childProgressTracker() = SignTransactionFlow.tracker()
        }

        object RECORDING : ProgressTracker.Step("Recording signed transaction.")

        fun tracker() = ProgressTracker(PREPARING, SIGNING, RECORDING)
    }

    override val progressTracker: ProgressTracker = tracker()

    @Suspendable
    override fun call(): SignedTransaction {
        // Receive the notification
        progressTracker.currentStep = PREPARING
        val notification = otherSession.receive<UpdateEvolvableToken.Notification>().unwrap { it }

        // Sign the transaction proposal, if required
        if (notification.signatureRequired) {
            progressTracker.currentStep = SIGNING
            val signTransactionFlow = object : SignTransactionFlow(otherSession, progressTracker = SIGNING.childProgressTracker()) {
                override fun checkTransaction(stx: SignedTransaction) = requireThat {
                    // TODO: Add any appropriate verification checks; in the default flow, no checks required.
                }
            }
            subFlow(signTransactionFlow)
        }

        // Resolve the creation transaction.
        progressTracker.currentStep = RECORDING
        return subFlow(ReceiveFinalityFlow(otherSideSession = otherSession, statesToRecord = StatesToRecord.ALL_VISIBLE))
    }
}