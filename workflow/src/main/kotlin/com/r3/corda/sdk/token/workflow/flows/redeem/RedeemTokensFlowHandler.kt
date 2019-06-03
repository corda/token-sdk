package com.r3.corda.sdk.token.workflow.flows.redeem

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.sdk.token.contracts.states.AbstractToken
import com.r3.corda.sdk.token.contracts.types.TokenType
import com.r3.corda.sdk.token.workflow.flows.finality.ObserverAwareFinalityFlowHandler
import com.r3.corda.sdk.token.workflow.flows.internal.checkOwner
import com.r3.corda.sdk.token.workflow.flows.internal.checkSameIssuer
import com.r3.corda.sdk.token.workflow.flows.internal.checkSameNotary
import net.corda.confidential.IdentitySyncFlow
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.flows.InitiatedBy
import net.corda.core.flows.SignTransactionFlow
import net.corda.core.transactions.SignedTransaction

// Called on Issuer side.
@InitiatedBy(RedeemTokensFlow::class)
class RedeemTokensFlowHandler<T : TokenType>(val otherSession: FlowSession) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        // Synchronise all confidential identities, issuer isn't involved in move transactions, so states holders may not be known to this node.
        subFlow(IdentitySyncFlow.Receive(otherSession))
        // Perform all the checks to sign the transaction.
        subFlow(object : SignTransactionFlow(otherSession) {
            override fun checkTransaction(stx: SignedTransaction) {
                val stateAndRefsToRedeem = stx.toLedgerTransaction(serviceHub, false).inRefsOfType<AbstractToken<T>>()
                checkSameIssuer(stateAndRefsToRedeem, ourIdentity)
                checkSameNotary(stateAndRefsToRedeem)
                checkOwner(serviceHub.identityService, stateAndRefsToRedeem, otherSession.counterparty)
            }
        })
        // Call observer aware finality flow handler.
        subFlow(ObserverAwareFinalityFlowHandler(otherSession))
    }
}
