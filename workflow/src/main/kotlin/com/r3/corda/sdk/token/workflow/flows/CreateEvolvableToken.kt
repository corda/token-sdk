package com.r3.corda.sdk.token.workflow.flows

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.sdk.token.contracts.commands.Create
import com.r3.corda.sdk.token.contracts.states.EvolvableTokenType
import com.r3.corda.sdk.token.contracts.utilities.withNotary
import net.corda.core.contracts.ContractClassName
import net.corda.core.contracts.TransactionState
import net.corda.core.contracts.requireThat
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.node.StatesToRecord
import net.corda.core.serialization.CordaSerializable
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker
import net.corda.core.utilities.unwrap

/**
 * Flow for creating an evolvable token type. This is just a simple flow for now. Although it can be invoked via the
 * shell, it is more likely to be used for unit testing or called as an inlined-subflow.
 */
object CreateEvolvableToken {

    @CordaSerializable
    data class EvolvableTokenCreationNotification(val sign: Boolean = false)

    @InitiatingFlow
    @StartableByRPC
    class Initiator<T : EvolvableTokenType>(
            val transactionState: TransactionState<T>
    ) : FlowLogic<SignedTransaction>() {

        constructor(evolvableToken: T, contract: ContractClassName, notary: Party)
                : this(TransactionState(evolvableToken, contract, notary))

        constructor(evolvableToken: T, notary: Party) : this(evolvableToken withNotary notary)

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
            // Note that initially it is not shared with anyone.
            progressTracker.currentStep = CREATING
            val evolvableToken = transactionState.data
            val signingKeys = evolvableToken.maintainers.map { it.owningKey }
            val utx: TransactionBuilder = TransactionBuilder().apply {
                addCommand(data = Create(), keys = signingKeys)
                addOutputState(state = transactionState)
            }

            // Sign the transaction proposal
            progressTracker.currentStep = SIGNING
            val stx: SignedTransaction = serviceHub.signInitialTransaction(utx)

            // Gather signatures from other maintainers
            progressTracker.currentStep = COLLECTING
            val maintainers = evolvableToken.maintainers.toSet().minus(this.ourIdentity)
            val maintainerSessions = maintainers.map { initiateFlow(it) }
            maintainerSessions.forEach { it.send(EvolvableTokenCreationNotification(sign = true)) }
            val tx = subFlow(CollectSignaturesFlow(
                    partiallySignedTx = stx,
                    sessionsToCollectFrom = maintainerSessions,
                    progressTracker = COLLECTING.childProgressTracker()
            ))

            // Finalise with all participants
            progressTracker.currentStep = RECORDING
            val participants = evolvableToken.participants.toSet()
                    .minus(this.ourIdentity)
                    .minus(evolvableToken.maintainers)
            val participantSessions = participants.map { initiateFlow( serviceHub.identityService.wellKnownPartyFromAnonymous(it)!! ) }
            participantSessions.forEach { it.send(EvolvableTokenCreationNotification(sign = false)) }
            return subFlow(FinalityFlow(
                    transaction = tx,
                    sessions = (maintainerSessions + participantSessions),
                    progressTracker = RECORDING.childProgressTracker()
            ))
        }
    }

    @InitiatedBy(Initiator::class)
    class Responder(val otherSession: FlowSession) : FlowLogic<SignedTransaction>() {
        @Suspendable
        override fun call(): SignedTransaction {
            // Receive the notification
            val notification = otherSession.receive<EvolvableTokenCreationNotification>().unwrap { it }

            // Sign the transaction proposal, if required
            if (notification.sign) {
                val signTransactionFlow = object : SignTransactionFlow(otherSession) {
                    override fun checkTransaction(stx: SignedTransaction) = requireThat {
                        // TODO
                    }
                }
                subFlow(signTransactionFlow)
            }

            // Resolve the creation transaction.
            return subFlow(ReceiveFinalityFlow(otherSideSession = otherSession, statesToRecord = StatesToRecord.ONLY_RELEVANT))
        }
    }

}