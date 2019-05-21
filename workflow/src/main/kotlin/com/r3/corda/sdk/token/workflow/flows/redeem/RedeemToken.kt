package com.r3.corda.sdk.token.workflow.flows.redeem

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.sdk.token.contracts.states.AbstractToken
import com.r3.corda.sdk.token.contracts.states.FungibleToken
import com.r3.corda.sdk.token.contracts.states.NonFungibleToken
import com.r3.corda.sdk.token.contracts.types.TokenType
import com.r3.corda.sdk.token.workflow.flows.internal.confidential.RequestConfidentialIdentityFlow
import com.r3.corda.sdk.token.workflow.flows.internal.confidential.RequestConfidentialIdentityFlowHandler
import com.r3.corda.sdk.token.workflow.flows.internal.selection.TokenSelection
import com.r3.corda.sdk.token.workflow.flows.internal.selection.generateExitNonFungible
import com.r3.corda.sdk.token.workflow.utilities.ownedTokensByTokenIssuer
import com.r3.corda.sdk.token.workflow.utilities.tokenAmountWithIssuerCriteria
import net.corda.confidential.IdentitySyncFlow
import net.corda.core.contracts.Amount
import net.corda.core.contracts.CommandData
import net.corda.core.contracts.StateAndRef
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.node.StatesToRecord
import net.corda.core.node.services.IdentityService
import net.corda.core.serialization.CordaSerializable
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker
import net.corda.core.utilities.unwrap

object RedeemToken {

    @CordaSerializable
    data class TokenRedeemNotification<T : TokenType>(val anonymous: Boolean, val amount: Amount<T>?)

    // Called on owner side.
    @InitiatingFlow
    @StartableByRPC
    class InitiateRedeem<T : TokenType>(
            val ownedToken: T,
            val issuer: Party,
            val amount: Amount<T>? = null,
            val anonymous: Boolean = true
    ) : FlowLogic<SignedTransaction>() {
        companion object {
            object REDEEM_NOTIFICATION : ProgressTracker.Step("Sending redeem notification to tokenHolders.")
            object CONF_ID : ProgressTracker.Step("Requesting confidential identity.")
            object SELECTING_STATES : ProgressTracker.Step("Selecting states to redeem.")
            object SEND_STATE_REF : ProgressTracker.Step("Sending states to the issuer for redeeming.")
            object SYNC_IDS : ProgressTracker.Step("Synchronising confidential identities.")
            object SIGNING_TX : ProgressTracker.Step("Signing transaction")
            object FINALISING_TX : ProgressTracker.Step("Finalising transaction")

            fun tracker() = ProgressTracker(REDEEM_NOTIFICATION, CONF_ID, SELECTING_STATES, SEND_STATE_REF, SYNC_IDS, SIGNING_TX, FINALISING_TX)
        }

        override val progressTracker: ProgressTracker = tracker()

        @Suspendable
        override fun call(): SignedTransaction {
            val issuerSession = initiateFlow(issuer)

            progressTracker.currentStep = REDEEM_NOTIFICATION
            // Notify the recipient that we'll be sending them tokens for redeeming and advise them of anything they must do, e.g.
            // request a confidential identity.
            issuerSession.send(TokenRedeemNotification(anonymous = anonymous, amount = amount))

            // Generate and send over a new confidential identity, if necessary.
            if (anonymous) {
                progressTracker.currentStep = CONF_ID
                subFlow(RequestConfidentialIdentityFlowHandler(issuerSession))
            }

            progressTracker.currentStep = SELECTING_STATES
            val exitStateAndRefs: List<StateAndRef<AbstractToken<T>>> = if (amount == null) {
                // NonFungibleToken path.
                val ownedTokenStateAndRef = serviceHub.vaultService.ownedTokensByTokenIssuer(ownedToken, issuer).states
                check(ownedTokenStateAndRef.size == 1) {
                    "Exactly one owned token of a particular type $ownedToken should be in the vault at any one time."
                }
                ownedTokenStateAndRef
            } else {
                // FungibleToken path.
                val tokenSelection = TokenSelection(serviceHub)
                val queryCriteria = tokenAmountWithIssuerCriteria(amount.token, issuer)
                val fungibleStates = tokenSelection.attemptSpend(amount, TransactionBuilder().lockId, queryCriteria) // TODO We shouldn't expose lockId in this function
                checkSameNotary(fungibleStates)
                fungibleStates
            }
            progressTracker.currentStep = SEND_STATE_REF
            subFlow(SendStateAndRefFlow(issuerSession, exitStateAndRefs))
            progressTracker.currentStep = SYNC_IDS
            // TODO This is very weird way of syncing identities.
            //  I feel like we need some better API to do that, because in this case we just need input identities, not full wire tx (btw it's not sent anywhere nor it verifies).
            val firstState = exitStateAndRefs.first().state
            val notary = firstState.notary
            val fakeWireTx = TransactionBuilder(notary = notary).withItems(*exitStateAndRefs.toTypedArray()).addCommand(DummyCommand(), ourIdentity.owningKey).toWireTransaction(serviceHub)
            subFlow(IdentitySyncFlow.Send(issuerSession, fakeWireTx))
            progressTracker.currentStep = SIGNING_TX
            subFlow(object : SignTransactionFlow(issuerSession) {
                // TODO Add some additional checks.
                override fun checkTransaction(stx: SignedTransaction) = Unit
            })

            progressTracker.currentStep = FINALISING_TX
            return subFlow(ReceiveFinalityFlow(otherSideSession = issuerSession, statesToRecord = StatesToRecord.ONLY_RELEVANT))
        }
    }

    //TODO Another nasty hack because identity sync flow has api that is useless in our case.
    private class DummyCommand : CommandData

    // Called on Issuer side.
    @InitiatedBy(InitiateRedeem::class)
    class IssuerResponder<T : TokenType>(val otherSession: FlowSession) : FlowLogic<SignedTransaction>() {
        @Suspendable
        override fun call(): SignedTransaction {
            // Receive a redeem notification from the party. It tells us if we need to sign up for token updates or
            // generate a confidential identity.
            val redeemNotification = otherSession.receive<TokenRedeemNotification<T>>().unwrap { it }

            // Request confidential identity, if necessary.
            val otherIdentity = if (redeemNotification.anonymous) {
                subFlow(RequestConfidentialIdentityFlow(otherSession)).party.anonymise()
            } else otherSession.counterparty

            val stateAndRefsToRedeem = subFlow(ReceiveStateAndRefFlow<AbstractToken<T>>(otherSession))
            // Synchronise identities.
            subFlow(IdentitySyncFlow.Receive(otherSession))
            check(stateAndRefsToRedeem.isNotEmpty()) {
                "Received empty list of states to redeem."
            }
            checkSameIssuer(stateAndRefsToRedeem, ourIdentity)
            checkSameNotary(stateAndRefsToRedeem)
            checkOwner(serviceHub.identityService, stateAndRefsToRedeem, otherSession.counterparty)
            val notary = stateAndRefsToRedeem.first().state.notary
            val txBuilder = TransactionBuilder(notary = notary)
            if (redeemNotification.amount == null) {
                generateExitNonFungible(txBuilder, stateAndRefsToRedeem.first() as StateAndRef<NonFungibleToken<T>>)
            } else {
                TokenSelection(serviceHub).generateExit(
                        builder = txBuilder,
                        exitStates = stateAndRefsToRedeem as List<StateAndRef<FungibleToken<T>>>,
                        amount = redeemNotification.amount,
                        changeOwner = otherIdentity
                )
            }
            val partialStx = serviceHub.signInitialTransaction(txBuilder, ourIdentity.owningKey)
            val stx = subFlow(CollectSignaturesFlow(partialStx, listOf(otherSession)))
            return subFlow(FinalityFlow(transaction = stx, sessions = listOf(otherSession)))
        }
    }

    // Check that all states share the same notary.
    @Suspendable
    private fun <T : TokenType> checkSameNotary(stateAndRefs: List<StateAndRef<AbstractToken<T>>>) {
        val notary = stateAndRefs.first().state.notary
        check(stateAndRefs.all { it.state.notary == notary }) {
            "All states should have the same notary. Automatic notary change isn't supported for now."
        }
    }

    // Checks if all states have the same issuer. If the issuer is provided as a parameter then it checks if all states
    // were issued by this issuer.
    @Suspendable
    private fun <T : TokenType> checkSameIssuer(
            stateAndRefs: List<StateAndRef<AbstractToken<T>>>,
            issuer: Party? = null
    ) {
        val issuerToCheck = issuer ?: stateAndRefs.first().state.data.issuer
        check(stateAndRefs.all { it.state.data.issuer == issuerToCheck }) {
            "Tokens with different issuers."
        }
    }

    // Check if owner of the states is well known. Check if states come from the same owner.
    // Should be called after synchronising identities step.
    @Suspendable
    private fun <T : TokenType> checkOwner(
            identityService: IdentityService,
            stateAndRefs: List<StateAndRef<AbstractToken<T>>>,
            counterparty: Party
    ) {
        val owners = stateAndRefs.map { identityService.wellKnownPartyFromAnonymous(it.state.data.holder) }
        check(owners.all { it != null }) {
            "Received states with owner that we don't know about."
        }
        check(owners.all { it == counterparty }) {
            "Received states that don't come from counterparty that initiated the flow."
        }
    }
}
