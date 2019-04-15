package com.r3.corda.sdk.token.workflow.flows

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.sdk.token.contracts.states.EvolvableTokenType
import com.r3.corda.sdk.token.workflow.utilities.addUpdateEvolvableToken
import com.r3.corda.sdk.token.workflow.utilities.getDistributionList
import net.corda.core.contracts.StateAndRef
import net.corda.core.flows.*
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.serialization.CordaSerializable
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker

@InitiatingFlow
@StartableByRPC
class UpdateEvolvableToken(
        val oldStateAndRef: StateAndRef<EvolvableTokenType>,
        val newState: EvolvableTokenType
) : FlowLogic<SignedTransaction>() {

    companion object {
        object CREATING : ProgressTracker.Step("Creating update evolvable token transaction proposal.")

        object SIGNING : ProgressTracker.Step("Signing transaction proposal.")

        object COLLECTING : ProgressTracker.Step("Gathering counterparty signatures.") {
            override fun childProgressTracker() = CollectSignaturesFlow.tracker()
        }

        object RECORDING : ProgressTracker.Step("Recording signed transaction.") {
            override fun childProgressTracker() = FinalityFlow.tracker()
        }

        fun tracker() = ProgressTracker(CREATING, SIGNING, COLLECTING, RECORDING)
    }

    override val progressTracker: ProgressTracker = tracker()

    /**
     * Simple notification class to inform counterparties of their role. In this instance, informs participants if
     * they are required to sign the command. This is intended to allow maintainers to sign commands while participants
     * and other observers merely finalise the transaction.
     */
    @CordaSerializable
    data class Notification(val signatureRequired: Boolean = false)

    @Suspendable
    override fun call(): SignedTransaction {
        // Create a transaction which updates the ledger with the new evolvable token.
        // The parties listed as maintainers in the old state should be the signers.
        // TODO Should this be both old and new maintainer lists?
        progressTracker.currentStep = CREATING
        val utx = addUpdateEvolvableToken(
                TransactionBuilder(notary = oldStateAndRef.state.notary),
                oldStateAndRef,
                newState
        )

        // Sign the transaction proposal (creating a partially signed transaction, or ptx)
        progressTracker.currentStep = SIGNING
        val ptx: SignedTransaction = serviceHub.signInitialTransaction(utx)

        // Gather signatures from other maintainers
        progressTracker.currentStep = COLLECTING
        val otherMaintainerSessions = otherMaintainers().map { initiateFlow(it) }
        otherMaintainerSessions.forEach { it.send(Notification(signatureRequired = true)) }
        val stx = subFlow(CollectSignaturesFlow(
                partiallySignedTx = ptx,
                sessionsToCollectFrom = otherMaintainerSessions,
                progressTracker = COLLECTING.childProgressTracker()
        ))

        // Distribute to all observers, including maintainers, participants, and subscribers (via distribution list)
        progressTracker.currentStep = RECORDING
        val observerSessions = wellKnownObservers().map { initiateFlow(it) }
        observerSessions.forEach { it.send(Notification(signatureRequired = false)) }
        return subFlow(FinalityFlow(
                transaction = stx,
                sessions = (otherMaintainerSessions + observerSessions),
                progressTracker = RECORDING.childProgressTracker()
        ))
    }

    private val oldState get() = oldStateAndRef.state.data

    private fun maintainers(): Set<Party> {
        return (oldState.maintainers + newState.maintainers).toSet()
    }

    private fun otherMaintainers(): Set<Party> {
        return maintainers().minus(this.ourIdentity)
    }

    private fun participants(): Set<AbstractParty> {
        return (oldState.participants + newState.participants).toSet()
    }

    private fun subscribers(): Set<Party> {
        return getDistributionList(serviceHub, newState.linearId).map { it.party }.toSet()
    }

    private fun observers(): Set<AbstractParty> {
        return (participants() + subscribers()).minus(maintainers()).minus(this.ourIdentity)
    }

    private fun wellKnownObservers(): List<Party> {
        return observers().map { serviceHub.identityService.wellKnownPartyFromAnonymous(it)!! }
    }

}
