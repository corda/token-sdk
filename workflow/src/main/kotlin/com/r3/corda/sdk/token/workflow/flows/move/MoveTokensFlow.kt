package com.r3.corda.sdk.token.workflow.flows.move

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.sdk.token.contracts.states.AbstractToken
import com.r3.corda.sdk.token.contracts.types.TokenType
import net.corda.core.contracts.StateAndRef
import net.corda.core.flows.FinalityFlow
import net.corda.core.flows.FlowSession
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker

class MoveTokensFlow<T : TokenType>(
        val inputs: List<StateAndRef<AbstractToken<T>>>,
        val outputs: List<AbstractToken<T>>,
        override val participantSessions: List<FlowSession>,
        override val observerSessions: List<FlowSession>
) : AbstractMoveTokensFlow() {

    constructor(
            input: StateAndRef<AbstractToken<T>>,
            output: AbstractToken<T>,
            participantSessions: List<FlowSession>,
            observerSessions: List<FlowSession>
    ) : this(listOf(input), listOf(output), participantSessions, observerSessions)

    companion object {
        object GENERATE : ProgressTracker.Step("Generating tokensToIssue move.")
        object SIGNING : ProgressTracker.Step("Signing transaction proposal.")
        object RECORDING : ProgressTracker.Step("Recording signed transaction.") {
            override fun childProgressTracker() = FinalityFlow.tracker()
        }

        fun tracker() = ProgressTracker(GENERATE, SIGNING, RECORDING)
    }

    override val progressTracker: ProgressTracker = tracker()

    @Suspendable
    override fun addMove(transactionBuilder: TransactionBuilder) {
        addMoveTokens(transactionBuilder, inputs, outputs)
    }

}
