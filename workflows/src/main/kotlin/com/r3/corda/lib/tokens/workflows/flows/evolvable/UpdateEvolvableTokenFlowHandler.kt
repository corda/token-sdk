package com.r3.corda.lib.tokens.workflows.flows.evolvable

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.lib.tokens.contracts.states.EvolvableTokenType
import com.r3.corda.lib.tokens.workflows.internal.flows.finality.ObserverAwareFinalityFlowHandler
import net.corda.core.contracts.requireThat
import net.corda.core.crypto.SecureHash
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.flows.SignTransactionFlow
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.unwrap

/** In-line counter-flow for [UpdateEvolvableTokenFlow]. */
class UpdateEvolvableTokenFlowHandler(val otherSession: FlowSession) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        // Receive the notification
        val notification = otherSession.receive<UpdateEvolvableTokenFlow.Notification>().unwrap { it }

        var expectedTransactionId: SecureHash? = null
        // Sign the transaction proposal, if required
        if (notification.signatureRequired) {
            val signTransactionFlow = object : SignTransactionFlow(otherSession) {
                override fun checkTransaction(stx: SignedTransaction) = requireThat {
                    val ledgerTransaction = stx.toLedgerTransaction(serviceHub, checkSufficientSignatures = false)
                    val evolvableTokenTypeStateRef = ledgerTransaction.inRefsOfType<EvolvableTokenType>().single()
                    val oldMaintainers = evolvableTokenTypeStateRef.state.data.maintainers
                    require(otherSession.counterparty in oldMaintainers) {
                        "This flow can only be started by existing maintainers of the EvolvableTokenType. However it " +
                                "was started by ${otherSession.counterparty} who is not a maintainer."
                    }
                    expectedTransactionId = stx.id
                }
            }
            subFlow(signTransactionFlow)
        }

        // Resolve the update transaction.
        subFlow(ObserverAwareFinalityFlowHandler(otherSession, expectedTransactionId))
    }
}