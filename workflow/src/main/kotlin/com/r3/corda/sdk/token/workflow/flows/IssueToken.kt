package com.r3.corda.sdk.token.workflow.flows

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.sdk.token.contracts.commands.IssueTokenCommand
import com.r3.corda.sdk.token.contracts.states.AbstractToken
import com.r3.corda.sdk.token.contracts.types.IssuedTokenType
import com.r3.corda.sdk.token.contracts.types.TokenPointer
import com.r3.corda.sdk.token.contracts.types.TokenType
import com.r3.corda.sdk.token.contracts.utilities.heldBy
import com.r3.corda.sdk.token.contracts.utilities.issuedBy
import com.r3.corda.sdk.token.contracts.utilities.withNotary
import net.corda.core.contracts.Amount
import net.corda.core.contracts.TransactionState
import net.corda.core.flows.*
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.node.StatesToRecord
import net.corda.core.serialization.CordaSerializable
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker
import net.corda.core.utilities.unwrap

/**
 * This flow takes a bunch of parameters and is used to issue a token or an amount of some token on the ledger to
 * a specified party. Most of the parameters are self explanatory. Most likely, this flow will be used as a sub-flow
 * from another flow which handles the over-arching token issuance process.
 *
 * @param owner the party which is the recipient of the token or amount of tokens
 * @param notary the notary which will be used for the owned token state. Currently the same notary must be used for the
 * token and the owned token if it contains an evolvable pointer.
 * @param token the type of token which will be issued. This can either be a [FixedTokenType] or a [TokenPointer]. Both
 * types are [TokenType]s as specified by the type parameter [T]. Fixed tokens are token definitions which can be
 * inlined into an owned token state. They are the most straight-forward token type to use. [FixedTokenType]s are mainly
 * used for things like [Money] which rarely change, if at all. For other types of token which have properties that are
 * expected to change over time, we use the [TokenPointer]. The [TokenPointer] points to an [EvolvableToken] which is
 * a [LinearState] that contains the details of the token. The token which is provided is not wrapped with an [IssuedTokenType]
 * object, instead, the issuer becomes the party which invokes this flow. This makes sense because it is impossible to
 * have an issuer other than the node which invokes the [IssueToken.Initiator] flow.
 * @param amount the amount of the token to be issued. Note that this can be set to null. If it is set to null then an
 * [NonFungibleToken] state is issued. This state wraps an [IssuedTokenType] [TokenType] with an [owner]. The [TokenType] is
 * non-fungible inside [NonFungibleToken]s - they cannot be split or merged because there is only ever one of them. However,
 * if an amount is specified, then that many tokens will be issued using an [FungibleToken] state. Currently, there
 * will be a single [FungibleToken] state issued for the amount of [IssuedTokenType] [TokenType] specified. Note that
 * if an amount of ONE is specified and the token has fraction digits set to "0.1" then that ONE token could be split
 * into TEN atomic units of the token. Likewise, if the token has fraction digits set to "0.01", then that ONE token
 * could be split into ONE HUNDRED atomic units of the token.
 * @param anonymous defaults to true. When true, the issuer asks the recipient for a newly generated public key which
 * will be used as the owning key for the tokens.
 *
 * It is likely that this flow will be split up in the future as the process becomes more complex.
 *
 * TODO: Add more constructors.
 * TODO: Allow for more customisation, e.g. tokens issued across multiple states instead of a single FungibleToken.
 * TODO: Split into two flows. One for owned tokens and another for owned token amounts.
 * TODO: Profile and optimise this flow.
 */
object IssueToken {

    @CordaSerializable
    data class TokenIssuanceNotification(val anonymous: Boolean)

    @InitiatingFlow
    @StartableByRPC
    class Initiator<T : TokenType>(
            val token: T,
            val owner: Party,
            val notary: Party,
            val amount: Amount<T>? = null,
            val anonymous: Boolean = true
    ) : FlowLogic<SignedTransaction>() {

        companion object {
            object ISSUANCE_NOTIFICATION : ProgressTracker.Step("Sending issuance notification to counterparty.")
            object REQUEST_CONF_ID : ProgressTracker.Step("Requesting confidential identity.")
            object DIST_LIST : ProgressTracker.Step("Adding party to distribution list.")
            object SIGNING : ProgressTracker.Step("Signing transaction proposal.")
            object RECORDING : ProgressTracker.Step("Recording signed transaction.") {
                override fun childProgressTracker() = FinalityFlow.tracker()
            }

            fun tracker() = ProgressTracker(ISSUANCE_NOTIFICATION, REQUEST_CONF_ID, DIST_LIST, SIGNING, RECORDING)
        }

        override val progressTracker: ProgressTracker = tracker()

        @Suspendable
        override fun call(): SignedTransaction {
            // This is the identity which will be used to issue tokens.
            // We also need a session for the other side.
            val me: Party = ourIdentity
            val ownerSession = initiateFlow(owner)

            progressTracker.currentStep = ISSUANCE_NOTIFICATION
            // Notify the recipient that we'll be issuing them a tokens and advise them of anything they must do, e.g.
            // generate a confidential identity for the issuer or sign up for updates for evolvable tokens.
            ownerSession.send(TokenIssuanceNotification(anonymous = anonymous))

            // This is the recipient of the tokens identity.
            val owningParty: AbstractParty = if (anonymous) {
                progressTracker.currentStep = REQUEST_CONF_ID
                subFlow(RequestConfidentialIdentity.Initiator(ownerSession)).party.anonymise()
            } else owner

            // Create the issued token. We add this to the commands for grouping.
            val issuedToken: IssuedTokenType<T> = token issuedBy me

            // Create the token. It's either an NonFungibleToken or FungibleToken.
            val ownedToken: AbstractToken = if (amount == null) {
                issuedToken heldBy owningParty
            } else {
                amount issuedBy me heldBy owningParty
            }

            // At this point, the issuer signs up the recipient to automatic updates for evolvable tokens. On the other
            // hand, if the token is a fixed inline tokenType, then the recipient will receive the tokenType with the
            // owned token amount, so there's no need to sign up to updates.
            //
            // NOTE: It might be the case that the issuer is not actually the token maintainer for the evolvable token!
            // In which case, signing up to updates in the manner provided by "RequestAdditionToDistributionList" would
            // not work, as the issuer would not be making the updates to the token - some other party would be. This is
            // where data distribution groups come in very handy! The token issuers would sign up to updates from the
            // token maintainer, and in turn, the recipients of those tokens from the issuer would sign up to updates
            // from the issuer, this way the token updates proliferate through the network.
            if (token is TokenPointer<*>) {
                progressTracker.currentStep = DIST_LIST
                subFlow(AddPartyToDistributionList(owner, token.pointer.pointer))
            }

            // Create the transaction.
            val transactionState: TransactionState<AbstractToken> = ownedToken withNotary notary
            val utx: TransactionBuilder = TransactionBuilder(notary = notary).apply {
                addCommand(data = IssueTokenCommand(issuedToken), keys = listOf(me.owningKey))
                addOutputState(state = transactionState)
            }
            progressTracker.currentStep = SIGNING
            // Sign the transaction. Only Concrete Parties should be used here.
            val stx: SignedTransaction = serviceHub.signInitialTransaction(utx)
            // No need to pass in a session as there's no counterparty involved.
            progressTracker.currentStep = RECORDING
            return subFlow(FinalityFlow(transaction = stx,
                    progressTracker = RECORDING.childProgressTracker(),
                    sessions = listOf(ownerSession)
            ))
        }
    }

    @InitiatedBy(Initiator::class)
    class Responder(val otherSession: FlowSession) : FlowLogic<SignedTransaction>() {
        @Suspendable
        override fun call(): SignedTransaction {
            // Receive an issuance notification from the issuer. It tells us if we need to sign up for token updates or
            // generate a confidential identity.
            val issuanceNotification = otherSession.receive<TokenIssuanceNotification>().unwrap { it }

            // Generate and send over a new confidential identity, if necessary.
            if (issuanceNotification.anonymous) {
                subFlow(RequestConfidentialIdentity.Responder(otherSession))
            }

            // Resolve the issuance transaction.
            return subFlow(ReceiveFinalityFlow(otherSideSession = otherSession, statesToRecord = StatesToRecord.ONLY_RELEVANT))
        }
    }

}
