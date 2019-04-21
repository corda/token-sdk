package com.r3.corda.sdk.token.workflow.flows

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.sdk.token.contracts.types.TokenType
import com.r3.corda.sdk.token.workflow.statesAndContracts.TradeConditions
import com.r3.corda.sdk.token.workflow.statesAndContracts.TradeConditionsContract
import net.corda.core.contracts.Amount
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.contracts.requireThat
import net.corda.core.flows.*
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import java.security.PublicKey

object MoveTokenWithTradeConditions {
    @InitiatingFlow
    @StartableByRPC
    class Initiator<T : TokenType>(
              token: T,
              issueTo: AbstractParty,
              amount: Amount<T>? = null,
              val conditions: String,
              session: FlowSession? = null
    ) : MoveToken.Primary<T>(token, issueTo, amount, session) {

        @Suspendable
        override fun transactionExtra(me: Party,
                                      holderParty: Party,
                                      holderSession: FlowSession,
                                      builder: TransactionBuilder): List<PublicKey> {
            builder.apply {
                addCommand(data = TradeConditionsContract.TradeCommand(), keys = listOf(holderParty.owningKey))
                addOutputState(state = TradeConditions(holderParty, conditions, UniqueIdentifier()))
            }

            return listOf(holderParty.owningKey)
        }
    }

    @InitiatedBy(Initiator::class)
    class Responder(otherSession: FlowSession) : MoveToken.Secondary(otherSession) {

        @Suspendable
        override fun checkTransaction(stx: SignedTransaction) = requireThat {
            val tradeConditions = stx.tx.outputsOfType<TradeConditions>().singleOrNull() ?:
                throw FlowException("The transaction must contain trade conditions.")

            if(tradeConditions.conditions.contains("cheating"))
                throw FlowException("The cheating trade conditions are unacceptable.")

        }
    }
}