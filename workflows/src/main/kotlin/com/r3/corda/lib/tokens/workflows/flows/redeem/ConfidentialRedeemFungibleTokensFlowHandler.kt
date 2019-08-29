package com.r3.corda.lib.tokens.workflows.flows.redeem

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.lib.ci.RequestKeyFlow
import com.r3.corda.lib.tokens.workflows.internal.flows.finality.TransactionRole
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.utilities.unwrap

/**
 * Responder flow to [ConfidentialRedeemFungibleTokensFlow].
 */
class ConfidentialRedeemFungibleTokensFlowHandler(val otherSession: FlowSession) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        val role = otherSession.receive<TransactionRole>().unwrap { it }
        if (role == TransactionRole.PARTICIPANT) {
            subFlow(RequestKeyFlow(otherSession))
        }
        // Perform checks that the change owner is well known and belongs to the party that inititated the flow
        subFlow(RedeemTokensFlowHandler(otherSession))
    }
}