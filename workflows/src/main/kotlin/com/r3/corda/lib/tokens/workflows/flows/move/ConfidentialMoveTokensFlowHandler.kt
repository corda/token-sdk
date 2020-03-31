package com.r3.corda.lib.tokens.workflows.flows.move

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.lib.tokens.workflows.flows.confidential.ConfidentialTokensFlowHandler
import com.r3.corda.lib.tokens.workflows.internal.flows.finality.ObserverAwareFinalityFlowHandler
import com.r3.corda.lib.tokens.workflows.internal.flows.finality.TransactionRole
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.utilities.unwrap

/**
 * Responder flow to confidential move tokens flows: [ConfidentialMoveNonFungibleTokensFlow] and
 * [ConfidentialMoveFungibleTokensFlow].
 */
class ConfidentialMoveTokensFlowHandler(val otherSession: FlowSession) : FlowLogic<Unit>() {
	@Suspendable
	override fun call() {
		val role = otherSession.receive<TransactionRole>().unwrap { it }
		if (role == TransactionRole.PARTICIPANT) {
			subFlow(ConfidentialTokensFlowHandler(otherSession))
		}
		subFlow(ObserverAwareFinalityFlowHandler(otherSession))
	}
}
