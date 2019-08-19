package com.r3.corda.lib.tokens.workflows.flows.redeem

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.lib.tokens.contracts.types.TokenType
import com.r3.corda.lib.tokens.workflows.internal.selection.TokenQueryBy
import net.corda.core.contracts.Amount
import net.corda.core.flows.FlowSession
import net.corda.core.identity.AbstractParty
import net.corda.core.transactions.TransactionBuilder

/**
 * Inlined flow used to redeem amount of [FungibleToken]s issued by the particular issuer with possible change output
 * paid to the [changeHolder].
 *
 * @param amount amount of token to redeem
 * @param changeHolder owner of possible change output, which defaults to the node identity of the calling node
 * @param issuerSession session with the issuer tokens should be redeemed with
 * @param observerSessions optional sessions with the observer nodes, to witch the transaction will be broadcasted
 * @param queryBy additional criteria for token selection, see [TokenQueryBy]
 */
class RedeemFungibleTokensFlow
@JvmOverloads
constructor(
        val amount: Amount<TokenType>,
        override val issuerSession: FlowSession,
        val changeHolder: AbstractParty? = null,
        override val observerSessions: List<FlowSession> = emptyList(),
        val queryBy: TokenQueryBy? = null
) : AbstractRedeemTokensFlow() {
    @Suspendable
    override fun generateExit(transactionBuilder: TransactionBuilder) {
        val issuer = issuerSession.counterparty
        if(queryBy?.issuer != null && queryBy.issuer != issuer) {
            throw IllegalArgumentException("Redeeming tokens issued by: ${queryBy.issuer} with issuer: $issuer")
        }
        addFungibleTokensToRedeem(
                transactionBuilder = transactionBuilder,
                serviceHub = serviceHub,
                amount = amount,
                changeOwner = changeHolder ?: ourIdentity,
                queryBy = queryBy?.copy(issuer = issuer) ?: TokenQueryBy(issuer = issuer)
        )
    }
}