package com.r3.corda.lib.tokens.workflows.flows.move

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.lib.tokens.contracts.types.TokenType
import com.r3.corda.lib.tokens.selection.TokenQueryBy
import com.r3.corda.lib.tokens.selection.database.selector.DatabaseTokenSelection
import com.r3.corda.lib.tokens.workflows.flows.confidential.ConfidentialTokensFlow
import com.r3.corda.lib.tokens.workflows.internal.flows.finality.TransactionRole
import com.r3.corda.lib.tokens.workflows.types.PartyAndAmount
import com.r3.corda.lib.tokens.workflows.types.toPairs
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.identity.AbstractParty
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.transactions.SignedTransaction

/**
 * Version of [MoveFungibleTokensFlow] using confidential identities. Confidential identities are generated and
 * exchanged for all parties that receive tokens states.
 *
 * Call this for one [TokenType] at a time. If you need to do multiple token types in one transaction then create a new
 * flow, calling [addMoveNonFungibleTokens] for each token type and handle confidential identities exchange yourself.
 *
 * @param partiesAndAmounts list of pairing party - amount of token that is to be moved to that party
 * @param participantSessions sessions with the participants of move transaction
 * @param changeHolder holder of the change outputs, it can be confidential identity
 * @param observerSessions optional sessions with the observer nodes, to witch the transaction will be broadcasted
 * @param queryCriteria additional criteria for token selection
 * @param haltForExternalSigning optional - halt the flow thread while waiting for signatures if a call to an external
 *                               service is required to obtain them, to prevent blocking other work
 */
class ConfidentialMoveFungibleTokensFlow
@JvmOverloads
constructor(
        val partiesAndAmounts: List<PartyAndAmount<TokenType>>,
        val participantSessions: List<FlowSession>,
        val changeHolder: AbstractParty,
        val observerSessions: List<FlowSession> = emptyList(),
        val queryCriteria: QueryCriteria? = null,
        val haltForExternalSigning: Boolean = false
) : FlowLogic<SignedTransaction>() {

    @JvmOverloads
    constructor(
            partyAndAmount: PartyAndAmount<TokenType>,
            participantSessions: List<FlowSession>,
            changeHolder: AbstractParty,
            queryCriteria: QueryCriteria? = null,
            observerSessions: List<FlowSession> = emptyList()

    ) : this(listOf(partyAndAmount), participantSessions, changeHolder, observerSessions, queryCriteria)

    @Suspendable
    override fun call(): SignedTransaction {
        // TODO add in memory selection too
        val tokenSelection = DatabaseTokenSelection(serviceHub)
        val (inputs, outputs) = tokenSelection.generateMove(
                lockId = stateMachine.id.uuid,
                partiesAndAmounts = partiesAndAmounts.toPairs(),
                changeHolder = changeHolder,
                queryBy = TokenQueryBy(queryCriteria = queryCriteria)
        )
        // TODO Not pretty fix, because we decided to go with sessions approach, we need to make sure that right responders are started depending on observer/participant role
        participantSessions.forEach { it.send(TransactionRole.PARTICIPANT) }
        observerSessions.forEach { it.send(TransactionRole.OBSERVER) }
        val confidentialOutputs = subFlow(ConfidentialTokensFlow(outputs, participantSessions))
        return subFlow(MoveTokensFlow(inputs, confidentialOutputs, participantSessions, observerSessions, haltForExternalSigning))
    }
}
