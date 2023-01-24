package com.r3.corda.lib.tokens.workflows.flows.redeem

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.lib.ci.workflows.SyncKeyMappingFlowHandler
import com.r3.corda.lib.tokens.contracts.states.AbstractToken
import com.r3.corda.lib.tokens.workflows.internal.checkOwner
import com.r3.corda.lib.tokens.workflows.internal.checkSameIssuer
import com.r3.corda.lib.tokens.workflows.internal.checkSameNotary
import com.r3.corda.lib.tokens.workflows.internal.flows.finality.ObserverAwareFinalityFlowHandler
import com.r3.corda.lib.tokens.workflows.internal.flows.finality.TransactionRole
import net.corda.core.crypto.SecureHash
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
class RedeemTokensFlowHandler(val otherSession: FlowSession) : FlowLogic<SignedTransaction?>() {
    @Suspendable
    override fun call(): SignedTransaction? {
        var expectedTransactionId: SecureHash? = null
        val role = otherSession.receive<TransactionRole>().unwrap { it }
        if (role == TransactionRole.PARTICIPANT) {
            // Synchronise all confidential identities, issuer isn't involved in move transactions, so states holders may
            // not be known to this node.
            subFlow(SyncKeyMappingFlowHandler(otherSession))
            // There is edge case where issuer redeems with themselves, then we need to be careful not to call handler for
            // collect signatures for already fully signed transaction - it causes session messages mismatch.
            if (!serviceHub.myInfo.isLegalIdentity(otherSession.counterparty)) {
                // Perform all the checks to sign the transaction.
                subFlow(object : SignTransactionFlow(otherSession) {
                    // TODO if it is with itself, then we won't perform that check...
                    override fun checkTransaction(stx: SignedTransaction) {
                        val stateAndRefsToRedeem = stx.toLedgerTransaction(serviceHub, false).inRefsOfType<AbstractToken>()
                        checkSameIssuer(stateAndRefsToRedeem, ourIdentity)
                        checkSameNotary(stateAndRefsToRedeem)
                        checkOwner(serviceHub.identityService, stateAndRefsToRedeem, otherSession.counterparty)
                        expectedTransactionId = stx.id
                    }
                })
            }
        }
        return if (!serviceHub.myInfo.isLegalIdentity(otherSession.counterparty)) {
            // Call observer aware finality flow handler.
            subFlow(ObserverAwareFinalityFlowHandler(otherSession, expectedTransactionId))
        } else null
    }
}
