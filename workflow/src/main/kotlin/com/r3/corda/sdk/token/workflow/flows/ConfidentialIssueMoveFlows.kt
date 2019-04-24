package com.r3.corda.sdk.token.workflow.flows

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.sdk.token.contracts.types.TokenType
import net.corda.core.contracts.Amount
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.flows.InitiatedBy
import net.corda.core.flows.InitiatingFlow
import net.corda.core.identity.Party
import net.corda.core.transactions.SignedTransaction

object ConfidentialIssueFlow {
    @InitiatingFlow
    class Initiator<T : TokenType>(
            val token: T,
            val holder: Party,
            val notary: Party,
            val amount: Amount<T>? = null
    ) : FlowLogic<SignedTransaction>() {
        @Suspendable
        override fun call(): SignedTransaction {
            val holderSession = initiateFlow(holder)
            val confidentialHolder = subFlow(RequestConfidentialIdentity.Initiator(holderSession)).party.anonymise()
            return subFlow(IssueToken.Initiator(token, confidentialHolder, notary, amount, holderSession))
        }
    }

    @InitiatedBy(Initiator::class)
    class Responder(val otherSession: FlowSession) : FlowLogic<Unit>() {
        @Suspendable
        override fun call() {
            subFlow(RequestConfidentialIdentity.Responder(otherSession))
            subFlow(IssueToken.Responder(otherSession))
        }
    }
}

object ConfidentialMoveFlow {
    @InitiatingFlow
    class Initiator<T : TokenType>(
            val ownedToken: T,
            val holder: Party,
            val amount: Amount<T>? = null
    ) : FlowLogic<SignedTransaction>() {
        @Suspendable
        override fun call(): SignedTransaction {
            val holderSession = initiateFlow(holder)
            val confidentialHolder = subFlow(RequestConfidentialIdentity.Initiator(holderSession)).party.anonymise()
            return if (amount == null) {
                subFlow(MoveTokenNonFungible(ownedToken, holder, holderSession))
            } else {
                subFlow(MoveTokenFungible(amount, confidentialHolder, holderSession))
            }
        }
    }

    @InitiatedBy(Initiator::class)
    class Responder(val otherSession: FlowSession) : FlowLogic<Unit>() {
        @Suspendable
        override fun call() {
            subFlow(RequestConfidentialIdentity.Responder(otherSession))
            subFlow(MoveToken.Responder(otherSession))
        }
    }
}