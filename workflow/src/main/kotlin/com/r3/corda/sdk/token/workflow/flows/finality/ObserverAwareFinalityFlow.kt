package com.r3.corda.sdk.token.workflow.flows.finality

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.sdk.token.contracts.commands.IssueTokenCommand
import com.r3.corda.sdk.token.contracts.commands.MoveTokenCommand
import com.r3.corda.sdk.token.contracts.states.AbstractToken
import com.r3.corda.sdk.token.contracts.types.TokenType
import com.r3.corda.sdk.token.workflow.utilities.addToDistributionList
import com.r3.corda.sdk.token.workflow.utilities.ourSigningKeys
import com.r3.corda.sdk.token.workflow.utilities.participants
import com.r3.corda.sdk.token.workflow.utilities.requireSessionsForParticipants
import com.r3.corda.sdk.token.workflow.utilities.sessionsForParicipants
import com.r3.corda.sdk.token.workflow.utilities.toWellKnownParties
import com.r3.corda.sdk.token.workflow.utilities.updateDistributionList
import net.corda.core.contracts.CommandWithParties
import net.corda.core.flows.FinalityFlow
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.flows.InitiatingFlow
import net.corda.core.identity.Party
import net.corda.core.serialization.CordaSerializable
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker

@CordaSerializable
enum class TransactionRole { PARTICIPANT, OBSERVER }

class ObserverAwareFinalityFlow(
        val transactionBuilder: TransactionBuilder,
        val sessions: Set<FlowSession>
) : FlowLogic<SignedTransaction>() {
    @Suspendable
    override fun call(): SignedTransaction {
        // Check there is a session for each participant, apart from the node itself.
        val ledgerTransaction = transactionBuilder.toLedgerTransaction(serviceHub)
        val participants = ledgerTransaction.participants
        val wellKnownParticipants = participants.toWellKnownParties(serviceHub).toSet().minus(ourIdentity)
        requireSessionsForParticipants(wellKnownParticipants, sessions)
        val finalSessions = sessions.filter { it.counterparty != ourIdentity }
        // Notify all session counterparties of their role.
        finalSessions.forEach { session ->
            if (session.counterparty in wellKnownParticipants) session.send(TransactionRole.PARTICIPANT)
            else session.send(TransactionRole.OBSERVER)
        }
        // Sign and finalise the transaction, obtaining the signing keys required from the LedgerTransaction.
        val ourSigningKeys = ledgerTransaction.ourSigningKeys(serviceHub)
        val signedTransaction = serviceHub.signInitialTransaction(transactionBuilder, signingPubKeys = ourSigningKeys)

        return subFlow(FinalityFlow(transaction = signedTransaction, sessions = finalSessions))
    }
}

// Observer aware finality flow that also updates the distribution lists accordingly.
class FinalizeTokensTransactionFlow private constructor(
        val transactionBuilder: TransactionBuilder,
        val existingSessions: Set<FlowSession>,
        val observers: Set<Party>
) : FlowLogic<SignedTransaction>() {
    constructor(transactionBuilder: TransactionBuilder, existingSessions: List<FlowSession>) : this(transactionBuilder, existingSessions.toSet(), emptySet())
    constructor(transactionBuilder: TransactionBuilder, observers: Set<Party>) : this(transactionBuilder, emptySet(), observers)

    companion object {
        object ADD_DIST_LIST : ProgressTracker.Step("Adding to distribution list.")
        object UPDATE_DIST_LIST : ProgressTracker.Step("Updating distribution list.")
        object RECORDING : ProgressTracker.Step("Recording tokens transaction.") {
            override fun childProgressTracker() = FinalityFlow.tracker()
        }

        fun tracker() = ProgressTracker(RECORDING, ADD_DIST_LIST, UPDATE_DIST_LIST)
    }

    override val progressTracker: ProgressTracker = tracker()

    @Suspendable
    override fun call(): SignedTransaction {
        val issueCmds = transactionBuilder.commands().filterIsInstance<IssueTokenCommand<TokenType>>()
        val moveCmds = transactionBuilder.commands().filterIsInstance<MoveTokenCommand<TokenType>>()
        val tokens: List<AbstractToken<TokenType>> = transactionBuilder.outputStates().map { it.data }.filterIsInstance<AbstractToken<TokenType>>()
        // Create new sessions if this is started as a top level flow.
        val sessions = if (existingSessions.isEmpty()) sessionsForParicipants(tokens, observers) else existingSessions
        progressTracker.currentStep = RECORDING
        // Determine which parties are participants and observers.
        val finalTx =  subFlow(ObserverAwareFinalityFlow(transactionBuilder, sessions))
        // Update the distribution list. This adds all proposed token holders to the distribution list for the token
        // type they are receiving. Observers are not currently added to the distribution list.
        if (issueCmds.isNotEmpty()) {
            val issueTypes = issueCmds.map { it.token.tokenType }
            progressTracker.currentStep = ADD_DIST_LIST
            val issueStates: List<AbstractToken<TokenType>> = tokens.filter { it.tokenType in issueTypes }
            addToDistributionList(issueStates)
        }
        if (moveCmds.isNotEmpty()) {
            val moveTypes = moveCmds.map { it.token.tokenType }
            progressTracker.currentStep = UPDATE_DIST_LIST
            val moveStates: List<AbstractToken<TokenType>> = tokens.filter { it.tokenType in moveTypes }
            updateDistributionList(moveStates)
        }
        return finalTx
    }
}