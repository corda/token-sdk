package com.r3.corda.lib.tokens.workflows.flows.redeem

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.lib.tokens.workflows.flows.internal.confidential.RequestConfidentialIdentityFlow
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession

/**
 * Responder flow to [ConfidentialRedeemFungibleTokensFlow].
 */
class ConfidentialRedeemFungibleTokensFlowHandler(val otherSession: FlowSession) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        subFlow(RequestConfidentialIdentityFlow(otherSession))
        // Perform checks that the change owner is well known and belongs to the party that inititated the flow
        subFlow(RedeemTokensFlowHandler(otherSession))
    }
}