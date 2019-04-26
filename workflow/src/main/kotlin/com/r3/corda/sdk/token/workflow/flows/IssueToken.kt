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
import com.r3.corda.sdk.token.workflow.utilities.addPartyToDistributionList
import com.r3.corda.sdk.token.workflow.utilities.requireKnownConfidentialIdentity
import net.corda.core.contracts.Amount
import net.corda.core.contracts.TransactionState
import net.corda.core.flows.FinalityFlow
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.flows.InitiatedBy
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.ReceiveFinalityFlow
import net.corda.core.flows.StartableByRPC
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.node.StatesToRecord
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker

/**
 * This flow takes a bunch of parameters and is used to issue a token or an amount of some token on the ledger to
 * a specified party. Most of the parameters are self explanatory. Most likely, this flow will be used as a sub-flow
 * from another flow which handles the over-arching token issuance process.
 *
 * @param issueTo the party which is the recipient of the token or amount of tokens. It can be a Party or an AnonymousParty,
 *  in the latter case it is important that [RequestConfidentialIdentity] flow was called to generate and record new identities.
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
 *
 * It is likely that this flow will be split up in the future as the process becomes more complex.
 *
 * TODO: Add more constructors.
 * TODO: Allow for more customisation, e.g. tokens issued across multiple states instead of a single FungibleToken.
 * TODO: Split into two flows. One for owned tokens and another for owned token amounts.
 * TODO: Profile and optimise this flow.
 */
object IssueToken {

    @InitiatingFlow
    @StartableByRPC
    class Initiator<T : TokenType>(
            val token: T,
            val issueTo: AbstractParty,
            val notary: Party,
            val amount: Amount<T>? = null,
            val session: FlowSession? = null
    ) : FlowLogic<SignedTransaction>() {
        companion object {
            object DIST_LIST : ProgressTracker.Step("Adding party to distribution list.")
            object SIGNING : ProgressTracker.Step("Signing transaction proposal.")
            object RECORDING : ProgressTracker.Step("Recording signed transaction.") {
                override fun childProgressTracker() = FinalityFlow.tracker()
            }

            fun tracker() = ProgressTracker(DIST_LIST, SIGNING, RECORDING)
        }

        override val progressTracker: ProgressTracker = tracker()

        @Suspendable
        override fun call(): SignedTransaction {
            // This is the identity which will be used to issue tokens.
            // We also need a session for the other side.
            val me: Party = ourIdentity
            val holderParty = serviceHub.identityService.requireKnownConfidentialIdentity(issueTo)
            val holderSession = if (session == null) initiateFlow(holderParty) else session

            // Create the issued token. We add this to the commands for grouping.
            val issuedToken: IssuedTokenType<T> = token issuedBy me

            // Create the token. It's either an NonFungibleToken or FungibleToken.
            val heldToken: AbstractToken<T> = if (amount == null) {
                issuedToken heldBy issueTo
            } else {
                amount issuedBy me heldBy issueTo
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
                addPartyToDistributionList(serviceHub, holderParty, token.pointer.pointer)
            }

            // Create the transaction.
            val transactionState: TransactionState<AbstractToken<T>> = heldToken withNotary notary
            val utx: TransactionBuilder = TransactionBuilder(notary = notary).apply {
                addCommand(data = IssueTokenCommand(issuedToken), keys = listOf(me.owningKey))
                addOutputState(state = transactionState)
            }
            progressTracker.currentStep = SIGNING
            // Sign the transaction. Only Concrete Parties should be used here.
            val stx: SignedTransaction = serviceHub.signInitialTransaction(utx)
            progressTracker.currentStep = RECORDING
            // Can issue to yourself, but finality flow doesn't take a session then.
            val sessions = if (me == holderParty) emptyList() else listOf(holderSession)
            return subFlow(FinalityFlow(transaction = stx,
                    progressTracker = RECORDING.childProgressTracker(),
                    sessions = sessions
            ))
        }
    }

    @InitiatedBy(Initiator::class)
    class Responder(val otherSession: FlowSession) : FlowLogic<Unit>() {
        @Suspendable
        override fun call() {
            // We must do this check because FinalityFlow does not send locally and we want to be able to issue to ourselves.
            if (!serviceHub.myInfo.isLegalIdentity(otherSession.counterparty)) {
                // Resolve the issuance transaction.
                subFlow(ReceiveFinalityFlow(otherSideSession = otherSession, statesToRecord = StatesToRecord.ONLY_RELEVANT))
            }
        }
    }
}
