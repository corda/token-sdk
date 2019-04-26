package com.r3.corda.sdk.token.workflow.flows

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.sdk.token.workflow.utilities.ourSigningKeys
import com.r3.corda.sdk.token.workflow.utilities.participants
import com.r3.corda.sdk.token.workflow.utilities.requireSessionsForParticipants
import com.r3.corda.sdk.token.workflow.utilities.toWellKnownParties
import net.corda.core.flows.FinalityFlow
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.serialization.CordaSerializable
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder

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
        val wellKnownParticipants = participants.toWellKnownParties(serviceHub) - ourIdentity
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