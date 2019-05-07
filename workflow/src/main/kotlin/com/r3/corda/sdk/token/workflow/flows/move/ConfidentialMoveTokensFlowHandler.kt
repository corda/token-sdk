package com.r3.corda.sdk.token.workflow.flows.move

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.sdk.token.workflow.flows.issue.KerfuffleHandler
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.flows.InitiatedBy

@InitiatedBy(ConfidentialMoveTokensFlow::class)
class ConfidentialMoveTokensFlowHandler(val otherSession: FlowSession) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        subFlow(KerfuffleHandler(otherSession))
        subFlow(MoveTokensFlowHandler(otherSession))
    }
}
