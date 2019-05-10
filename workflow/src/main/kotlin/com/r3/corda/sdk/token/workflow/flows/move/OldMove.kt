//package com.r3.corda.sdk.token.workflow.flows.move
//
//import co.paralleluniverse.fibers.Suspendable
//import com.r3.corda.sdk.token.contracts.types.TokenPointer
//import com.r3.corda.sdk.token.contracts.types.TokenType
//import com.r3.corda.sdk.token.workflow.flows.distribution.UpdateDistributionList
//import com.r3.corda.sdk.token.workflow.selection.TokenSelection
//import com.r3.corda.sdk.token.workflow.selection.generateMoveNonFungible
//import com.r3.corda.sdk.token.workflow.utilities.requireKnownConfidentialIdentity
//import net.corda.core.contracts.Amount
//import net.corda.core.flows.FinalityFlow
//import net.corda.core.flows.FlowLogic
//import net.corda.core.flows.FlowSession
//import net.corda.core.flows.InitiatedBy
//import net.corda.core.flows.InitiatingFlow
//import net.corda.core.flows.ReceiveFinalityFlow
//import net.corda.core.identity.AbstractParty
//import net.corda.core.transactions.SignedTransaction
//import net.corda.core.transactions.TransactionBuilder
//import net.corda.core.utilities.ProgressTracker
//import java.security.PublicKey
//
////TODO Scratch - remove that
//@InitiatingFlow
//abstract class MoveTokensFlowX<T : TokenType>(
//        val token: T,
//        val holder: AbstractParty,
//        val session: FlowSession? = null
//) : FlowLogic<SignedTransaction>() {
//    companion object {
//        object GENERATE_MOVE : ProgressTracker.Step("Generating tokens move.")
//        object SIGNING : ProgressTracker.Step("Signing transaction proposal.")
//        object RECORDING : ProgressTracker.Step("Recording signed transaction.") {
//            override fun childProgressTracker() = FinalityFlow.tracker()
//        }
//
//        fun tracker() = ProgressTracker(GENERATE_MOVE, SIGNING, RECORDING)
//    }
//
//    override val progressTracker: ProgressTracker = tracker()
//
//    @Suspendable
//    abstract fun generateMove(): Pair<TransactionBuilder, List<PublicKey>>
//
//    @Suspendable
//    override fun call(): SignedTransaction {
//        val holderParty = serviceHub.identityService.requireKnownConfidentialIdentity(holder)
//        val holderSession = if (session == null) initiateFlow(holderParty) else session
//
//        progressTracker.currentStep = GENERATE_MOVE
//
//        val (builder, keys) = generateMove()
//
//        progressTracker.currentStep = SIGNING
//        // WARNING: At present, the recipient will not be signed up to updates from the token maintainer.
//        val stx: SignedTransaction = serviceHub.signInitialTransaction(builder, keys)
//        progressTracker.currentStep = RECORDING
//        val sessions = if (ourIdentity == holderParty) emptyList() else listOf(holderSession)
//        val finalTx = subFlow(FinalityFlow(transaction = stx, sessions = sessions))
//        // If it's TokenPointer, then update the distribution lists for token maintainers
//        if (token is TokenPointer<*>) {
//            subFlow(UpdateDistributionList.Initiator(token, ourIdentity, holderParty))
//        }
//        return finalTx
//    }
//}
//
////class MoveTokenNonFungible<T : TokenType>(
////        val ownedToken: T,
////        holder: AbstractParty,
////        session: FlowSession? = null
////) : MoveTokensFlowX<T>(ownedToken, holder, session) {
////    @Suspendable
////    override fun generateMove(): Pair<TransactionBuilder, List<PublicKey>> {
////        return generateMoveNonFungible(serviceHub.vaultService, ownedToken, holder, TransactionBuilder())
////    }
////}
////
////open class MoveTokenFungible<T : TokenType>(
////        val amount: Amount<T>,
////        holder: AbstractParty,
////        session: FlowSession? = null
////) : MoveTokensFlowX<T>(amount.token, holder, session) {
////    @Suspendable
////    override fun generateMove(): Pair<TransactionBuilder, List<PublicKey>> {
////        val tokenSelection = TokenSelection(serviceHub)
////        return tokenSelection.generateMove(TransactionBuilder(), amount, holder)
////    }
////}
////
////@InitiatedBy(MoveTokensFlow::class)
////class MoveTokensFlowResponder(val otherSession: FlowSession) : FlowLogic<Unit>() {
////    @Suspendable
////    override fun call() {
////        // Resolve the move transaction.
////        if (!serviceHub.myInfo.isLegalIdentity(otherSession.counterparty)) {
////            subFlow(ReceiveFinalityFlow(otherSession))
////        }
////    }
////}