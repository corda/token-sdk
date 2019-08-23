package com.r3.corda.lib.tokens.workflows.flows.redeem

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.lib.ci.SyncKeyMappingFlowHandler
import com.r3.corda.lib.tokens.contracts.states.AbstractToken
import com.r3.corda.lib.tokens.workflows.internal.checkOwner
import com.r3.corda.lib.tokens.workflows.internal.checkSameIssuer
import com.r3.corda.lib.tokens.workflows.internal.checkSameNotary
import com.r3.corda.lib.tokens.workflows.internal.flows.finality.ObserverAwareFinalityFlowHandler
import com.r3.corda.lib.tokens.workflows.internal.flows.finality.TransactionRole
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.flows.SignTransactionFlow
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.unwrap

/**
 * Inlined responder flow called on the issuer side, should be used with: [RedeemFungibleTokensFlow],
 * [RedeemNonFungibleTokensFlow], [RedeemTokensFlow].
 */
// Called on Issuer side.
class RedeemTokensFlowHandler(val otherSession: FlowSession) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        val role = otherSession.receive<TransactionRole>().unwrap { it }
        if (role == TransactionRole.PARTICIPANT) {
            // Synchronise all confidential identities, issuer isn't involved in move transactions, so states holders may
            // not be known to this node.
            subFlow(SyncKeyMappingFlowHandler(otherSession))
            // Perform all the checks to sign the transaction.
            subFlow(object : SignTransactionFlow(otherSession) {
                override fun checkTransaction(stx: SignedTransaction) {
                    val stateAndRefsToRedeem = stx.toLedgerTransaction(serviceHub, false).inRefsOfType<AbstractToken>()
                    checkSameIssuer(stateAndRefsToRedeem, ourIdentity)
                    checkSameNotary(stateAndRefsToRedeem)
                    checkOwner(serviceHub.identityService, stateAndRefsToRedeem, otherSession.counterparty)
                }
            })
        }
        // Call observer aware finality flow handler.
        subFlow(ObserverAwareFinalityFlowHandler(otherSession))
    }
}
