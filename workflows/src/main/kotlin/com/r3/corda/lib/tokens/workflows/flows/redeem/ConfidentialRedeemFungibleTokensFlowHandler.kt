package com.r3.corda.lib.tokens.workflows.flows.redeem

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession

/**
 * Responder flow to [ConfidentialRedeemFungibleTokensFlow].
 */
class ConfidentialRedeemFungibleTokensFlowHandler(val otherSession: FlowSession) : FlowLogic<Unit>() {
	@Suspendable
	override fun call() {
		subFlow(RedeemTokensFlowHandler(otherSession))
	}
}