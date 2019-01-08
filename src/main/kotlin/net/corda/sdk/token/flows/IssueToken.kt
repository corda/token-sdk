package net.corda.sdk.token.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.TransactionState
import net.corda.core.flows.*
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.node.StatesToRecord
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.sdk.token.commands.Issue
import net.corda.sdk.token.types.AbstractOwnedToken
import net.corda.sdk.token.types.EmbeddableToken
import net.corda.sdk.token.types.Issued
import net.corda.sdk.token.types.TokenPointer
import net.corda.sdk.token.utilities.issuedBy
import net.corda.sdk.token.utilities.of
import net.corda.sdk.token.utilities.ownedBy
import net.corda.sdk.token.utilities.withNotary

/**
 * Currently the same notary for the token and the owned token if it contains an evolvable pointer.
 * Need to deal with anonymous keys.
 * Need to deal with difference between fungible and non fungible.
 * Add constructors for each type.
 * Params: token, owner, issuer, notary, contract, amount.
 * TODO May also have to send the token type as well.
 */
object IssueToken {

    @InitiatingFlow
    @StartableByRPC
    class Initiator<T : EmbeddableToken>(
            val token: T,
            val owner: Party,
            val notary: Party,
            val amount: Long? = null,
            val anonymous: Boolean = true
    ) : FlowLogic<SignedTransaction>() {

        @Suspendable
        override fun call(): SignedTransaction {
            // This is the identity which will be used to issue tokens.
            val me: Party = ourIdentity

            // This is the recipient of the tokens identity.
            val owningParty: AbstractParty = if (anonymous) {
                subFlow(RequestConfidentialIdentity.Initiator(owner)).party.anonymise()
            } else owner

            // If T is evolvable then we need to send it as well...
            // TODO Test this flow without sending the evolvable token to see what happens.
            if (token is TokenPointer<*>) {
                // Look up the actual token using the vault and send it to the other side.
                // OR can it be supplied to the flow ?
                token.pointer.pointer
            }

            // Create the issued token. We add this to the commands for grouping.
            val issuedToken: Issued<T> = token issuedBy me

            // Create the token. It's either an OwnedToken or OwnedTokenAmount.
            val ownedToken: AbstractOwnedToken<T> = if (amount == null) {
                issuedToken ownedBy owningParty
            } else {
                amount of issuedToken ownedBy owningParty
            }

            // Create the transaction.
            val transactionState: TransactionState<AbstractOwnedToken<T>> = ownedToken withNotary notary
            val utx: TransactionBuilder = TransactionBuilder().apply {
                addCommand(data = Issue(issuedToken), keys = me.owningKey)
                addOutputState(state = transactionState)
            }
            // Sign the transaction. Only Concrete Parties should be used here.
            val stx: SignedTransaction = serviceHub.signInitialTransaction(utx)
            // No need to pass in a session as there's no counterparty involved.
            val ownerSession = initiateFlow(owner)
            return subFlow(FinalityFlow(transaction = stx, sessions = listOf(ownerSession)))
        }
    }

    @InitiatedBy(Initiator::class)
    class Responder(val otherSession: FlowSession) : FlowLogic<SignedTransaction>() {
        @Suspendable
        override fun call(): SignedTransaction {
            subFlow(RequestConfidentialIdentity.Responder(otherSession))
            return subFlow(ReceiveFinalityFlow(otherSideSession = otherSession, statesToRecord = StatesToRecord.ONLY_RELEVANT))
        }
    }

}
