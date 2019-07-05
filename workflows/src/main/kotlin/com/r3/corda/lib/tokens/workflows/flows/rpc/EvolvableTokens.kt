package com.r3.corda.lib.tokens.workflows.flows.rpc

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.lib.tokens.contracts.states.EvolvableTokenType
import com.r3.corda.lib.tokens.workflows.flows.evolvable.CreateEvolvableTokensFlow
import com.r3.corda.lib.tokens.workflows.flows.evolvable.CreateEvolvableTokensFlowHandler
import com.r3.corda.lib.tokens.workflows.flows.evolvable.UpdateEvolvableTokenFlow
import com.r3.corda.lib.tokens.workflows.flows.evolvable.UpdateEvolvableTokenFlowHandler
import com.r3.corda.lib.tokens.workflows.flows.evolvable.maintainers
import com.r3.corda.lib.tokens.workflows.flows.evolvable.otherMaintainers
import com.r3.corda.lib.tokens.workflows.flows.evolvable.participants
import com.r3.corda.lib.tokens.workflows.flows.evolvable.subscribersForState
import net.corda.core.contracts.StateAndRef
import net.corda.core.contracts.TransactionState
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.flows.InitiatedBy
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.StartableByRPC
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.transactions.SignedTransaction

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

@InitiatedBy(CreateEvolvableTokens::class)
class CreateEvolvableTokensHandler(val otherSession: FlowSession) : FlowLogic<SignedTransaction>() {
    @Suspendable
    override fun call() = subFlow(CreateEvolvableTokensFlowHandler(otherSession))
}


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

@InitiatedBy(UpdateEvolvableToken::class)
class UpdateEvolvableTokenHandler(val otherSession: FlowSession) : FlowLogic<SignedTransaction>() {
    @Suspendable
    override fun call() = subFlow(UpdateEvolvableTokenFlowHandler(otherSession))
}
