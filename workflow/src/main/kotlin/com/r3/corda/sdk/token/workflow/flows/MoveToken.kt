package com.r3.corda.sdk.token.workflow.flows

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.sdk.token.contracts.commands.MoveTokenCommand
import com.r3.corda.sdk.token.contracts.types.TokenType
import com.r3.corda.sdk.token.contracts.utilities.withNotary
import com.r3.corda.sdk.token.workflow.selection.TokenSelection
import com.r3.corda.sdk.token.workflow.utilities.ownedTokensByToken
import net.corda.core.contracts.Amount
import net.corda.core.flows.*
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.node.StatesToRecord
import net.corda.core.serialization.CordaSerializable
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.unwrap

object MoveToken {

    @CordaSerializable
    data class TokenMoveNotification(val anonymous: Boolean)

    @InitiatingFlow
    @StartableByRPC
    class Initiator<T : TokenType>(
            val ownedToken: T,
            val owner: Party,
            val amount: Amount<T>? = null,
            val anonymous: Boolean = true
    ) : FlowLogic<SignedTransaction>() {
        @Suspendable
        override fun call(): SignedTransaction {
            val ownerSession = initiateFlow(owner)

            // Notify the recipient that we'll be sending them tokens and advise them of anything they must do, e.g.
            // generate a confidential identity for the issuer or sign up for updates for evolvable tokens.
            ownerSession.send(MoveToken.TokenMoveNotification(anonymous = anonymous))

            val owningParty: AbstractParty = if (anonymous) {
                subFlow(RequestConfidentialIdentity.Initiator(ownerSession)).party.anonymise()
            } else owner

            val (builder, keys) = if (amount == null) {
                // The assumption here is that there is only one owned token of a particular type at any one time.
                // Double clarify this in the docs to ensure that it is used properly. Either way, this code likely
                // needs to be refactored out into a separate flow. For now it's just temporary to get things going.
                val ownedTokenStateAndRef = serviceHub.vaultService.ownedTokensByToken(ownedToken).states.single()
                val ownedTokenState = ownedTokenStateAndRef.state.data
                val notary = ownedTokenStateAndRef.state.notary
                val signingKey = ownedTokenState.holder.owningKey
                val output = ownedTokenState.withNewHolder(owningParty)
                val command = MoveTokenCommand(output.token)
                val utx: TransactionBuilder = TransactionBuilder(notary = notary).apply {
                    addInputState(ownedTokenStateAndRef)
                    addCommand(command, signingKey)
                    addOutputState(state = output withNotary notary)
                }
                Pair(utx, listOf(signingKey))
            } else {
                val tokenSelection = TokenSelection(serviceHub)
                tokenSelection.generateMove(TransactionBuilder(), amount, owningParty)
            }

            // WARNING: At present, the recipient will not be signed up to updates from the token maintainer.
            val stx: SignedTransaction = serviceHub.signInitialTransaction(builder, keys)
            // No need to pass in a session as there's no counterparty involved.
            return subFlow(FinalityFlow(transaction = stx, sessions = listOf(ownerSession)))
        }
    }

    @InitiatedBy(Initiator::class)
    class Responder(val otherSession: FlowSession) : FlowLogic<SignedTransaction>() {
        @Suspendable
        override fun call(): SignedTransaction {
            // Receive a move notification from the issuer. It tells us if we need to sign up for token updates or
            // generate a confidential identity.
            val issuanceNotification = otherSession.receive<TokenMoveNotification>().unwrap { it }

            // Generate and send over a new confidential identity, if necessary.
            if (issuanceNotification.anonymous) {
                subFlow(RequestConfidentialIdentity.Responder(otherSession))
            }

            // Resolve the issuance transaction.
            return subFlow(ReceiveFinalityFlow(otherSideSession = otherSession, statesToRecord = StatesToRecord.ONLY_RELEVANT))
        }
    }

}