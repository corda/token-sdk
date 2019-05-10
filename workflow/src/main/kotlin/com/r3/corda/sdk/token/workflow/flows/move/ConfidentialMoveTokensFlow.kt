package com.r3.corda.sdk.token.workflow.flows.move

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.sdk.token.contracts.states.AbstractToken
import com.r3.corda.sdk.token.contracts.types.TokenType
import com.r3.corda.sdk.token.workflow.flows.confidential.ConfidentialTokensFlow
import com.r3.corda.sdk.token.workflow.flows.confidential.AnonymisePartiesFlow
import com.r3.corda.sdk.token.workflow.flows.finality.FinalizeTokensTransactionFlow
import com.r3.corda.sdk.token.workflow.utilities.getPreferredNotary
import com.r3.corda.sdk.token.workflow.utilities.sessionsForParicipants
import net.corda.core.contracts.Amount
import net.corda.core.contracts.StateAndRef
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.flows.InitiatingFlow
import net.corda.core.identity.Party
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder

// TODO Refactor this further to simplify token selection handling both in this flow and in MoveTokensFlow.
// TODO can be called with either inputs-outputs or with parties-amounts/tokens
@InitiatingFlow
class ConfidentialMoveTokensFlow<T : TokenType> private constructor(
        val inputs: List<StateAndRef<AbstractToken<T>>>,
        val outputs: List<AbstractToken<T>>,
        val partiesAndAmounts: Map<Party, List<Amount<T>>>,
        val partiesAndTokens: Map<Party, List<T>>,
        val existingSessions: Set<FlowSession>) : FlowLogic<SignedTransaction>() {

    @JvmOverloads
    constructor(token: T, holder: Party, sessions: Set<FlowSession> = emptySet())
            : this(emptyList(), emptyList(), emptyMap(), mapOf(holder to listOf(token)), sessions)

    @JvmOverloads
    constructor(amount: Amount<T>, holder: Party, sessions: Set<FlowSession> = emptySet())
            : this(emptyList(), emptyList(), mapOf(holder to listOf(amount)), emptyMap(), sessions)

    //TODO add constructor with parties and amounts?

    @JvmOverloads
    constructor(inputs: List<StateAndRef<AbstractToken<T>>>, outputs: List<AbstractToken<T>>, sessions: Set<FlowSession> = emptySet())
            : this(inputs, outputs, emptyMap(), emptyMap(), sessions)

    @Suspendable
    override fun call(): SignedTransaction {
        val transactionBuilder = TransactionBuilder(getPreferredNotary(serviceHub))
        //TODO we have to add sessions for all parties we anonymise
        val otherParties = partiesAndAmounts.keys + partiesAndTokens.keys
        val sessions = if (existingSessions.isEmpty()) sessionsForParicipants(outputs, otherParties) else existingSessions
        if (partiesAndAmounts.isNotEmpty() || partiesAndTokens.isNotEmpty()) {
            val anonymisedParties = subFlow(AnonymisePartiesFlow(otherParties, sessions))
            val anonymousPartiesAndAmounts = partiesAndAmounts.mapKeys { (party, _) -> anonymisedParties[party] ?:  throw IllegalStateException("Missing anonymous party for $party.")}
            val anonymousPartiesAndTokens = partiesAndTokens.mapKeys { (party, _) -> anonymisedParties[party] ?:  throw IllegalStateException("Missing anonymous party for $party.")}
            generateMove(anonymousPartiesAndAmounts, anonymousPartiesAndTokens, transactionBuilder)
        } else if (inputs.isNotEmpty()) {
            // Get all proposed holders.
            val confidentialTokens = subFlow(ConfidentialTokensFlow(outputs, sessions))
            addMoveTokens(inputs, confidentialTokens, transactionBuilder)
        }
        return subFlow(FinalizeTokensTransactionFlow(transactionBuilder, sessions.toList()))
    }
}
