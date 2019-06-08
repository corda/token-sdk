package com.r3.corda.sdk.token.workflow.flows

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.sdk.token.contracts.types.TokenType
import net.corda.core.contracts.Amount
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.flows.InitiatedBy
import net.corda.core.flows.InitiatingFlow
import net.corda.core.identity.Party
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.ProgressTracker


object ConfidentialIssueFlow {
    @InitiatingFlow
    class Initiator<T : TokenType>(
            val token: T,
            val holder: Party,
            val notary: Party,
            val amount: Amount<T>? = null,
            override val progressTracker: ProgressTracker = tracker()
    ) : FlowLogic<SignedTransaction>() {

        @Suspendable
        override fun call(): SignedTransaction {
            val holderSession = initiateFlow(holder)
            progressTracker.currentStep = REQUESTING_IDENTITY
            val confidentialHolder = subFlow(RequestConfidentialIdentity.Initiator(holderSession)).party.anonymise()
            progressTracker.currentStep = ISSUING_TOKEN
            return subFlow(IssueToken.Initiator(
                    token,
                    confidentialHolder,
                    notary,
                    amount,
                    holderSession,
                    progressTracker = ISSUING_TOKEN.childProgressTracker()
            ))
        }

        companion object {
            object REQUESTING_IDENTITY : ProgressTracker.Step("Requesting a confidential identity.")

            object ISSUING_TOKEN : ProgressTracker.Step("Issuing token.") {
                override fun childProgressTracker() = IssueToken.Initiator.tracker()
            }

            fun tracker() = ProgressTracker(REQUESTING_IDENTITY, ISSUING_TOKEN)
        }
    }

    @InitiatedBy(Initiator::class)
    class Responder(
            val otherSession: FlowSession,
            override val progressTracker: ProgressTracker = tracker()
    ) : FlowLogic<Unit>() {

        constructor(otherSession: FlowSession) : this(otherSession, tracker())

        @Suspendable
        override fun call() {
            progressTracker.currentStep = REQUESTING_IDENTITY
            subFlow(RequestConfidentialIdentity.Responder(otherSession))
            progressTracker.currentStep = ISSUING_TOKEN
            subFlow(IssueToken.Responder(otherSession, progressTracker = ISSUING_TOKEN.childProgressTracker()))
        }

        companion object {
            object REQUESTING_IDENTITY : ProgressTracker.Step("Requesting a confidential identity.")

            object ISSUING_TOKEN : ProgressTracker.Step("Issuing token.") {
                override fun childProgressTracker() = IssueToken.Responder.tracker()
            }

            fun tracker() = ProgressTracker(REQUESTING_IDENTITY, ISSUING_TOKEN)
        }
    }
}

object ConfidentialMoveFlow {
    @InitiatingFlow
    class Initiator<T : TokenType>(
            val ownedToken: T,
            val holder: Party,
            val amount: Amount<T>? = null,
            override val progressTracker: ProgressTracker = tracker()
    ) : FlowLogic<SignedTransaction>() {

        @Suspendable
        override fun call(): SignedTransaction {
            val holderSession = initiateFlow(holder)
            progressTracker.currentStep = REQUESTING_IDENTITY
            val confidentialHolder = subFlow(RequestConfidentialIdentity.Initiator(holderSession)).party.anonymise()
            progressTracker.currentStep = MOVING_TOKEN
            return if (amount == null) {
                subFlow(MoveTokenNonFungible(ownedToken, holder, holderSession, progressTracker = MOVING_TOKEN.childProgressTracker()))
            } else {
                subFlow(MoveTokenFungible(amount, confidentialHolder, holderSession, progressTracker = MOVING_TOKEN.childProgressTracker()))
            }
        }

        companion object {
            object REQUESTING_IDENTITY : ProgressTracker.Step("Requesting a confidential identity.")

            object MOVING_TOKEN : ProgressTracker.Step("Moving token.") {
                override fun childProgressTracker() = MoveToken.Initiator.tracker()
            }

            fun tracker() = ProgressTracker(REQUESTING_IDENTITY, MOVING_TOKEN)
        }

    }

    @InitiatedBy(Initiator::class)
    class Responder(
            val otherSession: FlowSession,
            override val progressTracker: ProgressTracker = tracker()
    ) : FlowLogic<Unit>() {

        constructor(otherSession: FlowSession) : this(otherSession, tracker())

        @Suspendable
        override fun call() {
            progressTracker.currentStep = REQUESTING_IDENTITY
            subFlow(RequestConfidentialIdentity.Responder(otherSession))
            progressTracker.currentStep = MOVING_TOKEN
            subFlow(MoveToken.Responder(otherSession, progressTracker = MOVING_TOKEN.childProgressTracker()))
        }

        companion object {
            object REQUESTING_IDENTITY : ProgressTracker.Step("Requesting a confidential identity.")

            object MOVING_TOKEN : ProgressTracker.Step("Moving token.") {
                override fun childProgressTracker() = MoveToken.Responder.tracker()
            }

            fun tracker() = ProgressTracker(REQUESTING_IDENTITY, MOVING_TOKEN)
        }

    }
}
