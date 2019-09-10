package com.r3.corda.lib.tokens.workflows.flows.rpc

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.lib.ci.registerKeyToParty
import com.r3.corda.lib.tokens.contracts.types.TokenType
import com.r3.corda.lib.tokens.workflows.flows.move.ConfidentialMoveFungibleTokensFlow
import com.r3.corda.lib.tokens.workflows.flows.move.ConfidentialMoveNonFungibleTokensFlow
import com.r3.corda.lib.tokens.workflows.flows.move.ConfidentialMoveTokensFlowHandler
import com.r3.corda.lib.tokens.workflows.flows.move.MoveFungibleTokensFlow
import com.r3.corda.lib.tokens.workflows.flows.move.MoveNonFungibleTokensFlow
import com.r3.corda.lib.tokens.workflows.flows.move.MoveTokensFlowHandler
import com.r3.corda.lib.tokens.workflows.types.PartyAndAmount
import com.r3.corda.lib.tokens.workflows.types.PartyAndToken
import com.r3.corda.lib.tokens.workflows.utilities.sessionsForParties
import net.corda.core.contracts.Amount
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.flows.InitiatedBy
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.StartableByRPC
import net.corda.core.flows.StartableByService
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.AnonymousParty
import net.corda.core.identity.Party
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.transactions.SignedTransaction

/**
 * TODO docs
 */
@StartableByService
@StartableByRPC
@InitiatingFlow
class MoveFungibleTokens
@JvmOverloads
constructor(
        val partiesAndAmounts: List<PartyAndAmount<TokenType>>,
        val observers: List<Party> = emptyList(),
        val queryCriteria: QueryCriteria? = null,
        val changeHolder: AbstractParty? = null
) : FlowLogic<SignedTransaction>() {

    @JvmOverloads
    constructor(
            partyAndAmount: PartyAndAmount<TokenType>,
            observers: List<Party> = emptyList(),
            queryCriteria: QueryCriteria? = null,
            changeHolder: AbstractParty? = null
    ) : this(listOf(partyAndAmount), observers, queryCriteria, changeHolder)

    constructor(amount: Amount<TokenType>, holder: AbstractParty) : this(PartyAndAmount(holder, amount), emptyList())

    @Suspendable
    override fun call(): SignedTransaction {
        val participants = partiesAndAmounts.map(PartyAndAmount<*>::party)
        val observerSessions = sessionsForParties(observers)
        val participantSessions = sessionsForParties(participants)
        return subFlow(MoveFungibleTokensFlow(
                partiesAndAmounts = partiesAndAmounts,
                participantSessions = participantSessions,
                observerSessions = observerSessions,
                queryCriteria = queryCriteria,
                changeHolder = changeHolder
        ))
    }
}

/**
 * TODO docs
 */
@InitiatedBy(MoveFungibleTokens::class)
class MoveFungibleTokensHandler(val otherSession: FlowSession) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() = subFlow(MoveTokensFlowHandler(otherSession))
}

/**
 * TODO docs
 */
@StartableByService
@StartableByRPC
@InitiatingFlow
class MoveNonFungibleTokens
@JvmOverloads
constructor(
        val partyAndToken: PartyAndToken,
        val observers: List<Party> = emptyList(),
        val queryCriteria: QueryCriteria? = null
) : FlowLogic<SignedTransaction>() {
    @Suspendable
    override fun call(): SignedTransaction {
        val observerSessions = sessionsForParties(observers)
        val participantSessions = sessionsForParties(listOf(partyAndToken.party))
        return subFlow(MoveNonFungibleTokensFlow(
                partyAndToken = partyAndToken,
                participantSessions = participantSessions,
                observerSessions = observerSessions,
                queryCriteria = queryCriteria
        ))
    }
}

/**
 * TODO docs
 */
@InitiatedBy(MoveNonFungibleTokens::class)
class MoveNonFungibleTokensHandler(val otherSession: FlowSession) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() = subFlow(MoveTokensFlowHandler(otherSession))
}

/* Confidential flows. */

/**
 * TODO docs
 */
@StartableByService
@StartableByRPC
@InitiatingFlow
class ConfidentialMoveFungibleTokens(
        val partiesAndAmounts: List<PartyAndAmount<TokenType>>,
        val observers: List<Party>,
        val queryCriteria: QueryCriteria? = null,
        val changeHolder: AbstractParty? = null
) : FlowLogic<SignedTransaction>() {

    constructor(
            partyAndAmount: PartyAndAmount<TokenType>,
            observers: List<Party>,
            queryCriteria: QueryCriteria? = null,
            changeHolder: AbstractParty? = null
    ) : this(listOf(partyAndAmount), observers, queryCriteria, changeHolder)

    @Suspendable
    override fun call(): SignedTransaction {
        val participants = partiesAndAmounts.map(PartyAndAmount<*>::party)
        val observerSessions = sessionsForParties(observers)
        val participantSessions = sessionsForParties(participants)
        val confidentialHolder = changeHolder?:let {
                val key = serviceHub.keyManagementService.freshKey()
                    registerKeyToParty(key, ourIdentity, serviceHub)
                    AnonymousParty(key)
                }
        return subFlow(ConfidentialMoveFungibleTokensFlow(
                partiesAndAmounts = partiesAndAmounts,
                participantSessions = participantSessions,
                observerSessions = observerSessions,
                queryCriteria = queryCriteria,
                changeHolder = confidentialHolder
        ))
    }
}

/**
 * TODO docs
 */
@InitiatedBy(ConfidentialMoveFungibleTokens::class)
class ConfidentialMoveFungibleTokensHandler(val otherSession: FlowSession) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() = subFlow(ConfidentialMoveTokensFlowHandler(otherSession))
}

/**
 * TODO docs
 */
@StartableByService
@StartableByRPC
@InitiatingFlow
class ConfidentialMoveNonFungibleTokens(
        val partyAndToken: PartyAndToken,
        val observers: List<Party>,
        val queryCriteria: QueryCriteria? = null
) : FlowLogic<SignedTransaction>() {
    @Suspendable
    override fun call(): SignedTransaction {
        val observerSessions = sessionsForParties(observers)
        val participantSessions = sessionsForParties(listOf(partyAndToken.party))
        return subFlow(ConfidentialMoveNonFungibleTokensFlow(
                partyAndToken = partyAndToken,
                participantSessions = participantSessions,
                observerSessions = observerSessions,
                queryCriteria = queryCriteria
        ))
    }
}

/**
 * TODO docs
 */
@InitiatedBy(ConfidentialMoveNonFungibleTokens::class)
class ConfidentialMoveNonFungibleTokensHandler(val otherSession: FlowSession) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() = subFlow(ConfidentialMoveTokensFlowHandler(otherSession))
}