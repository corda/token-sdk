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

class ConfidentialSelectAndMoveNonFungibleTokensFlow<T : TokenType>(
        val partyAndToken: PartyAndToken<T>,
        val participantSessions: List<FlowSession>,
        val observerSessions: List<FlowSession>,
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
