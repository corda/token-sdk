package com.r3.corda.sdk.token.workflow.flows.evolvable

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.requireThat
import net.corda.core.flows.*
import net.corda.core.node.StatesToRecord
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.unwrap

@InitiatedBy(CreateEvolvableToken::class)
class CreateEvolvableTokenResponder(val otherSession: FlowSession) : FlowLogic<SignedTransaction>() {
    @Suspendable
    override fun call(): SignedTransaction {
        // Receive the notification
        val notification = otherSession.receive<CreateEvolvableToken.Notification>().unwrap { it }

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
        return subFlow(ReceiveFinalityFlow(otherSideSession = otherSession, statesToRecord = StatesToRecord.ONLY_RELEVANT))
    }
}