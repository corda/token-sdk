package com.r3.corda.lib.tokens.workflows.flows.rpc

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.lib.tokens.contracts.types.TokenType
import com.r3.corda.lib.tokens.workflows.flows.move.*
import com.r3.corda.lib.tokens.workflows.types.PartyAndAmount
import com.r3.corda.lib.tokens.workflows.types.PartyAndToken
import com.r3.corda.lib.tokens.workflows.utilities.sessionsForParties
import net.corda.core.contracts.Amount
import net.corda.core.flows.*
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.AnonymousParty
import net.corda.core.identity.Party
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.transactions.SignedTransaction

/**
 * Initiating flow used to move amounts of tokens to parties, [partiesAndAmounts] specifies what amount of tokens is moved
 * to each participant with possible change output paid to the [changeHolder].
 *
 * Call this for one [TokenType] at a time. If you need to do multiple token types in one transaction then create a new
 * flow, calling [addMoveFungibleTokens] for each token type.
 *
 * @param partiesAndAmounts list of pairing party - amount of token that is to be moved to that party
 * @param observers optional observing parties to which the transaction will be broadcast
 * @param queryCriteria additional criteria for token selection
 * @param changeHolder optional holder of the change outputs, it can be confidential identity, if not specified it
 *                     defaults to caller's legal identity
 * @param haltForExternalSigning optional - halt the flow thread while waiting for signatures if a call to an external
 *                               service is required to obtain them, to prevent blocking other work
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
        val changeHolder: AbstractParty? = null,
        val haltForExternalSigning: Boolean = false
) : FlowLogic<SignedTransaction>() {

    @JvmOverloads
    constructor(
            partyAndAmount: PartyAndAmount<TokenType>,
            observers: List<Party> = emptyList(),
            queryCriteria: QueryCriteria? = null,
            changeHolder: AbstractParty? = null,
            haltForExternalSigning: Boolean = false
    ) : this(listOf(partyAndAmount), observers, queryCriteria, changeHolder, haltForExternalSigning)

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
                changeHolder = changeHolder,
                haltForExternalSigning = haltForExternalSigning
        ))
    }
}

/**
 * Responder flow for [MoveFungibleTokens].
 */
@InitiatedBy(MoveFungibleTokens::class)
class MoveFungibleTokensHandler(val otherSession: FlowSession) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() = subFlow(MoveTokensFlowHandler(otherSession))
}

/**
 * Initiating flow used to move non fungible tokens to parties, [partiesAndTokens] specifies what tokens are moved
 * to each participant.
 *
 * Call this for one [TokenType] at a time. If you need to do multiple token types in one transaction then create a new
 * flow, calling [addMoveNonFungibleTokens] for each token type.
 *
 * @param partyAndToken pairing party - token that is to be moved to that party
 * @param observers optional observing parties to which the transaction will be broadcast
 * @param queryCriteria additional criteria for token selection
 * @param haltForExternalSigning optional - halt the flow thread while waiting for signatures if a call to an external
 *                               service is required to obtain them, to prevent blocking other work
 */
@StartableByService
@StartableByRPC
@InitiatingFlow
class MoveNonFungibleTokens
@JvmOverloads
constructor(
        val partyAndToken: PartyAndToken,
        val observers: List<Party> = emptyList(),
        val queryCriteria: QueryCriteria? = null,
        val haltForExternalSigning: Boolean = false
) : FlowLogic<SignedTransaction>() {
    @Suspendable
    override fun call(): SignedTransaction {
        val observerSessions = sessionsForParties(observers)
        val participantSessions = sessionsForParties(listOf(partyAndToken.party))
        return subFlow(MoveNonFungibleTokensFlow(
                partyAndToken = partyAndToken,
                participantSessions = participantSessions,
                observerSessions = observerSessions,
                queryCriteria = queryCriteria,
                haltForExternalSigning = haltForExternalSigning
        ))
    }
}

/**
 * Responder flow for [MoveNonFungibleTokens].
 */
@InitiatedBy(MoveNonFungibleTokens::class)
class MoveNonFungibleTokensHandler(val otherSession: FlowSession) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() = subFlow(MoveTokensFlowHandler(otherSession))
}

/* Confidential flows. */

/**
 * Version of [MoveFungibleTokens] using confidential identities. Confidential identities are generated and
 * exchanged for all parties that receive tokens states.
 *
 * Call this for one [TokenType] at a time. If you need to do multiple token types in one transaction then create a new
 * flow, calling [addMoveNonFungibleTokens] for each token type and handle confidential identities exchange yourself.
 *
 * @param partiesAndAmounts list of pairing party - amount of token that is to be moved to that party
 * @param observers optional observing parties to which the transaction will be broadcast
 * @param queryCriteria additional criteria for token selection
 * @param changeHolder holder of the change outputs, it can be confidential identity
 * @param haltForExternalSigning optional - halt the flow thread while waiting for signatures if a call to an external
 *                               service is required to obtain them, to prevent blocking other work
 */
@StartableByService
@StartableByRPC
@InitiatingFlow
class ConfidentialMoveFungibleTokens(
        val partiesAndAmounts: List<PartyAndAmount<TokenType>>,
        val observers: List<Party>,
        val queryCriteria: QueryCriteria? = null,
        val changeHolder: AbstractParty? = null,
        val haltForExternalSigning: Boolean = false
) : FlowLogic<SignedTransaction>() {

    constructor(
            partyAndAmount: PartyAndAmount<TokenType>,
            observers: List<Party>,
            queryCriteria: QueryCriteria? = null,
            changeHolder: AbstractParty? = null,
            haltForExternalSigning: Boolean = false
    ) : this(listOf(partyAndAmount), observers, queryCriteria, changeHolder, haltForExternalSigning)

    @Suspendable
    override fun call(): SignedTransaction {
        val participants = partiesAndAmounts.map(PartyAndAmount<*>::party)
        val observerSessions = sessionsForParties(observers)
        val participantSessions = sessionsForParties(participants)
        val confidentialHolder = changeHolder ?: let {
            val key = serviceHub.keyManagementService.freshKey()
            try {
                serviceHub.identityService.registerKey(key, ourIdentity)
            } catch (e: Exception) {
                throw FlowException("Could not register a new key for party: $ourIdentity as the provided public key is already registered " +
                        "or registered to a different party.")
            }
            AnonymousParty(key)
        }
        return subFlow(ConfidentialMoveFungibleTokensFlow(
                partiesAndAmounts = partiesAndAmounts,
                participantSessions = participantSessions,
                observerSessions = observerSessions,
                queryCriteria = queryCriteria,
                changeHolder = confidentialHolder,
                haltForExternalSigning = haltForExternalSigning
        ))
    }
}

/**
 * Responder flow for [ConfidentialMoveFungibleTokens]
 */
@InitiatedBy(ConfidentialMoveFungibleTokens::class)
class ConfidentialMoveFungibleTokensHandler(val otherSession: FlowSession) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() = subFlow(ConfidentialMoveTokensFlowHandler(otherSession))
}

/**
 * Version of [MoveNonFungibleTokens] using confidential identities. Confidential identities are generated and
 * exchanged for all parties that receive tokens states.
 *
 * Call this for one [TokenType] at a time. If you need to do multiple token types in one transaction then create a new
 * flow, calling [addMoveFungibleTokens] for each token type and handle confidential identities exchange yourself.
 *
 * @param partyAndToken list of pairing party - token that is to be moved to that party
 * @param observers optional observing parties to which the transaction will be broadcast
 * @param queryCriteria additional criteria for token selection
 * @param haltForExternalSigning optional - halt the flow thread while waiting for signatures if a call to an external
 *                               service is required to obtain them, to prevent blocking other work
 */
@StartableByService
@StartableByRPC
@InitiatingFlow
class ConfidentialMoveNonFungibleTokens(
        val partyAndToken: PartyAndToken,
        val observers: List<Party>,
        val queryCriteria: QueryCriteria? = null,
        val haltForExternalSigning: Boolean = false
) : FlowLogic<SignedTransaction>() {
    @Suspendable
    override fun call(): SignedTransaction {
        val observerSessions = sessionsForParties(observers)
        val participantSessions = sessionsForParties(listOf(partyAndToken.party))
        return subFlow(ConfidentialMoveNonFungibleTokensFlow(
                partyAndToken = partyAndToken,
                participantSessions = participantSessions,
                observerSessions = observerSessions,
                queryCriteria = queryCriteria,
                haltForExternalSigning = haltForExternalSigning
        ))
    }
}

/**
 * Responder flow for [ConfidentialMoveNonFungibleTokens].
 */
@InitiatedBy(ConfidentialMoveNonFungibleTokens::class)
class ConfidentialMoveNonFungibleTokensHandler(val otherSession: FlowSession) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() = subFlow(ConfidentialMoveTokensFlowHandler(otherSession))
}