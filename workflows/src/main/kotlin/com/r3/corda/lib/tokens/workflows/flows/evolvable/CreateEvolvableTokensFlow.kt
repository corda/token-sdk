package com.r3.corda.lib.tokens.workflows.flows.evolvable

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.lib.tokens.contracts.states.EvolvableTokenType
import com.r3.corda.lib.tokens.workflows.internal.flows.finality.ObserverAwareFinalityFlow
import net.corda.core.contracts.TransactionState
import net.corda.core.flows.CollectSignaturesFlow
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.serialization.CordaSerializable
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder

/**
 * Inline sub-flow for creating multiple tokens of evolvable token type. This is just a simple flow for now.
 */
class CreateEvolvableTokensFlow
@JvmOverloads
constructor(
        val transactionStates: List<TransactionState<EvolvableTokenType>>,
        val participantSessions: List<FlowSession>,
        val observerSessions: List<FlowSession> = emptyList()
) : FlowLogic<SignedTransaction>() {
    @JvmOverloads
    constructor(transactionState: TransactionState<EvolvableTokenType>, participantSessions: List<FlowSession>, observerSessions: List<FlowSession> = emptyList()) :
            this(listOf(transactionState), participantSessions, observerSessions)

    @CordaSerializable
    data class Notification(val signatureRequired: Boolean = false)

    private val evolvableTokens = transactionStates.map { it.data }

    @Suspendable
    override fun call(): SignedTransaction {
        checkLinearIds(transactionStates)
        // TODO what about... preferred notary
        checkSameNotary()
        val transactionBuilder = TransactionBuilder(transactionStates.first().notary) // todo

        // Create a transaction which updates the ledger with the new evolvable tokens.
        transactionStates.forEach {
            addCreateEvolvableToken(transactionBuilder, it)
        }

        // Sign the transaction proposal
        val ptx: SignedTransaction = serviceHub.signInitialTransaction(transactionBuilder)

        // Gather signatures from other maintainers
        // Check that we have sessions with all maitainers but not with ourselves
        val otherMaintainerSessions = participantSessions.filter { it.counterparty in evolvableTokens.otherMaintainers(ourIdentity) }
        otherMaintainerSessions.forEach { it.send(Notification(signatureRequired = true)) }
        val stx = subFlow(CollectSignaturesFlow(
                partiallySignedTx = ptx,
                sessionsToCollectFrom = otherMaintainerSessions
        ))
        // Finalise with all participants, including maintainers, participants, and subscribers (via distribution list)
        val wellKnownObserverSessions = participantSessions.filter { it.counterparty in wellKnownObservers }
        val allObserverSessions = (wellKnownObserverSessions + observerSessions).toSet()
        allObserverSessions.forEach { it.send(Notification(signatureRequired = false)) }
        return subFlow(ObserverAwareFinalityFlow(signedTransaction = stx, allSessions = otherMaintainerSessions + allObserverSessions))
    }

    private fun checkLinearIds(transactionStates: List<TransactionState<EvolvableTokenType>>) {
        check(transactionStates.map { it.data.linearId }.toSet().size == transactionStates.size) {
            "Shouldn't create evolvable tokens with the same linearId."
        }
    }

    private fun checkSameNotary() {
        check(transactionStates.map { it.notary }.toSet().size == 1) {
            "All states should have the same notary"
        }
    }

    // TODO Refactor it more.
    private val otherObservers
        get(): Set<AbstractParty> {
            return evolvableTokens.participants().minus(evolvableTokens.maintainers()).minus(this.ourIdentity)
        }

    private val wellKnownObservers
        get(): List<Party> {
            return otherObservers.map { serviceHub.identityService.wellKnownPartyFromAnonymous(it)!! }
        }
}
