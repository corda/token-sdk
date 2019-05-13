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

class ConfidentialSelectAndMoveFungibleTokensFlow<T : TokenType>(
        val partiesAndAmounts: List<PartyAndAmount<T>>,
        val participantSessions: List<FlowSession>,
        val observerSessions: List<FlowSession>,
        val queryCriteria: QueryCriteria?,
        val changeHolder: AbstractParty? = null
) : FlowLogic<SignedTransaction>() {

    constructor(
            partyAndAmount: PartyAndAmount<T>,
            queryCriteria: QueryCriteria,
            participantSessions: List<FlowSession>,
            observerSessions: List<FlowSession>,
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
