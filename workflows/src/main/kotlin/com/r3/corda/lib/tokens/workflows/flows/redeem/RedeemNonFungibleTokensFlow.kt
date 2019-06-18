package com.r3.corda.lib.tokens.workflows.flows.redeem

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.lib.tokens.contracts.types.TokenType
import net.corda.core.flows.FlowSession
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
        addRedeemTokens5(transactionBuilder, ownedToken, issuerSession.counterparty)
    }
}