package com.r3.corda.sdk.token.workflow.flows

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.sdk.token.contracts.types.TokenType
import com.r3.corda.sdk.token.workflow.statesAndContracts.TradeConditions
import com.r3.corda.sdk.token.workflow.statesAndContracts.TradeConditionsContract
import net.corda.core.contracts.Amount
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.flows.FlowSession
import net.corda.core.flows.InitiatedBy
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.StartableByRPC
import net.corda.core.identity.Party
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.unwrap

object RedeemTokenWithTradeConditions {

    @InitiatingFlow
    @StartableByRPC
    class InitiateRedeem<T : TokenType>(
            ownedToken: T,
            issuer: Party,
            amount: Amount<T>? = null,
            anonymous: Boolean = true,
            val conditions: String
    ) : RedeemToken.Primary<T>(ownedToken, issuer, amount, anonymous) {

        @Suspendable
        override fun extraFlow(issuerSession: FlowSession) {
            issuerSession.send(TradeConditions(ourIdentity, conditions, UniqueIdentifier()))
        }
    }

    @InitiatedBy(InitiateRedeem::class)
    class IssuerResponder<T : TokenType>(otherSession: FlowSession) : RedeemToken.Secondary<T>(otherSession) {

        @Suspendable
        override fun extraFlow(builder: TransactionBuilder) {

            val tradeConditions = otherSession.receive<TradeConditions>().unwrap { it }

            builder.apply {
                addCommand(data = TradeConditionsContract.TradeCommand(), keys = listOf(tradeConditions.owner.owningKey))
                addOutputState(state = tradeConditions)
            }

        }
    }
}