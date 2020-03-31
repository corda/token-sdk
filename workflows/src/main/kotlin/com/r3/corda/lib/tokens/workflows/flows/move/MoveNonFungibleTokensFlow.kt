package com.r3.corda.lib.tokens.workflows.flows.move

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.lib.tokens.contracts.types.TokenType
import com.r3.corda.lib.tokens.workflows.types.PartyAndToken
import net.corda.core.flows.FlowSession
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.transactions.TransactionBuilder

/**
 * Inlined flow used to move non fungible tokens to parties, [partiesAndTokens] specifies what tokens are moved
 * to each participant.
 *
 * Call this for one [TokenType] at a time. If you need to do multiple token types in one transaction then create a new
 * flow, calling [addMoveNonFungibleTokens] for each token type.
 *
 * @param partyAndToken pairing party - token that is to be moved to that party
 * @param participantSessions sessions with the participants of move transaction
 * @param observerSessions optional sessions with the observer nodes, to witch the transaction will be broadcasted
 * @param queryCriteria additional criteria for token selection
 */
class MoveNonFungibleTokensFlow
@JvmOverloads
constructor(
        val partyAndToken: PartyAndToken,
        override val participantSessions: List<FlowSession>,
        override val observerSessions: List<FlowSession> = emptyList(),
        val queryCriteria: QueryCriteria?
) : AbstractMoveTokensFlow() {
    @Suspendable
    override fun addMove(transactionBuilder: TransactionBuilder) {
        addMoveNonFungibleTokens(transactionBuilder, serviceHub, partyAndToken, queryCriteria)
    }
}