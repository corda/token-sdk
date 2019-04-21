package com.r3.corda.sdk.token.workflow.flows

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.sdk.token.contracts.types.TokenType
import com.r3.corda.sdk.token.workflow.selection.TokenSelection
import com.r3.corda.sdk.token.workflow.selection.generateMoveNonFungible
import net.corda.core.contracts.Amount
import net.corda.core.contracts.requireThat
import net.corda.core.flows.*
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.node.StatesToRecord
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker
import java.security.PublicKey

object MoveToken {

    abstract class Primary<T : TokenType>(
            val ownedToken: T,
            val holder: AbstractParty,
            val amount: Amount<T>? = null,
            val session: FlowSession? = null
    ) : FlowLogic<SignedTransaction>() {
        companion object {
            object GENERATE_MOVE : ProgressTracker.Step("Generating tokens move.")
            object EXTRA_FLOW : ProgressTracker.Step("Starting extra flow")
            object SIGNING : ProgressTracker.Step("Signing transaction proposal.")
            object RECORDING : ProgressTracker.Step("Recording signed transaction.") {
                override fun childProgressTracker() = FinalityFlow.tracker()
            }

            fun tracker() = ProgressTracker(GENERATE_MOVE, EXTRA_FLOW, SIGNING, RECORDING)
        }

        override val progressTracker: ProgressTracker = tracker()

        @Suspendable
        abstract fun transactionExtra(me: Party,
                                      holderParty: Party,
                                      holderSession: FlowSession,
                                      builder: TransactionBuilder): List<PublicKey>

        @Suspendable
        open fun move(holderParty: Party,
                      holderSession: FlowSession): Pair<TransactionBuilder, List<PublicKey>> {

            return if (amount == null) {
                generateMoveNonFungible(serviceHub.vaultService, ownedToken, holder)
            } else {
                val tokenSelection = TokenSelection(serviceHub)
                tokenSelection.generateMove(TransactionBuilder(), amount, holder)
            }
        }

        @Suspendable
        override fun call(): SignedTransaction {
            val me: Party = ourIdentity
            val holderParty = serviceHub.identityService.wellKnownPartyFromAnonymous(holder)
                    ?: throw IllegalArgumentException("Called MoveToken flow with anonymous party that node doesn't know about. " +
                            "Make sure that RequestConfidentialIdentity flow is called before.")
            val holderSession = if (session == null) initiateFlow(holderParty) else session

            progressTracker.currentStep = GENERATE_MOVE
            val (builder, keys) = move(holderParty, holderSession)

            progressTracker.currentStep = EXTRA_FLOW
            val extraKeys = transactionExtra(me, holderParty, holderSession, builder)

            progressTracker.currentStep = SIGNING
            // WARNING: At present, the recipient will not be signed up to updates from the token maintainer.
            val ptx: SignedTransaction = serviceHub.signInitialTransaction(builder, keys)
            progressTracker.currentStep = RECORDING
            val sessions = if (ourIdentity == holderParty) emptyList() else listOf(holderSession)

            val stx = ptx +
                    if (!serviceHub.myInfo.isLegalIdentity(holderSession.counterparty)) {
                        subFlow(CollectSignatureFlow(ptx, holderSession, extraKeys))
                    } else {
                        listOf()
                    }

            return subFlow(FinalityFlow(transaction = stx, sessions = sessions))
        }
    }

    abstract class Secondary(val otherSession: FlowSession) : FlowLogic<Unit>() {

        @Suspendable
        abstract fun checkTransaction(stx: SignedTransaction)

        @Suspendable
        override fun call(): Unit {
            // Resolve the issuance transaction.
            if (!serviceHub.myInfo.isLegalIdentity(otherSession.counterparty)) {

                val parties = serviceHub.identityService.wellKnownPartyFromAnonymous(otherSession.counterparty)
                        ?: throw IllegalArgumentException("Called MoveToken flow with anonymous party that node doesn't know about. " +
                                "Make sure that RequestConfidentialIdentity flow is called before.")

                parties

                val signTransactionFlow = object : SignTransactionFlow(otherSession) {
                    override fun checkTransaction(stx: SignedTransaction) = this@Secondary.checkTransaction(stx)
                }

                val txId = subFlow(signTransactionFlow).id

                // Resolve the issuance transaction.
                subFlow(ReceiveFinalityFlow(otherSideSession = otherSession,
                        statesToRecord = StatesToRecord.ONLY_RELEVANT, expectedTxId = txId))
            }
        }
    }

    @InitiatingFlow
    @StartableByRPC
    class Initiator<T : TokenType>(
            ownedToken: T,
            holder: AbstractParty,
            amount: Amount<T>? = null,
            session: FlowSession? = null
    ) : Primary<T>(ownedToken, holder, amount, session) {

        @Suspendable
        override fun transactionExtra(me: Party,
                                      holderParty: Party,
                                      holderSession: FlowSession,
                                      builder: TransactionBuilder): List<PublicKey> {
            return listOf()
        }
    }

    @InitiatedBy(Initiator::class)
    class Responder(otherSession: FlowSession) : Secondary(otherSession) {

        @Suspendable
        override fun checkTransaction(stx: SignedTransaction) = requireThat { }
    }
}