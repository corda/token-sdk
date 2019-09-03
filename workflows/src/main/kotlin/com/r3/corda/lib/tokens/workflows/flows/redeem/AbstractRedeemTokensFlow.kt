package com.r3.corda.lib.tokens.workflows.flows.redeem

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.lib.ci.SyncKeyMappingFlow
import com.r3.corda.lib.tokens.workflows.internal.flows.finality.ObserverAwareFinalityFlow
import com.r3.corda.lib.tokens.workflows.internal.flows.finality.TransactionRole
import com.r3.corda.lib.tokens.workflows.utilities.ourSigningKeys
import net.corda.core.flows.CollectSignaturesFlow
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker

/**
 * Abstract class for the redeem token flows family.
 * You must provide [issuerSession] and optional [observerSessions] for finalization. Override [generateExit] to select
 * tokens for redeeming.
 * The flow performs basic tasks, generates redeem transaction proposal for the issuer, synchronises any confidential
 * identities from the states to redeem with the issuer (bear in mind that issuer usually isn't involved in move of tokens),
 * collects signatures and finalises transaction with observers if present.
 */
abstract class AbstractRedeemTokensFlow : FlowLogic<SignedTransaction>() {

    abstract val issuerSession: FlowSession
    abstract val observerSessions: List<FlowSession>

    companion object {
        object SELECTING_STATES : ProgressTracker.Step("Selecting states to redeem.")
        object SYNC_IDS : ProgressTracker.Step("Synchronising confidential identities.")
        object COLLECT_SIGS : ProgressTracker.Step("Collecting signatures")
        object FINALISING_TX : ProgressTracker.Step("Finalising transaction")

        fun tracker() = ProgressTracker(SELECTING_STATES, SYNC_IDS, COLLECT_SIGS, FINALISING_TX)
    }

    override val progressTracker: ProgressTracker = tracker()

    /**
     * Add redeem of tokens to the [transactionBuilder]. Modifies builder.
     */
    @Suspendable
    abstract fun generateExit(transactionBuilder: TransactionBuilder)

    @Suspendable
    override fun call(): SignedTransaction {
        issuerSession.send(TransactionRole.PARTICIPANT)
        observerSessions.forEach { it.send(TransactionRole.OBSERVER) }
        val txBuilder = TransactionBuilder()
        progressTracker.currentStep = SELECTING_STATES
        generateExit(txBuilder)
        // First synchronise identities between issuer and our states.
        // TODO: Only do this if necessary.
        progressTracker.currentStep = SYNC_IDS
        subFlow(SyncKeyMappingFlow(issuerSession, txBuilder.toWireTransaction(serviceHub)))
        val ourSigningKeys = txBuilder.toLedgerTransaction(serviceHub).ourSigningKeys(serviceHub)
        val partialStx = serviceHub.signInitialTransaction(txBuilder, ourSigningKeys)
        // Call collect signatures flow, issuer should perform all the checks for redeeming states.
        progressTracker.currentStep = COLLECT_SIGS
        val stx = subFlow(CollectSignaturesFlow(partialStx, listOf(issuerSession), ourSigningKeys))
        progressTracker.currentStep = FINALISING_TX
        return subFlow(ObserverAwareFinalityFlow(stx, observerSessions + issuerSession))
    }
}
