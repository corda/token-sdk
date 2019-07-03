package com.r3.corda.lib.tokens.workflows.flows.redeem

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.lib.tokens.contracts.types.TokenType
import net.corda.core.contracts.Amount
import net.corda.core.flows.FlowSession
import net.corda.core.identity.AbstractParty
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.transactions.TransactionBuilder

/**
 * Inlined flow used to redeem amount of [FungibleToken]s issued by the particular issuer with possible change output
 * paid to the [changeOwner].
 *
 * @param amount amount of token to redeem
 * @param changeOwner owner of possible change output, which defaults to the node identity of the calling node
 * @param issuerSession session with the issuer tokens should be redeemed with
 * @param observerSessions optional sessions with the observer nodes, to witch the transaction will be broadcasted
 * @param additionalQueryCriteria additional criteria for token selection
 */
class RedeemFungibleTokensFlow<T : TokenType>
@JvmOverloads
constructor(
        val amount: Amount<T>,
        override val issuerSession: FlowSession,
        val changeOwner: AbstractParty? = null,
        override val observerSessions: List<FlowSession> = emptyList(),
        val additionalQueryCriteria: QueryCriteria? = null
) : AbstractRedeemTokensFlow() {
    @Suspendable
    override fun generateExit(transactionBuilder: TransactionBuilder) {
        addFungibleTokensToRedeem(
                transactionBuilder = transactionBuilder,
                serviceHub = serviceHub,
                amount = amount,
                issuer = issuerSession.counterparty,
                changeOwner = changeOwner ?: ourIdentity,
                additionalQueryCriteria = additionalQueryCriteria
        )
    }
}