package com.r3.corda.lib.tokens.workflows.flows.move

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.lib.tokens.contracts.types.TokenType
import com.r3.corda.lib.tokens.workflows.types.PartyAndAmount
import net.corda.core.flows.FlowSession
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.transactions.TransactionBuilder

/**
 * Inlined flow used to move amounts of tokens to parties, [partiesAndAmounts] specifies what amount of tokens is moved
 * to each participant with possible change output paid to the [changeOwner].
 *
 * Call this for one [TokenType] at a time. If you need to do multiple token types in one transaction then create a new
 * flow, calling [addMoveTokens] for each token type.
 *
 * @param partiesAndAmounts list of pairing party - amount of token that is to be moved to that party
 * @param participantSessions sessions with the participants of move transaction
 * @param observerSessions optional sessions with the observer nodes, to witch the transaction will be broadcasted
 * @param queryCriteria additional criteria for token selection
 */
class MoveFungibleTokensFlow<T : TokenType>
@JvmOverloads
constructor(
        val partiesAndAmounts: List<PartyAndAmount<T>>,
        override val participantSessions: List<FlowSession>,
        override val observerSessions: List<FlowSession> = emptyList(),
        val queryCriteria: QueryCriteria? = null
) : AbstractMoveTokensFlow() {

    @JvmOverloads
    constructor(
            partyAndAmount: PartyAndAmount<T>,
            queryCriteria: QueryCriteria,
            participantSessions: List<FlowSession>,
            observerSessions: List<FlowSession> = emptyList()
    ) : this(listOf(partyAndAmount), participantSessions, observerSessions, queryCriteria)

    @Suspendable
    override fun addMove(transactionBuilder: TransactionBuilder) {
        addMoveTokens(
                transactionBuilder = transactionBuilder,
                partiesAndAmounts = partiesAndAmounts,
                changeHolder = ourIdentity,
                queryCriteria = queryCriteria
        )
    }
}