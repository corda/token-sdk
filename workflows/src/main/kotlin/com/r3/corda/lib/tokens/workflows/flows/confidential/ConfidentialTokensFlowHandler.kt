package com.r3.corda.lib.tokens.workflows.flows.confidential

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.lib.tokens.workflows.internal.flows.confidential.AnonymisePartiesFlowHandler
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession

/**
 * Use of this flow should be paired with [ConfidentialTokensFlow]. If asked to do so, this flow begins the generation of
 * a new key pair by calling [RequestConfidentialIdentityFlowHandler].
 */
class ConfidentialTokensFlowHandler(val otherSession: FlowSession) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() = subFlow(AnonymisePartiesFlowHandler(otherSession))
}