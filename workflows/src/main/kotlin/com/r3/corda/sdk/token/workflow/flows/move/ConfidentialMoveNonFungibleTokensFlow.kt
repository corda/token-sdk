package com.r3.corda.sdk.token.workflow.flows.move

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.sdk.token.contracts.types.TokenType
import com.r3.corda.sdk.token.workflow.flows.confidential.ConfidentialTokensFlow
import com.r3.corda.sdk.token.workflow.flows.internal.selection.generateMoveNonFungible
import com.r3.corda.sdk.token.workflow.types.PartyAndToken
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.transactions.SignedTransaction

/**
 * Version of [MoveNonFungibleTokensFlow] using confidential identities. Confidential identities are generated and exchanged for
 * all parties that receive tokens states.
 * Call this for one [TokenType] at a time. If you need to do multiple token types in one transaction then create a new
 * flow, calling [addMoveTokens] for each token type and handle confidential identities exchange yourself.
 *
 * @param partyAndToken list of pairing party - token that is to be moved to that party
 * @param participantSessions sessions with the participants of move transaction
 * @param observerSessions optional sessions with the observer nodes, to witch the transaction will be broadcasted
 * @param queryCriteria additional criteria for token selection
 */
class ConfidentialMoveNonFungibleTokensFlow<T : TokenType>
@JvmOverloads
constructor(
        val partyAndToken: PartyAndToken<T>,
        val participantSessions: List<FlowSession>,
        val observerSessions: List<FlowSession> = emptyList(),
        val queryCriteria: QueryCriteria?
) : FlowLogic<SignedTransaction>() {
    @Suspendable
    override fun call(): SignedTransaction {
        // TODO: This should be moved into a utility function.
        val (input, output) = generateMoveNonFungible(partyAndToken, serviceHub.vaultService, queryCriteria)
        val confidentialOutput = subFlow(ConfidentialTokensFlow(listOf(output), participantSessions)).single()
        return subFlow(MoveTokensFlow(input, confidentialOutput, participantSessions, observerSessions))
    }
}
