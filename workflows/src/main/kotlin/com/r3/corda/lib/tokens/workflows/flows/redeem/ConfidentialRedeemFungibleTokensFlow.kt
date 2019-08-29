package com.r3.corda.lib.tokens.workflows.flows.redeem

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.lib.ci.ProvideKeyFlow
import com.r3.corda.lib.tokens.contracts.types.TokenType
import com.r3.corda.lib.tokens.workflows.internal.flows.finality.TransactionRole
import net.corda.core.contracts.Amount
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.transactions.SignedTransaction

/**
 * Version of [RedeemFungibleTokensFlow] using confidential identity for a change owner.
 * There is no [NonFungibleToken] version of this flow, because there is no output paid.
 * Identities are synchronised during normal redeem call.
 *
 * @param amount amount of token to redeem
 * @param issuerSession session with the issuer tokens should be redeemed with
 * @param observerSessions optional sessions with the observer nodes, to witch the transaction will be broadcasted
 * @param additionalQueryCriteria additional criteria for token selection
 */
class ConfidentialRedeemFungibleTokensFlow
@JvmOverloads
constructor(
        val amount: Amount<TokenType>,
        val issuerSession: FlowSession,
        val observerSessions: List<FlowSession> = emptyList(),
        val additionalQueryCriteria: QueryCriteria? = null
) : FlowLogic<SignedTransaction>() {
    @Suspendable
    override fun call(): SignedTransaction {
        // Send anonymous identity to the issuer.
        issuerSession.send(TransactionRole.PARTICIPANT)
        observerSessions.forEach { it.send(TransactionRole.OBSERVER) }
        val changeOwner = subFlow(ProvideKeyFlow(issuerSession))
        return subFlow(RedeemFungibleTokensFlow(
                amount = amount,
                issuerSession = issuerSession,
                changeHolder = changeOwner,
                observerSessions = observerSessions,
                additionalQueryCriteria = additionalQueryCriteria
        ))
    }
}