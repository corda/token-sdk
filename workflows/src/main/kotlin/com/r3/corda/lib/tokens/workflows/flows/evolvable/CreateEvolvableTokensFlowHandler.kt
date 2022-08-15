package com.r3.corda.lib.tokens.workflows.flows.evolvable

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.lib.tokens.workflows.internal.flows.finality.ObserverAwareFinalityFlowHandler
import net.corda.core.contracts.requireThat
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.flows.SignTransactionFlow
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.unwrap

/** In-line counter-flow to [CreateEvolvableTokensFlow]. */
class CreateEvolvableTokensFlowHandler(val otherSession: FlowSession) : FlowLogic<Unit>() {
	@Suspendable
	override fun call() {
		val ourKeys = serviceHub.keyManagementService.filterMyKeys(serviceHub.keyManagementService.keys)

		// Receive the notification
		val notification = otherSession.receive<CreateEvolvableTokensFlow.Notification>().unwrap { it }

		// Sign the transaction proposal, if required
		if (notification.signatureRequired) {
			val signTransactionFlow = object : SignTransactionFlow(otherSession) {
				override fun checkTransaction(stx: SignedTransaction) = requireThat {
					require(stx.getMissingSigners().any { it in ourKeys }) {
						"Our node was asked to sign this transaction '${stx.id} but our signature is not required."
					}
				}
			}
			subFlow(signTransactionFlow)
		}

		// Resolve the creation transaction.
		subFlow(ObserverAwareFinalityFlowHandler(otherSession))
	}
}