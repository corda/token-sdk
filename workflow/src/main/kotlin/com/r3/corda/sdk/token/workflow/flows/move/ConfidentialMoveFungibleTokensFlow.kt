package com.r3.corda.sdk.token.workflow.flows.move

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.sdk.token.contracts.types.TokenType
import com.r3.corda.sdk.token.workflow.flows.confidential.ConfidentialTokensFlow
import com.r3.corda.sdk.token.workflow.flows.internal.selection.TokenSelection
import com.r3.corda.sdk.token.workflow.types.PartyAndAmount
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.identity.AbstractParty
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.transactions.SignedTransaction

/**
 * Version of [MoveFungibleTokensFlow] using confidential identities. Confidential identities are generated and exchanged for
 * all parties that receive tokens states.
 * Call this for one [TokenType] at a time. If you need to do multiple token types in one transaction then create a new
 * flow, calling [addMoveTokens] for each token type and handle confidential identities exchange yourself.
 *
 * @param partiesAndAmounts list of pairing party - amount of token that is to be moved to that party
 * @param participantSessions sessions with the participants of move transaction
 * @param observerSessions optional sessions with the observer nodes, to witch the transaction will be broadcasted
 * @param queryCriteria additional criteria for token selection
 * @param changeHolder optional holder of the change outputs, it can be confidential identity
 */
class ConfidentialMoveFungibleTokensFlow<T : TokenType>
@JvmOverloads
constructor (
        val partiesAndAmounts: List<PartyAndAmount<T>>,
        val participantSessions: List<FlowSession>,
        val observerSessions: List<FlowSession> = emptyList(),
        val queryCriteria: QueryCriteria?,
        val changeHolder: AbstractParty? = null
) : FlowLogic<SignedTransaction>() {

    @JvmOverloads
    constructor(
            partyAndAmount: PartyAndAmount<T>,
            queryCriteria: QueryCriteria,
            participantSessions: List<FlowSession>,
            observerSessions: List<FlowSession> = emptyList(),
            changeHolder: AbstractParty?
    ) : this(listOf(partyAndAmount), participantSessions, observerSessions, queryCriteria, changeHolder)

    @Suspendable
    override fun call(): SignedTransaction {
        // TODO: This should be moved into a utility function.
        val tokenSelection = TokenSelection(serviceHub)
        val (inputs, outputs) = tokenSelection.generateMove(stateMachine.id.uuid, partiesAndAmounts, queryCriteria, changeHolder)
        val confidentialOutputs = subFlow(ConfidentialTokensFlow(outputs, participantSessions))
        return subFlow(MoveTokensFlow(inputs, confidentialOutputs, participantSessions, observerSessions))
    }
}
