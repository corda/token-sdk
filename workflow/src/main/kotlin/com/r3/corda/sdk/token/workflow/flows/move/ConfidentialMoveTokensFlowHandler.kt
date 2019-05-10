package com.r3.corda.sdk.token.workflow.flows.move

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.sdk.token.workflow.flows.confidential.AnonymisePartiesFlowHandler
import com.r3.corda.sdk.token.workflow.flows.finality.FinalizeTokensTransactionFlowHandler
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.flows.InitiatedBy

@InitiatedBy(ConfidentialMoveTokensFlow::class)
class ConfidentialMoveTokensFlowHandler(val otherSession: FlowSession) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        subFlow(AnonymisePartiesFlowHandler(otherSession))
        subFlow(FinalizeTokensTransactionFlowHandler(otherSession))
    }
}
