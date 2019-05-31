package com.r3.corda.sdk.token.workflow.flows.redeem

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.sdk.token.contracts.states.AbstractToken
import com.r3.corda.sdk.token.workflow.flows.finality.ObserverAwareFinalityFlow
import com.r3.corda.sdk.token.workflow.utilities.ourSigningKeys
import net.corda.confidential.IdentitySyncFlow
import net.corda.core.contracts.StateAndRef
import net.corda.core.flows.CollectSignaturesFlow
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker

abstract class AbstractRedeemTokensFlow : FlowLogic<SignedTransaction>() {
    abstract val issuerSession: FlowSession
    abstract val observerSessions: List<FlowSession>

    // TODO update progress tracker
    companion object {
        object REDEEM_NOTIFICATION : ProgressTracker.Step("Sending redeem notification to tokenHolders.")
        //        object CONF_ID : ProgressTracker.Step("Requesting confidential identity.")
        object SELECTING_STATES : ProgressTracker.Step("Selecting states to redeem.")

        object SEND_STATE_REF : ProgressTracker.Step("Sending states to the issuer for redeeming.")
        object SYNC_IDS : ProgressTracker.Step("Synchronising confidential identities.")
        object SIGNING_TX : ProgressTracker.Step("Signing transaction")
        object FINALISING_TX : ProgressTracker.Step("Finalising transaction")

        fun tracker() = ProgressTracker(REDEEM_NOTIFICATION, SELECTING_STATES, SEND_STATE_REF, SYNC_IDS, SIGNING_TX, FINALISING_TX)
    }

    override val progressTracker: ProgressTracker = tracker()

    abstract fun generateExit(transactionBuilder: TransactionBuilder): TransactionBuilder

    //TODO add progress tracker
    @Suspendable
    override fun call(): SignedTransaction {
        val txBuilder = TransactionBuilder()
        generateExit(txBuilder)
        // First synchronise identities between issuer and our states.
        subFlow(IdentitySyncFlow.Send(issuerSession, txBuilder.toWireTransaction(serviceHub)))
        // Call collect signatures flow, issuer should perform all the checks for redeeming states.
        val ourSigningKeys = txBuilder.toLedgerTransaction(serviceHub).ourSigningKeys(serviceHub)
        val partialStx = serviceHub.signInitialTransaction(txBuilder, ourSigningKeys)
        val stx = subFlow(CollectSignaturesFlow(partialStx, listOf(issuerSession), ourSigningKeys))
        return subFlow(ObserverAwareFinalityFlow(stx, observerSessions + issuerSession))
    }
}
