//package com.r3.corda.sdk.token.workflow.flows.redeem
//
//import com.r3.corda.sdk.token.workflow.flows.finality.ObserverAwareFinalityFlow
//import net.corda.core.flows.FlowLogic
//import net.corda.core.flows.FlowSession
//import net.corda.core.transactions.SignedTransaction
//import net.corda.core.transactions.TransactionBuilder
//
////TODO will be implemented as other PR
//class MakeRedeemTokenFlow(): FlowLogic<SignedTransaction>() {
//    val sessions = emptySet<FlowSession>()
//    override fun call(): SignedTransaction {
//        val txBuilder = subFlow(AddRedeemTokenFlow())
//        return subFlow(ObserverAwareFinalityFlow(txBuilder, sessions))
//    }
//}
//
//
//class AddRedeemTokenFlow(): FlowLogic<TransactionBuilder>() {
//    override fun call(): TransactionBuilder {
//        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
//    }
//}