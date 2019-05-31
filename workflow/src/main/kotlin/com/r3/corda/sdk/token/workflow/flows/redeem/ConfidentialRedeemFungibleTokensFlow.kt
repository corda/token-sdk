package com.r3.corda.sdk.token.workflow.flows.redeem

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.sdk.token.contracts.types.TokenType
import com.r3.corda.sdk.token.workflow.flows.internal.confidential.RequestConfidentialIdentityFlowHandler
import net.corda.core.contracts.Amount
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.identity.Party
import net.corda.core.transactions.SignedTransaction

class ConfidentialRedeemFungibleTokensFlow<T : TokenType>
@JvmOverloads
constructor(
        val amount: Amount<T>,
        val issuer: Party,
        val issuerSession: FlowSession,
        val observerSessions: List<FlowSession> = emptyList()
) : FlowLogic<SignedTransaction>() {
    @Suspendable
    override fun call(): SignedTransaction {
        // Send anonymous identity to the issuer.
        val changeOwner = subFlow(RequestConfidentialIdentityFlowHandler(issuerSession))
        return subFlow(RedeemFungibleTokensFlow(amount, issuer, changeOwner, issuerSession, observerSessions))
    }
}