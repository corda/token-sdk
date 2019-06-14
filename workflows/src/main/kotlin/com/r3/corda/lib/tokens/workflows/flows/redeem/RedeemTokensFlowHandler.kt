package com.r3.corda.lib.tokens.workflows.flows.redeem

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.lib.tokens.contracts.states.AbstractToken
import com.r3.corda.lib.tokens.contracts.types.TokenType
import com.r3.corda.lib.tokens.workflows.flows.finality.ObserverAwareFinalityFlowHandler
import com.r3.corda.lib.tokens.workflows.internal.checkOwner
import com.r3.corda.lib.tokens.workflows.internal.checkSameIssuer
import com.r3.corda.lib.tokens.workflows.internal.checkSameNotary
import net.corda.confidential.IdentitySyncFlow
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.flows.InitiatedBy
import net.corda.core.flows.SignTransactionFlow
import net.corda.core.transactions.SignedTransaction

/**
 * Inlined responder flow called on the issuer side, should be used with: [RedeemFungibleTokensFlow],
 * [RedeemNonFungibleTokensFlow], [RedeemTokensFlow].
 */
// Called on Issuer side.
@InitiatedBy(RedeemTokensFlow::class)
class RedeemTokensFlowHandler(val otherSession: FlowSession) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        // Synchronise all confidential identities, issuer isn't involved in move transactions, so states holders may
        // not be known to this node.
        subFlow(IdentitySyncFlow.Receive(otherSession))
        // Perform all the checks to sign the transaction.
        subFlow(object : SignTransactionFlow(otherSession) {
            override fun checkTransaction(stx: SignedTransaction) {
                val stateAndRefsToRedeem = stx.toLedgerTransaction(serviceHub, false).inRefsOfType<AbstractToken<TokenType>>()
                checkSameIssuer(stateAndRefsToRedeem, ourIdentity)
                checkSameNotary(stateAndRefsToRedeem)
                checkOwner(serviceHub.identityService, stateAndRefsToRedeem, otherSession.counterparty)
            }
        })
        // Call observer aware finality flow handler.
        subFlow(ObserverAwareFinalityFlowHandler(otherSession))
    }
}
