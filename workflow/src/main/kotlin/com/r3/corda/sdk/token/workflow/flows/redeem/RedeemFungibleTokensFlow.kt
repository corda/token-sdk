package com.r3.corda.sdk.token.workflow.flows.redeem

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.sdk.token.contracts.types.TokenType
import com.r3.corda.sdk.token.workflow.flows.internal.checkSameNotary
import com.r3.corda.sdk.token.workflow.flows.internal.selection.TokenSelection
import com.r3.corda.sdk.token.workflow.utilities.tokenAmountWithIssuerCriteria
import net.corda.core.contracts.Amount
import net.corda.core.flows.FlowSession
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.transactions.TransactionBuilder

/**
 * TODO docs
 */
// TODO query criteria?
class RedeemFungibleTokensFlow<T : TokenType>
@JvmOverloads
constructor(
        val amount: Amount<T>,
        val issuer: Party,
        val changeOwner: AbstractParty, // TODO default it to us
        override val issuerSession: FlowSession,
        override val observerSessions: List<FlowSession> = emptyList()
) : AbstractRedeemTokensFlow() {
    @Suspendable
    override fun generateExit(transactionBuilder: TransactionBuilder): TransactionBuilder {
       return addRedeemTokens(transactionBuilder, amount, issuer, changeOwner)
    }
}