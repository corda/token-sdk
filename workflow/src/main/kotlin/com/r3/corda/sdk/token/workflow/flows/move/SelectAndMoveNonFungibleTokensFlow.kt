package com.r3.corda.sdk.token.workflow.flows.move

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.sdk.token.contracts.types.TokenType
import com.r3.corda.sdk.token.workflow.types.PartyAndToken
import net.corda.core.flows.FlowSession
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.transactions.TransactionBuilder

class SelectAndMoveNonFungibleTokensFlow<T : TokenType>(
        val partyAndToken: PartyAndToken<T>,
        override val participantSessions: List<FlowSession>,
        override val observerSessions: List<FlowSession>,
        val queryCriteria: QueryCriteria?
) : AbstractMoveTokensFlow() {
    @Suspendable
    override fun addMove(transactionBuilder: TransactionBuilder) {
        addMoveTokens(transactionBuilder, partyAndToken, queryCriteria)
    }
}