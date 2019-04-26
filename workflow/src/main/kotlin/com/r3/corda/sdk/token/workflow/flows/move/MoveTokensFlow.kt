package com.r3.corda.sdk.token.workflow.flows.move

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.sdk.token.contracts.states.AbstractToken
import com.r3.corda.sdk.token.contracts.types.TokenPointer
import com.r3.corda.sdk.token.contracts.types.TokenType
import com.r3.corda.sdk.token.workflow.flows.ObserverAwareFinalityFlow
import com.r3.corda.sdk.token.workflow.flows.distribution.UpdateDistributionList
import com.r3.corda.sdk.token.workflow.utilities.getPreferredNotary
import com.r3.corda.sdk.token.workflow.utilities.requireKnownConfidentialIdentity
import com.r3.corda.sdk.token.workflow.utilities.sessionsForParicipants
import net.corda.core.contracts.Amount
import net.corda.core.flows.FinalityFlow
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.StartableByRPC
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker
import java.security.PublicKey

// So usage could be for DvP
// txB = addMove(sth)
// addMove(txB, anotherSth)
// subFlow(FinalizeMoveTokenFlow, txB, sessions, observers))
@InitiatingFlow
class FinalizeMoveTokensFlow private constructor(
        val transactionBuilder: TransactionBuilder,
        val existingSessions: Set<FlowSession>,
        val observers: Set<Party>
) : FlowLogic<SignedTransaction>() {

    /** Standard constructors. */
    constructor(transactionBuilder: TransactionBuilder, existingSessions: List<FlowSession>) : this(transactionBuilder, existingSessions.toSet(), emptySet())

    constructor(transactionBuilder: TransactionBuilder, observers: Set<Party>) : this(transactionBuilder, emptySet(), observers)

    constructor(transactionBuilder: TransactionBuilder, session: FlowSession) : this(transactionBuilder, setOf(session), emptySet())

    constructor(transactionBuilder: TransactionBuilder, observer: Party) : this(transactionBuilder, emptySet(), setOf(observer))

    constructor(transactionBuilder: TransactionBuilder) : this(transactionBuilder, emptySet(), emptySet())

    companion object {
        object RECORDING : ProgressTracker.Step("Recording signed transaction.") {
            override fun childProgressTracker() = FinalityFlow.tracker()
        }

        object UPDATE_DIST : ProgressTracker.Step("Updating distribution list.")

        fun tracker() = ProgressTracker(RECORDING, UPDATE_DIST)
    }

    override val progressTracker: ProgressTracker = tracker()

    @Suspendable
    override fun call(): SignedTransaction {
        progressTracker.currentStep = RECORDING
        val outputs = transactionBuilder.outputStates().map { it.data }
        // Create new sessions if this is started as a top level flow.
        val sessions = if (existingSessions.isEmpty()) sessionsForParicipants(outputs, observers) else existingSessions
        // Determine which parties are participants and observers.
        val finalTx = subFlow(ObserverAwareFinalityFlow(transactionBuilder, sessions))

        // TODO move it to UpdateDistributionList after refactor of that flow!!!
        progressTracker.currentStep = UPDATE_DIST
        for (output in outputs) {
            if (output is AbstractToken<*>) {
                val token = output.tokenType
                val holderParty = serviceHub.identityService.requireKnownConfidentialIdentity(output.holder) // TODO refactor
                if (token is TokenPointer<*>) {
                    subFlow(UpdateDistributionList.Initiator(token, ourIdentity, holderParty))
                }
            }
        }
        return finalTx
    }
}


@InitiatingFlow
abstract class MoveTokensFlow<T : TokenType>(
        val token: T,
        val holder: AbstractParty,
        val session: FlowSession? = null
) : FlowLogic<SignedTransaction>() {
    companion object {
        object GENERATE_MOVE : ProgressTracker.Step("Generating tokens move.")
        object SIGNING : ProgressTracker.Step("Signing transaction proposal.")
        object RECORDING : ProgressTracker.Step("Recording signed transaction.") {
            override fun childProgressTracker() = FinalityFlow.tracker()
        }

        fun tracker() = ProgressTracker(GENERATE_MOVE, SIGNING, RECORDING)
    }

    override val progressTracker: ProgressTracker = tracker()

    @Suspendable
    abstract fun generateMove(): Pair<TransactionBuilder, List<PublicKey>>

    @Suspendable
    override fun call(): SignedTransaction {
        // TODO extract into some utils function
        val holderParty = serviceHub.identityService.requireKnownConfidentialIdentity(holder)
        val holderSession = if (session == null) initiateFlow(holderParty) else session

        progressTracker.currentStep = GENERATE_MOVE

        val (builder, keys) = generateMove()

        progressTracker.currentStep = SIGNING
        // WARNING: At present, the recipient will not be signed up to updates from the token maintainer.
        val stx: SignedTransaction = serviceHub.signInitialTransaction(builder, keys)
        progressTracker.currentStep = RECORDING
        val sessions = if (ourIdentity == holderParty) emptyList() else listOf(holderSession)
        val finalTx = subFlow(FinalityFlow(transaction = stx, sessions = sessions))
        // If it's TokenPointer, then update the distribution lists for token maintainers
        if (token is TokenPointer<*>) {
            subFlow(UpdateDistributionList.Initiator(token, ourIdentity, holderParty))
        }
        return finalTx
    }
}

// This one is supposed to be called from shell only, it could have been moved to MoveTokenFlow entirely, but... then it's messy because shell shows all the constructors in a list, and some don't make sense.
@InitiatingFlow
@StartableByRPC
class MakeMoveTokenFlow<T : TokenType> private constructor(
        val holder: AbstractParty, // TODO maybe API from shell that moves to many parties?
        val tokens: List<T>,
        val amounts: List<Amount<T>>
) : FlowLogic<SignedTransaction>() {
    constructor(holder: AbstractParty, token: T) : this(holder, listOf(token), emptyList())

    constructor(holder: AbstractParty, tokens: List<T>) : this(holder, tokens, emptyList())

    constructor(holder: AbstractParty, amount: Amount<T>) : this(holder, listOf(), listOf(amount))

    constructor(holder: AbstractParty, amounts: Set<Amount<T>>) : this(holder, listOf(), amounts.toList())
    // todo add map party -> amount

    @Suspendable
    override fun call(): SignedTransaction {
        val transactionBuilder = TransactionBuilder(getPreferredNotary(serviceHub))
        for (amount in amounts) {
            addMoveTokens(serviceHub, amount, holder, transactionBuilder)
        }
        for (token in tokens) {
            addMoveTokens(serviceHub, token, holder, transactionBuilder)
        }
        return subFlow(FinalizeMoveTokensFlow(transactionBuilder))
    }
}