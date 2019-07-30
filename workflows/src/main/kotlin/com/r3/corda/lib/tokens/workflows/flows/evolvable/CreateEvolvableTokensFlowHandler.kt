package com.r3.corda.lib.tokens.workflows.flows.evolvable

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.lib.tokens.workflows.internal.flows.finality.ObserverAwareFinalityFlowHandler
import net.corda.core.contracts.requireThat
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.flows.SignTransactionFlow
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.unwrap

class CreateEvolvableTokensFlowHandler(val otherSession: FlowSession) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        // Receive the notification
        val notification = otherSession.receive<CreateEvolvableTokensFlow.Notification>().unwrap { it }

        // Sign the transaction proposal, if required
        if (notification.signatureRequired) {
            val signTransactionFlow = object : SignTransactionFlow(otherSession) {
                override fun checkTransaction(stx: SignedTransaction) = requireThat {
                    // TODO
                }
            }
            subFlow(signTransactionFlow)
        }

        // Resolve the creation transaction.
        return subFlow(ObserverAwareFinalityFlowHandler(otherSession))
    }
}