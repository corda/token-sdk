package com.r3.corda.lib.tokens.workflows.internal.flows.confidential

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.lib.ci.ProvideKeyFlow
import com.r3.corda.lib.ci.RequestKeyResponder
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.utilities.unwrap

class AnonymisePartiesFlowHandler(val otherSession: FlowSession) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        val action = otherSession.receive<ActionRequest>().unwrap { it }
        if (action == ActionRequest.CREATE_NEW_KEY) {
            subFlow(ProvideKeyFlow(otherSession))
        }
    }
}