package com.r3.corda.lib.tokens.workflows.flows.redeem

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.lib.tokens.contracts.states.AbstractToken
import com.r3.corda.lib.tokens.contracts.types.TokenType
import net.corda.core.contracts.StateAndRef
import net.corda.core.flows.FlowSession
import net.corda.core.transactions.TransactionBuilder

/**
 * General inlined flow used to redeem any type of tokens with the issuer. Should be called on tokens' owner side.
 * Notice that token selection and change output generation should be done beforehand. This flow builds a transaction
 * containing those states, but all checks should have be done before calling this flow as a subflow.
 * It can only be called for one [TokenType] at a time. If you need to do multiple token types in one transaction then create a new
 * flow, calling [addTokensToRedeem] for each token type.
 *
 * @param inputs list of token inputs to redeem
 * @param changeOutput possible change output to be paid back to the tokens owner
 * @param issuerSession session with the issuer of the tokens
 * @param observerSessions session with optional observers of the redeem transaction
 */
// Called on owner side.
class RedeemTokensFlow
@JvmOverloads
constructor(
	val inputs: List<StateAndRef<AbstractToken>>,
	val changeOutput: AbstractToken?,
	override val issuerSession: FlowSession,
	override val observerSessions: List<FlowSession> = emptyList()
) : AbstractRedeemTokensFlow() {
	@Suspendable
	override fun generateExit(transactionBuilder: TransactionBuilder) {
		addTokensToRedeem(transactionBuilder, inputs, changeOutput)
	}
}
