package com.r3.corda.sdk.token.workflow.flows.issue

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.sdk.token.workflow.flows.confidential.ConfidentialTokensFlowHandler
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession

class ConfidentialIssueTokensFlowHandler(val otherSession: FlowSession) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        subFlow(ConfidentialTokensFlowHandler(otherSession))
        subFlow(IssueTokensFlowHandler(otherSession))
    }
}