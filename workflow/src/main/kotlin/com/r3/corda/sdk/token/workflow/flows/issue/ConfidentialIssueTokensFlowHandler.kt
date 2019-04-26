package com.r3.corda.sdk.token.workflow.flows.issue

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.sdk.token.workflow.flows.confidential.RequestConfidentialIdentityFlowHandler
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.flows.InitiatedBy
import net.corda.core.utilities.unwrap

@InitiatedBy(ConfidentialIssueTokensFlow::class)
class ConfidentialIssueTokensFlowHandler(val otherSession: FlowSession) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        val action = otherSession.receive<ActionRequest>().unwrap { it }
        if (action == ActionRequest.ISSUE_NEW_KEY) {
            subFlow(RequestConfidentialIdentityFlowHandler(otherSession))
        }
        subFlow(IssueTokensFlowHandler(otherSession))
    }
}