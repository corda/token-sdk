package com.r3.corda.lib.tokens.workflows.flows.evolvable

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.lib.tokens.contracts.states.EvolvableTokenType
import com.r3.corda.lib.tokens.workflows.internal.flows.finality.ObserverAwareFinalityFlow
import net.corda.core.contracts.StateAndRef
import net.corda.core.flows.CollectSignaturesFlow
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.serialization.CordaSerializable
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder

class UpdateEvolvableTokenFlow(
        val oldStateAndRef: StateAndRef<EvolvableTokenType>,
        val newState: EvolvableTokenType,
        val participantSessions: List<FlowSession>,
        val observerSessions: List<FlowSession> = emptyList()
) : FlowLogic<SignedTransaction>() {
    /**
     * Simple notification class to inform counterparties of their role. In this instance, informs participants if
     * they are required to sign the command. This is intended to allow maintainers to sign commands while participants
     * and other observers merely finalise the transaction.
     */
    @CordaSerializable
    data class Notification(val signatureRequired: Boolean = false)

    @Suspendable
    override fun call(): SignedTransaction {
        require(ourIdentity in oldStateAndRef.state.data.maintainers) {
            "This flow can only be started by existing maintainers of the EvolvableTokenType."
        }

        // Create a transaction which updates the ledger with the new evolvable token.
        // The tokenHolders listed as maintainers in the old state should be the signers.
        // TODO Should this be both old and new maintainer lists?
        val utx = addUpdateEvolvableToken(
                TransactionBuilder(notary = oldStateAndRef.state.notary),
                oldStateAndRef,
                newState
        )

        // Sign the transaction proposal (creating a partially signed transaction, or ptx)
        val ptx: SignedTransaction = serviceHub.signInitialTransaction(utx)

        // Gather signatures from other maintainers
        val otherMaintainerSessions = participantSessions.filter { it.counterparty in evolvableTokens.otherMaintainers(ourIdentity) }
        otherMaintainerSessions.forEach { it.send(Notification(signatureRequired = true)) }
        val stx = subFlow(CollectSignaturesFlow(
                partiallySignedTx = ptx,
                sessionsToCollectFrom = otherMaintainerSessions
        ))

        // Distribute to all observers, including maintainers, participants, and subscribers (via distribution list)
        val wellKnownObserverSessions = participantSessions.filter { it.counterparty in wellKnownObservers }
        val allObserverSessions = (wellKnownObserverSessions + observerSessions).toSet()
        observerSessions.forEach { it.send(Notification(signatureRequired = false)) }
        return subFlow(ObserverAwareFinalityFlow(signedTransaction = stx, allSessions = otherMaintainerSessions + allObserverSessions))
    }

    // TODO Refactor it more.
    private val oldState get() = oldStateAndRef.state.data
    private val evolvableTokens = listOf(oldState, newState)

    private fun otherObservers(subscribers: Set<Party>): Set<AbstractParty> {
        return (evolvableTokens.participants() + subscribers).minus(evolvableTokens.maintainers()).minus(this.ourIdentity)
    }

    private val wellKnownObservers
        get(): List<Party> {
            return otherObservers(subscribersForState(newState, serviceHub)).map { serviceHub.identityService.wellKnownPartyFromAnonymous(it)!! }
        }
}
