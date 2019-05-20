package com.r3.corda.sdk.token.workflow.flows.move

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.sdk.token.workflow.flows.finality.ObserverAwareFinalityFlowHandler
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession

class MoveTokensFlowHandler(val otherSession: FlowSession) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        // Resolve the move transaction.
        if (!serviceHub.myInfo.isLegalIdentity(otherSession.counterparty)) {
            subFlow(ObserverAwareFinalityFlowHandler(otherSession))
        }
    }
}