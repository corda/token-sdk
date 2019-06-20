package com.r3.corda.lib.tokens.workflows.flows.move

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.lib.tokens.workflows.internal.flows.finality.ObserverAwareFinalityFlowHandler
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession

/**
 * Responder flow for [MoveTokensFlow], [MoveFungibleTokensFlow], [MoveNonFungibleTokensFlow]
 */
class MoveTokensFlowHandler(val otherSession: FlowSession) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        // Resolve the move transaction.
        if (!serviceHub.myInfo.isLegalIdentity(otherSession.counterparty)) {
            subFlow(ObserverAwareFinalityFlowHandler(otherSession))
        }
    }
}