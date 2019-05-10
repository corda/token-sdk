package com.r3.corda.sdk.token.workflow.flows.move

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.sdk.token.contracts.types.TokenType
import com.r3.corda.sdk.token.workflow.flows.finality.FinalizeTokensTransactionFlow
import com.r3.corda.sdk.token.workflow.utilities.sessionsForParicipants
import net.corda.core.contracts.Amount
import net.corda.core.flows.FinalityFlow
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.flows.InitiatingFlow
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.ProgressTracker

// TODO Add inputs, outputs, query criteria (similar to addMove from MoveUtilities).
@InitiatingFlow
abstract class MoveTokensFlow<T : TokenType> private constructor(
        val partiesAndAmounts: Map<AbstractParty, List<Amount<T>>>,
        val partiesAndTokens: Map<AbstractParty, List<T>>,
        val existingSessions: Set<FlowSession>,
        val observers: Set<Party>
) : FlowLogic<SignedTransaction>() {
    /** Standard constructors. */
    @JvmOverloads
    constructor(partiesAndAmounts: Map<AbstractParty, List<Amount<T>>> = emptyMap(),
                partiesAndTokens: Map<AbstractParty, List<T>> = emptyMap(),
                existingSessions: List<FlowSession>) : this(partiesAndAmounts, partiesAndTokens, existingSessions.toSet(), emptySet())

    @JvmOverloads
    constructor(partiesAndAmounts: Map<AbstractParty, List<Amount<T>>> = emptyMap(),
                partiesAndTokens: Map<AbstractParty, List<T>> = emptyMap(),
                observers: Set<Party>) : this(partiesAndAmounts, partiesAndTokens, emptySet(), observers)

    @JvmOverloads
    constructor(partiesAndAmounts: Map<AbstractParty, List<Amount<T>>> = emptyMap(),
                partiesAndTokens: Map<AbstractParty, List<T>> = emptyMap(),
                session: FlowSession) : this(partiesAndAmounts, partiesAndTokens, setOf(session), emptySet())

    @JvmOverloads
    constructor(partiesAndAmounts: Map<AbstractParty, List<Amount<T>>> = emptyMap(),
                partiesAndTokens: Map<AbstractParty, List<T>> = emptyMap(),
                observer: Party) : this(partiesAndAmounts, partiesAndTokens, emptySet(), setOf(observer))

    @JvmOverloads
    constructor(partiesAndAmounts: Map<AbstractParty, List<Amount<T>>> = emptyMap(),
                partiesAndTokens: Map<AbstractParty, List<T>> = emptyMap()) : this(partiesAndAmounts, partiesAndTokens, emptySet(), emptySet())

    /** Some more constructors for fungible/non-fungible. with sessions **/
    constructor(holder: AbstractParty, token: T, existingSessions: Set<FlowSession>)
            : this(emptyMap(), mapOf(holder to listOf(token)), existingSessions, emptySet())

    constructor(holder: AbstractParty, tokens: List<T>, existingSessions: Set<FlowSession>)
            : this(emptyMap(), mapOf(holder to tokens), existingSessions, emptySet())

    constructor(holder: AbstractParty, amount: Amount<T>, existingSessions: Set<FlowSession>)
            : this(mapOf(holder to listOf(amount)), emptyMap(), existingSessions, emptySet())

    constructor(holder: AbstractParty, amounts: Set<Amount<T>>, existingSessions: Set<FlowSession>)
            : this(mapOf(holder to amounts.toList()), emptyMap(), existingSessions, emptySet())

    //TODO fix progress tracker
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
    override fun call(): SignedTransaction {
        progressTracker.currentStep = GENERATE_MOVE
        val transactionBuilder = generateMove(partiesAndAmounts, partiesAndTokens)
        progressTracker.currentStep = RECORDING
        val outputs = transactionBuilder.outputStates().map { it.data }
        // Create new sessions if this is started as a top level flow.
        val sessions = if (existingSessions.isEmpty()) sessionsForParicipants(outputs, observers) else existingSessions
        return subFlow(FinalizeTokensTransactionFlow(transactionBuilder, sessions.toList()))
    }
}
