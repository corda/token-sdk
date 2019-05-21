package com.r3.corda.sdk.token.workflow.flows.confidential

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.sdk.token.workflow.flows.internal.confidential.RequestConfidentialIdentityFlowHandler
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