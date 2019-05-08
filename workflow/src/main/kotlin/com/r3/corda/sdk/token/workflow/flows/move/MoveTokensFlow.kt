package com.r3.corda.sdk.token.workflow.flows.move

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.sdk.token.contracts.states.AbstractToken
import com.r3.corda.sdk.token.contracts.types.TokenPointer
import com.r3.corda.sdk.token.contracts.types.TokenType
import com.r3.corda.sdk.token.workflow.flows.distribution.UpdateDistributionList
import com.r3.corda.sdk.token.workflow.flows.finality.FinalizeTokensTransactionFlow
import com.r3.corda.sdk.token.workflow.flows.finality.ObserverAwareFinalityFlow
import com.r3.corda.sdk.token.workflow.utilities.getPreferredNotary
import com.r3.corda.sdk.token.workflow.utilities.requireKnownConfidentialIdentity
import com.r3.corda.sdk.token.workflow.utilities.sessionsForParicipants
import net.corda.core.contracts.Amount
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.StartableByRPC
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder

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

    @Suspendable
    override fun call(): SignedTransaction {
        val outputs = transactionBuilder.outputStates().map { it.data }
        // Create new sessions if this is started as a top level flow.
        val sessions = if (existingSessions.isEmpty()) sessionsForParicipants(outputs, observers) else existingSessions
        return subFlow(FinalizeTokensTransactionFlow(transactionBuilder, sessions.toList()))
    }
}

// This one is supposed to be called from shell only, it could have been moved to MoveTokenFlow entirely, but... then it's messy because shell shows all the constructors in a list, and some don't make sense.
@InitiatingFlow
@StartableByRPC
class MakeMoveTokenFlow<T : TokenType>(
        val partiesAndAmounts: Map<AbstractParty, List<Amount<T>>>,
        val partiesAndTokens: Map<AbstractParty, List<T>> = emptyMap()
) : FlowLogic<SignedTransaction>() {
    constructor(holder: AbstractParty, token: T) : this(emptyMap(), mapOf(holder to listOf(token)))

    constructor(holder: AbstractParty, tokens: List<T>) : this(emptyMap(), mapOf(holder to tokens))

    constructor(holder: AbstractParty, amount: Amount<T>) : this(mapOf(holder to listOf(amount)), emptyMap())

    constructor(holder: AbstractParty, amounts: Set<Amount<T>>) : this(mapOf(holder to amounts.toList()), emptyMap())

    @Suspendable
    override fun call(): SignedTransaction {
        val transactionBuilder = TransactionBuilder(getPreferredNotary(serviceHub))
        for ((holder, amounts) in partiesAndAmounts) {
            for (amount in amounts) {
                addMoveTokens(amount, holder, transactionBuilder)
            }
        }
        for ((holder, tokens) in partiesAndTokens) {
            for (token in tokens) addMoveTokens(token, holder, transactionBuilder)
        }
        return subFlow(FinalizeMoveTokensFlow(transactionBuilder))
    }
}