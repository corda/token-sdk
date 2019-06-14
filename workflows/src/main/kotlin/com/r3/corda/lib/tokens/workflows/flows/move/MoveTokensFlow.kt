package com.r3.corda.lib.tokens.workflows.flows.move

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.lib.tokens.contracts.states.AbstractToken
import com.r3.corda.lib.tokens.contracts.types.TokenType
import net.corda.core.contracts.StateAndRef
import net.corda.core.flows.FlowSession
import net.corda.core.transactions.TransactionBuilder

/**
 * General inlined flow used to move any type of tokens. This flow builds a transaction containing passed as parameters
 * input and output states, but all checks should have be done before calling this flow as a subflow.
 *
 * It can only be called for one [TokenType] at a time. If you need to do multiple token types in one transaction then
 * create a new flow, calling [addMoveTokens] for each token type.
 *
 * @param inputs list of token inputs to move
 * @param outputs list of result token outputs
 * @param participantSessions session with the participants of move tokens transaction
 * @param observerSessions session with optional observers of the redeem transaction
 */
class MoveTokensFlow<T : TokenType>
@JvmOverloads
constructor(
        val inputs: List<StateAndRef<AbstractToken<T>>>,
        val outputs: List<AbstractToken<T>>,
        override val participantSessions: List<FlowSession>,
        override val observerSessions: List<FlowSession> = emptyList()
) : AbstractMoveTokensFlow() {
    @JvmOverloads
    constructor(
            input: StateAndRef<AbstractToken<T>>,
            output: AbstractToken<T>,
            participantSessions: List<FlowSession>,
            observerSessions: List<FlowSession> = emptyList()
    ) : this(listOf(input), listOf(output), participantSessions, observerSessions)

    @Suspendable
    override fun addMove(transactionBuilder: TransactionBuilder) {
        addMoveTokens(transactionBuilder, inputs, outputs)
    }
}
