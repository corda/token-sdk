package com.r3.corda.sdk.token.workflow.flows.finality

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.sdk.token.workflow.flows.internal.finality.TransactionRole
import com.r3.corda.sdk.token.workflow.utilities.ourSigningKeys
import com.r3.corda.sdk.token.workflow.utilities.participants
import com.r3.corda.sdk.token.workflow.utilities.requireSessionsForParticipants
import com.r3.corda.sdk.token.workflow.utilities.toWellKnownParties
import net.corda.core.flows.FinalityFlow
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.transactions.LedgerTransaction
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder

/**
 * This flow is a wrapper around [FinalityFlow] and properly handles broadcasting transactions to observers (those which
 * are not transaction participants) by amending the [StatesToRecord] level based upon the role. Those which are not
 * participants in any of the states must invoke [FinalityFlow] with [StatesToRecord] set to ALL_VISIBLE, otherwise they
 * will not store any of the states. Those which are participants record the transaction as usual. This does mean that
 * there is an "all or nothing" approach to storing outputs for observers, so if there are privacy concerns, then it is
 * best to split state issuance up for different token holders in separate flow invocations.
 *
 * @property transactionBuilder the transaction builder to finalise
 * @property allSessions a set of sessions for, at least, all the transaction participants and maybe observers
 */
class ObserverAwareFinalityFlow(
        val transactionBuilder: TransactionBuilder,
        val allSessions: List<FlowSession>
) : FlowLogic<SignedTransaction>() {
    @Suspendable
    override fun call(): SignedTransaction {
        // Check there is a session for each participant, apart from the node itself.
        val ledgerTransaction: LedgerTransaction = transactionBuilder.toLedgerTransaction(serviceHub)
        val participants: List<AbstractParty> = ledgerTransaction.participants
        val wellKnownParticipants: Set<Party> = participants.toWellKnownParties(serviceHub).toSet()
        val wellKnownParticipantsApartFromUs: Set<Party> = wellKnownParticipants - ourIdentity
        // We need participantSessions for all participants apart from us.
        requireSessionsForParticipants(wellKnownParticipantsApartFromUs, allSessions)
        val finalSessions = allSessions.filter { it.counterparty != ourIdentity }
        // Notify all session counterparties of their role. Observers store the transaction using
        // StatesToRecord.ALL_VISIBLE, participants store the transaction using StatesToRecord.ONLY_RELEVANT.
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