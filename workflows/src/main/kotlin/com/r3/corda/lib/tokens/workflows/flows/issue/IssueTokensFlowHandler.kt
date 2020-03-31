package com.r3.corda.lib.tokens.workflows.flows.issue

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.lib.tokens.workflows.internal.flows.finality.ObserverAwareFinalityFlowHandler
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession

/**
 * The in-line flow handler for [IssueTokensFlow].
 */
class IssueTokensFlowHandler(val otherSession: FlowSession) : FlowLogic<Unit>() {
	@Suspendable
	override fun call() {
		if (!serviceHub.myInfo.isLegalIdentity(otherSession.counterparty)) {
			subFlow(ObserverAwareFinalityFlowHandler(otherSession))
		}
	}
}