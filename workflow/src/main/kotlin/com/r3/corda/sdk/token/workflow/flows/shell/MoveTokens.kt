package com.r3.corda.sdk.token.workflow.flows.shell

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.sdk.token.contracts.types.TokenType
import com.r3.corda.sdk.token.workflow.flows.move.*
import com.r3.corda.sdk.token.workflow.types.PartyAndAmount
import com.r3.corda.sdk.token.workflow.types.PartyAndToken
import com.r3.corda.sdk.token.workflow.utilities.sessionsForParties
import net.corda.core.contracts.Amount
import net.corda.core.flows.*
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.transactions.SignedTransaction

@StartableByService
@StartableByRPC
@InitiatingFlow
class MoveFungibleTokens<T : TokenType>(
        val partiesAndAmounts: List<PartyAndAmount<T>>,
        val observers: List<Party>,
        val queryCriteria: QueryCriteria? = null,
        val changeHolder: AbstractParty? = null
) : FlowLogic<SignedTransaction>() {

    constructor(
            partyAndAmount: PartyAndAmount<T>,
            observers: List<Party>,
            queryCriteria: QueryCriteria? = null,
            changeHolder: AbstractParty? = null
    ) : this(listOf(partyAndAmount), observers, queryCriteria, changeHolder)

    constructor(amount: Amount<T>, holder: AbstractParty) : this(PartyAndAmount(holder, amount), emptyList())

    @Suspendable
    override fun call(): SignedTransaction {
        val participants = partiesAndAmounts.map(PartyAndAmount<*>::party)
        val observerSessions = sessionsForParties(observers)
        val participantSessions = sessionsForParties(participants)
        return subFlow(SelectAndMoveFungibleTokensFlow(
                partiesAndAmounts = partiesAndAmounts,
                participantSessions = participantSessions,
                observerSessions = observerSessions,
                queryCriteria = queryCriteria,
                changeHolder = changeHolder
        ))
    }
}

@InitiatedBy(MoveFungibleTokens::class)
class MoveFungibleTokensHandler(val otherSession: FlowSession) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() = subFlow(MoveTokensFlowHandler(otherSession))
}

@StartableByService
@StartableByRPC
@InitiatingFlow
class MoveNonFungibleTokens<T : TokenType>(
        val partyAndToken: PartyAndToken<T>,
        val observers: List<Party>,
        val queryCriteria: QueryCriteria? = null
) : FlowLogic<SignedTransaction>() {
    @Suspendable
    override fun call(): SignedTransaction {
        val observerSessions = sessionsForParties(observers)
        val participantSessions = sessionsForParties(listOf(partyAndToken.party))
        return subFlow(SelectAndMoveNonFungibleTokensFlow(
                partyAndToken = partyAndToken,
                participantSessions = participantSessions,
                observerSessions = observerSessions,
                queryCriteria = queryCriteria
        ))
    }
}

@InitiatedBy(MoveNonFungibleTokens::class)
class MoveNonFungibleTokensHandler(val otherSession: FlowSession) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() = subFlow(MoveTokensFlowHandler(otherSession))
}

/* Confidential flows. */

@StartableByService
@StartableByRPC
@InitiatingFlow
class ConfidentialMoveFungibleTokens<T : TokenType>(
        val partiesAndAmounts: List<PartyAndAmount<T>>,
        val observers: List<Party>,
        val queryCriteria: QueryCriteria? = null,
        val changeHolder: AbstractParty? = null
) : FlowLogic<SignedTransaction>() {

    constructor(
            partyAndAmount: PartyAndAmount<T>,
            observers: List<Party>,
            queryCriteria: QueryCriteria? = null,
            changeHolder: AbstractParty? = null
    ) : this(listOf(partyAndAmount), observers, queryCriteria, changeHolder)

    @Suspendable
    override fun call(): SignedTransaction {
        val participants = partiesAndAmounts.map(PartyAndAmount<*>::party)
        val observerSessions = sessionsForParties(observers)
        val participantSessions = sessionsForParties(participants)
        return subFlow(ConfidentialSelectAndMoveFungibleTokensFlow(
                partiesAndAmounts = partiesAndAmounts,
                participantSessions = participantSessions,
                observerSessions = observerSessions,
                queryCriteria = queryCriteria,
                changeHolder = changeHolder
        ))
    }
}

@InitiatedBy(ConfidentialMoveFungibleTokens::class)
class ConfidentialMoveFungibleTokensHandler(val otherSession: FlowSession) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() = subFlow(ConfidentialMoveTokensFlowHandler(otherSession))
}

@StartableByService
@StartableByRPC
@InitiatingFlow
class ConfidentialMoveNonFungibleTokens<T : TokenType>(
        val partyAndToken: PartyAndToken<T>,
        val observers: List<Party>,
        val queryCriteria: QueryCriteria? = null
) : FlowLogic<SignedTransaction>() {
    @Suspendable
    override fun call(): SignedTransaction {
        val observerSessions = sessionsForParties(observers)
        val participantSessions = sessionsForParties(listOf(partyAndToken.party))
        return subFlow(ConfidentialSelectAndMoveNonFungibleTokensFlow(
                partyAndToken = partyAndToken,
                participantSessions = participantSessions,
                observerSessions = observerSessions,
                queryCriteria = queryCriteria
        ))
    }
}

@InitiatedBy(ConfidentialMoveNonFungibleTokens::class)
class ConfidentialMoveNonFungibleTokensHandler(val otherSession: FlowSession) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() = subFlow(ConfidentialMoveTokensFlowHandler(otherSession))
}