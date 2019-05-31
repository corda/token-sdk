package com.r3.corda.sdk.token.workflow.flows.redeem

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.sdk.token.contracts.states.AbstractToken
import com.r3.corda.sdk.token.contracts.types.TokenType
import net.corda.core.contracts.StateAndRef
import net.corda.core.flows.FlowSession
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.StartableByRPC
import net.corda.core.transactions.TransactionBuilder

/**
 * TODO docs
 */
// Called on owner side.
@InitiatingFlow
@StartableByRPC
class RedeemTokensFlow<T : TokenType>
@JvmOverloads
constructor(
        val inputs: List<StateAndRef<AbstractToken<T>>>,
        val changeOutput: AbstractToken<T>?,
        override val issuerSession: FlowSession,
        override val observerSessions: List<FlowSession> = emptyList()
) : AbstractRedeemTokensFlow() {
    @Suspendable
    override fun generateExit(transactionBuilder: TransactionBuilder): TransactionBuilder {
        return addRedeemTokens(transactionBuilder, inputs, changeOutput)
    }
}
