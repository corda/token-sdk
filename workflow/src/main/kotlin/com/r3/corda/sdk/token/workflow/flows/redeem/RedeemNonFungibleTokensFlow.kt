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
 * TODO docs
 */
class RedeemNonFungibleTokensFlow<T : TokenType>(
        val ownedToken: T,
        val issuer: Party,
        override val issuerSession: FlowSession,
        override val observerSessions: List<FlowSession>
) : AbstractRedeemTokensFlow() {
    @Suspendable
    override fun generateExit(transactionBuilder: TransactionBuilder): TransactionBuilder {
        return addRedeemTokens(transactionBuilder, ownedToken, issuer)
    }
}