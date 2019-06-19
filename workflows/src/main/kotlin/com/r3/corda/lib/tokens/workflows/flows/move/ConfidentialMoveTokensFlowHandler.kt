package com.r3.corda.lib.tokens.workflows.flows.move

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.lib.tokens.workflows.flows.confidential.ConfidentialTokensFlowHandler
import com.r3.corda.lib.tokens.workflows.flows.finality.ObserverAwareFinalityFlowHandler
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession

/**
 * Responder flow to confidential move tokens flows: [ConfidentialMoveNonFungibleTokensFlow] and
 * [ConfidentialMoveFungibleTokensFlow].
 */
class ConfidentialMoveTokensFlowHandler(val otherSession: FlowSession) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        subFlow(ConfidentialTokensFlowHandler(otherSession))
        subFlow(ObserverAwareFinalityFlowHandler(otherSession))
    }
}
