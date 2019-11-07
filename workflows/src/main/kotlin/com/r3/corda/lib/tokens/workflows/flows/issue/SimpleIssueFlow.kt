package com.r3.corda.lib.tokens.workflows.flows.issue

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.lib.tokens.contracts.states.FungibleToken
import com.r3.corda.lib.tokens.contracts.types.TokenType
import com.r3.corda.lib.tokens.contracts.utilities.issuedBy
import com.r3.corda.lib.tokens.contracts.utilities.of
import com.r3.corda.lib.tokens.money.FiatCurrency
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.transactions.SignedTransaction

@StartableByRPC
@StartableByService
@InitiatingFlow
class SimpleIssueFlow(val partyToIssueTo: Party) : FlowLogic<SignedTransaction>() {
    @Suspendable
    override fun call(): SignedTransaction {
        val tt: TokenType = FiatCurrency.getInstance("GBP")
        val participantSessions = listOf(partyToIssueTo).filter { it != ourIdentity }.map { initiateFlow(it) }
        return subFlow(IssueTokensFlow(FungibleToken(100 of tt issuedBy ourIdentity, partyToIssueTo), participantSessions))
    }
}


@InitiatedBy(SimpleIssueFlow::class)
class SimpleIssueHandler(val otherSession: FlowSession) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        subFlow(IssueTokensFlowHandler(otherSession))
    }
}