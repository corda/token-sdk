package com.r3.corda.lib.tokens.workflows.flows.rpc

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.lib.tokens.contracts.states.EvolvableTokenType
import com.r3.corda.lib.tokens.workflows.flows.evolvable.*
import net.corda.core.contracts.StateAndRef
import net.corda.core.contracts.TransactionState
import net.corda.core.flows.*
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.transactions.SignedTransaction

/**
 * Initiating flow for creating multiple tokens of evolvable token type.
 *
 * @property transactionStates a list of states to create evolvable token types with
 * @param observers optional observing parties to which the transaction will be broadcast
 */
@InitiatingFlow
@StartableByRPC
class CreateEvolvableTokens
@JvmOverloads
constructor(
        val transactionStates: List<TransactionState<EvolvableTokenType>>,
        val observers: List<Party> = emptyList()
) : FlowLogic<SignedTransaction>() {
    @JvmOverloads
    constructor(transactionState: TransactionState<EvolvableTokenType>, observers: List<Party> = emptyList()) : this(listOf(transactionState), observers)

    @Suspendable
    override fun call(): SignedTransaction {
        // Initiate sessions to all observers.
        val observersSessions = (observers + statesObservers).toSet().map { initiateFlow(it) }
        // Initiate sessions to all maintainers but our node.
        val participantsSessions: List<FlowSession> = evolvableTokens.otherMaintainers(ourIdentity).map { initiateFlow(it) }
        return subFlow(CreateEvolvableTokensFlow(transactionStates, participantsSessions, observersSessions))
    }

    private val evolvableTokens = transactionStates.map { it.data }

    // TODO Refactor it more.
    private val statesObservers
        get(): List<Party> {
            val observers = evolvableTokens.participants().minus(evolvableTokens.maintainers()).minus(this.ourIdentity)
            return observers.map { serviceHub.identityService.wellKnownPartyFromAnonymous(it)!! }
        }
}

/**
 * Responder flow for [CreateEvolvableTokens].
 */
@InitiatedBy(CreateEvolvableTokens::class)
class CreateEvolvableTokensHandler(val otherSession: FlowSession) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() = subFlow(CreateEvolvableTokensFlowHandler(otherSession))
}

/**
 * An initiating flow to update an existing evolvable token type which is already recorded on the ledger. This is an
 *
 * @property oldStateAndRef the existing evolvable token type to update
 * @property newState the new version of the evolvable token type
 * @param observers optional observing parties to which the transaction will be broadcast
 */
@InitiatingFlow
@StartableByRPC
class UpdateEvolvableToken
@JvmOverloads
constructor(val oldStateAndRef: StateAndRef<EvolvableTokenType>,
            val newState: EvolvableTokenType,
            val observers: List<Party> = emptyList()
) : FlowLogic<SignedTransaction>() {
    @Suspendable
    override fun call(): SignedTransaction {
        // Initiate sessions to all observers.
        val observersSessions = (observers + statesObservers).toSet().map { initiateFlow(it) }
        // Initiate sessions to all maintainers but our node.
        val participantsSessions: List<FlowSession> = evolvableTokens.otherMaintainers(ourIdentity).map { initiateFlow(it) }
        return subFlow(UpdateEvolvableTokenFlow(oldStateAndRef, newState, participantsSessions, observersSessions))
    }

    private val oldState get() = oldStateAndRef.state.data
    private val evolvableTokens = listOf(oldState, newState)

    // TODO Refactor it more.
    private val otherObservers
        get(): Set<AbstractParty> {
            return (evolvableTokens.participants() + subscribersForState(newState, serviceHub)).minus(evolvableTokens.maintainers()).minus(this.ourIdentity)
        }

    private val statesObservers
        get(): List<Party> {
            return otherObservers.map { serviceHub.identityService.wellKnownPartyFromAnonymous(it)!! }
        }
}

/**
 * Responder flow for [UpdateEvolvableToken].
 */
@InitiatedBy(UpdateEvolvableToken::class)
class UpdateEvolvableTokenHandler(val otherSession: FlowSession) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() = subFlow(UpdateEvolvableTokenFlowHandler(otherSession))
}
