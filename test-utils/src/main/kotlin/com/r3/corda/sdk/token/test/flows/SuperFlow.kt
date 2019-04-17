package com.r3.corda.sdk.token.test.flows

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.sdk.token.money.DigitalCurrency
import com.r3.corda.sdk.token.workflow.flows.IssueToken
import net.corda.core.contracts.Amount
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.StartableByRPC
import net.corda.core.flows.StartableByService

@StartableByRPC
@StartableByService
class SuperFlow : FlowLogic<Unit>() {

    @Suspendable
    override fun call() {
        subFlow(IssueToken.Initiator(
                DigitalCurrency.getInstance("BTC"),
                ourIdentity,
                serviceHub.networkMapCache.notaryIdentities.first(),
                Amount(10, DigitalCurrency.getInstance("BTC"))))
    }
}