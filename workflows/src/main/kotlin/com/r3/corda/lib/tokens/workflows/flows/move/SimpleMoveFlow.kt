package com.r3.corda.lib.tokens.workflows.flows.move

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.lib.tokens.contracts.types.TokenType
import com.r3.corda.lib.tokens.contracts.utilities.of
import com.r3.corda.lib.tokens.money.FiatCurrency
import com.r3.corda.lib.tokens.workflows.flows.rpc.MoveFungibleTokensHandler
import com.r3.corda.lib.tokens.workflows.types.PartyAndAmount
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.transactions.SignedTransaction

@StartableByRPC
@StartableByService
@InitiatingFlow
class SimpleMoveFlow(val partyToMoveTo: Party) : FlowLogic<SignedTransaction>() {
    @Suspendable
    override fun call(): SignedTransaction {
        val tt: TokenType = FiatCurrency.getInstance("GBP")
        return subFlow(MoveFungibleTokensFlow(listOf(PartyAndAmount(partyToMoveTo, 1 of tt)), listOf(initiateFlow(partyToMoveTo))))
    }
}

@InitiatedBy(SimpleMoveFlow::class)
class SimpleMoveHandler(val otherSession: FlowSession) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        return subFlow(MoveFungibleTokensHandler(otherSession))
    }

}