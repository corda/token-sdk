package com.r3.corda.lib.tokens.workflows.flows.move

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.lib.tokens.contracts.types.TokenType
import com.r3.corda.lib.tokens.workflows.internal.selection.TokenQueryBy
import com.r3.corda.lib.tokens.workflows.types.PartyAndAmount
import net.corda.core.flows.FlowSession
import net.corda.core.identity.AbstractParty
import net.corda.core.transactions.TransactionBuilder

/**
 * Inlined flow used to move amounts of tokens to parties, [partiesAndAmounts] specifies what amount of tokens is moved
 * to each participant with possible change output paid to the [changeOwner].
 *
 * Call this for one [TokenType] at a time. If you need to do multiple token types in one transaction then create a new
 * flow, calling [addMoveFungibleTokens] for each token type.
 *
 * @param partiesAndAmounts list of pairing party - amount of token that is to be moved to that party
 * @param participantSessions sessions with the participants of move transaction
 * @param observerSessions optional sessions with the observer nodes, to witch the transaction will be broadcasted
 * @param queryCriteria additional criteria for token selection
 * @param changeHolder optional holder of the change outputs, it can be confidential identity, if not specified it defaults to caller's legal identity
 */
class MoveFungibleTokensFlow
@JvmOverloads
constructor(
        val partiesAndAmounts: List<PartyAndAmount<TokenType>>,
        override val participantSessions: List<FlowSession>,
        override val observerSessions: List<FlowSession> = emptyList(),
        val queryBy: TokenQueryBy? = null,
        val changeHolder: AbstractParty? = null
) : AbstractMoveTokensFlow() {

    @JvmOverloads
    constructor(
            partyAndAmount: PartyAndAmount<TokenType>,
            participantSessions: List<FlowSession>,
            observerSessions: List<FlowSession> = emptyList(),
            queryBy: TokenQueryBy? = null,
            changeHolder: AbstractParty? = null
    ) : this(listOf(partyAndAmount), participantSessions, observerSessions, queryBy, changeHolder)

    @Suspendable
    override fun addMove(transactionBuilder: TransactionBuilder) {
        addMoveFungibleTokens(
                transactionBuilder = transactionBuilder,
                serviceHub = serviceHub,
                partiesAndAmounts = partiesAndAmounts,
                changeHolder = changeHolder ?: ourIdentity,
                queryBy = queryBy
        )
    }
}