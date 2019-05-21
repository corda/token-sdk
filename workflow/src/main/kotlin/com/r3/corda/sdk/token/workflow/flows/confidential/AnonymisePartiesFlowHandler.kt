package com.r3.corda.sdk.token.workflow.flows.confidential

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.sdk.token.workflow.flows.internal.confidential.ActionRequest
import com.r3.corda.sdk.token.workflow.flows.internal.confidential.RequestConfidentialIdentityFlowHandler
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.utilities.unwrap

class AnonymisePartiesFlowHandler(val otherSession: FlowSession) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        val action = otherSession.receive<ActionRequest>().unwrap { it }
        if (action == ActionRequest.CREATE_NEW_KEY) {
            subFlow(RequestConfidentialIdentityFlowHandler(otherSession))
        }
    }
}