package com.r3.corda.sdk.token.workflow.flows.move

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.sdk.token.contracts.types.TokenType
import com.r3.corda.sdk.token.workflow.types.PartyAndAmount
import net.corda.core.flows.FlowSession
import net.corda.core.identity.AbstractParty
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.transactions.TransactionBuilder

/**
 * Call this for one [TokenType] at a time. If you need to do multiple token types in one transaction then create a new
 * flow, calling [addMoveTokens] for each token type.
 */
class SelectAndMoveFungibleTokensFlow<T : TokenType>(
        val partiesAndAmounts: List<PartyAndAmount<T>>,
        override val participantSessions: List<FlowSession>,
        override val observerSessions: List<FlowSession>,
        val queryCriteria: QueryCriteria?,
        val changeHolder: AbstractParty? = null
) : AbstractMoveTokensFlow() {

    constructor(
            partyAndAmount: PartyAndAmount<T>,
            queryCriteria: QueryCriteria,
            participantSessions: List<FlowSession>,
            observerSessions: List<FlowSession>,
            changeHolder: AbstractParty?
    ) : this(listOf(partyAndAmount), participantSessions, observerSessions, queryCriteria, changeHolder)

    @Suspendable
    override fun addMove(transactionBuilder: TransactionBuilder) {
        addMoveTokens(transactionBuilder, partiesAndAmounts, queryCriteria, changeHolder)
    }

}