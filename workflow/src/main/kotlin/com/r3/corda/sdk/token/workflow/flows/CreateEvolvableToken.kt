package com.r3.corda.sdk.token.workflow.flows

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.sdk.token.contracts.states.EvolvableTokenType
import com.r3.corda.sdk.token.contracts.utilities.withNotary
import com.r3.corda.sdk.token.workflow.utilities.addCreateEvolvableToken
import net.corda.core.contracts.ContractClassName
import net.corda.core.contracts.TransactionState
import net.corda.core.flows.*
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.serialization.CordaSerializable
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker

/**
 * Flow for creating an evolvable token type. This is just a simple flow for now. Although it can be invoked via the
 * shell, it is more likely to be used for unit testing or called as an inlined-subflow.
 */
@InitiatingFlow
@StartableByRPC
class CreateEvolvableToken<T : EvolvableTokenType>(
        val transactionState: TransactionState<T>
) : FlowLogic<SignedTransaction>() {

    constructor(evolvableToken: T, contract: ContractClassName, notary: Party)
            : this(TransactionState(evolvableToken, contract, notary))

    constructor(evolvableToken: T, notary: Party) : this(evolvableToken withNotary notary)

    @CordaSerializable
    data class Notification(val signatureRequired: Boolean = false)

    companion object {
        object CREATING : ProgressTracker.Step("Creating transaction proposal.")

        object SIGNING : ProgressTracker.Step("Signing transaction proposal.")

        object COLLECTING : ProgressTracker.Step("Gathering counterparty signature.") {
            override fun childProgressTracker() = CollectSignaturesFlow.tracker()
        }

        object RECORDING : ProgressTracker.Step("Recording signed transaction.") {
            override fun childProgressTracker() = FinalityFlow.tracker()
        }

        fun tracker() = ProgressTracker(CREATING, SIGNING, COLLECTING, RECORDING)
    }

    override val progressTracker: ProgressTracker = tracker()

    @Suspendable
    override fun call(): SignedTransaction {
        // Create a transaction which updates the ledger with the new evolvable token.
        progressTracker.currentStep = CREATING
        val utx = addCreateEvolvableToken(TransactionBuilder(), transactionState)

        // Sign the transaction proposal
        progressTracker.currentStep = SIGNING
        val stx: SignedTransaction = serviceHub.signInitialTransaction(utx)

        // Gather signatures from other maintainers
        progressTracker.currentStep = COLLECTING
        val otherMaintainerSessions = otherMaintainers().map { initiateFlow(it) }
        otherMaintainerSessions.forEach { it.send(Notification(signatureRequired = true)) }
        val tx = subFlow(CollectSignaturesFlow(
                partiallySignedTx = stx,
                sessionsToCollectFrom = otherMaintainerSessions,
                progressTracker = COLLECTING.childProgressTracker()
        ))

        // Finalise with all participants, including maintainers, participants, and subscribers (via distribution list)
        progressTracker.currentStep = RECORDING
        val observerSessions = wellKnownObservers().map { initiateFlow(it) }
        observerSessions.forEach { it.send(Notification(signatureRequired = false)) }
        return subFlow(FinalityFlow(
                transaction = tx,
                sessions = (otherMaintainerSessions + observerSessions),
                progressTracker = RECORDING.childProgressTracker()
        ))
    }

    private fun maintainers(): Set<Party> {
        return transactionState.data.maintainers.toSet()
    }

    private fun otherMaintainers(): Set<Party> {
        return maintainers().minus(this.ourIdentity)
    }

    private fun participants(): Set<AbstractParty> {
        return transactionState.data.participants.toSet()
    }

    private fun observers(): Set<AbstractParty> {
        return participants().minus(maintainers()).minus(this.ourIdentity)
    }

    private fun wellKnownObservers(): List<Party> {
        return observers().map { serviceHub.identityService.wellKnownPartyFromAnonymous(it)!! }
    }
}
