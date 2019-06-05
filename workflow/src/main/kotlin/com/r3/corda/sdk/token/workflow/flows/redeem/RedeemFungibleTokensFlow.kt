package com.r3.corda.sdk.token.workflow.flows.redeem

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.sdk.token.contracts.types.TokenType
import net.corda.core.contracts.Amount
import net.corda.core.flows.FlowSession
import net.corda.core.identity.AbstractParty
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.transactions.TransactionBuilder

/**
 * Inlined flow used to redeem amount of [FungibleToken]s issued by the particular issuer with possible change output paid to the [changeOwner].
 *
 * @param amount amount of token to redeem
 * @param changeOwner owner of possible change output
 * @param issuerSession session with the issuer tokens should be redeemed with
 * @param observerSessions optional sessions with the observer nodes, to witch the transaction will be broadcasted
 * @param additionalQueryCriteria additional criteria for token selection
 */
class RedeemFungibleTokensFlow<T : TokenType>
@JvmOverloads
constructor(
        val amount: Amount<T>,
        val changeOwner: AbstractParty,
        override val issuerSession: FlowSession,
        override val observerSessions: List<FlowSession> = emptyList(),
        val additionalQueryCriteria: QueryCriteria? = null
) : AbstractRedeemTokensFlow() {
    @Suspendable
    override fun generateExit(transactionBuilder: TransactionBuilder) {
        addRedeemTokens(transactionBuilder, amount, issuerSession.counterparty, changeOwner, additionalQueryCriteria)
    }
}