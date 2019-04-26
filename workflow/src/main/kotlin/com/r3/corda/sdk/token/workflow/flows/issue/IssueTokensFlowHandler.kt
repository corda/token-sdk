package com.r3.corda.sdk.token.workflow.flows.issue

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.sdk.token.workflow.flows.ObserverAwareFinalityFlowHandler
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.flows.InitiatedBy

//TODO @InitiatedBy(MakeIssueTokensFlow::class)
@InitiatedBy(IssueTokensFlow::class)
class IssueTokensFlowHandler(val otherSession: FlowSession) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        // Transaction observers - those which are not participants in any of the output states - must invoke
        // FinalityFlow with StatesToRecord set to ALL_VISIBLE, otherwise they will not store any of the states. This
        // does mean that there is an "all or nothing" approach to storing outputs, so if there are privacy concerns,
        // then it is best to split state issuance up for different parties in separate flow invocations.
        subFlow(ObserverAwareFinalityFlowHandler(otherSession))
    }
}