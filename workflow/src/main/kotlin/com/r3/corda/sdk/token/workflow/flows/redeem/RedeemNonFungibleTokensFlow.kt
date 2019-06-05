package com.r3.corda.sdk.token.workflow.flows.redeem

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.sdk.token.contracts.types.TokenType
import com.r3.corda.sdk.token.workflow.flows.internal.selection.generateExitNonFungible
import com.r3.corda.sdk.token.workflow.utilities.addNotaryWithCheck
import com.r3.corda.sdk.token.workflow.utilities.ownedTokensByTokenIssuer
import net.corda.core.flows.FlowSession
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.transactions.TransactionBuilder

/**
 * Inlined flow used to redeem [NonFungibleToken] [ownedToken] issued by the particular issuer.
 *
 * @param ownedToken non fungible token to redeem
 * @param issuerSession session with the issuer token should be redeemed with
 * @param observerSessions optional sessions with the observer nodes, to witch the transaction will be broadcasted
 */
class RedeemNonFungibleTokensFlow<T : TokenType>(
        val ownedToken: T,
        override val issuerSession: FlowSession,
        override val observerSessions: List<FlowSession>
) : AbstractRedeemTokensFlow() {
    @Suspendable
    override fun generateExit(transactionBuilder: TransactionBuilder) {
        addRedeemTokens(transactionBuilder, ownedToken, issuerSession.counterparty)
    }
}