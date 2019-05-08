package com.r3.corda.sdk.token.workflow.flows.redeem

import com.r3.corda.sdk.token.workflow.flows.finality.ObserverAwareFinalityFlow
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder

// Redeem tokens that take a list of different abstract tokens


class MakeRedeemTokenFlow(): FlowLogic<SignedTransaction>() {
    val sessions = emptySet<FlowSession>() // TODO pass sessions
    // TODO observers etc
    override fun call(): SignedTransaction {
        val txBuilder = subFlow(AddRedeemTokenFlow())
        return subFlow(ObserverAwareFinalityFlow(txBuilder, sessions))
    }
}


class AddRedeemTokenFlow(): FlowLogic<TransactionBuilder>() {
    override fun call(): TransactionBuilder {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }
}