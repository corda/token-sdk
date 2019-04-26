package com.r3.corda.sdk.token.workflow.flows.move

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.sdk.token.workflow.flows.ObserverAwareFinalityFlowHandler
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.flows.InitiatedBy
import net.corda.core.flows.ReceiveFinalityFlow
import net.corda.core.node.StatesToRecord

@InitiatedBy(FinalizeMoveTokensFlow::class)
class MoveTokensFlowHandler(val otherSession: FlowSession) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        // Resolve the move transaction.
        if (!serviceHub.myInfo.isLegalIdentity(otherSession.counterparty)) {
            subFlow(ObserverAwareFinalityFlowHandler(otherSession))
        }
    }
}