package com.r3.corda.sdk.token.workflow.flows.shell

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.sdk.token.contracts.types.TokenType
import com.r3.corda.sdk.token.workflow.flows.redeem.ConfidentialRedeemFungibleTokensFlow
import com.r3.corda.sdk.token.workflow.flows.redeem.ConfidentialRedeemFungibleTokensFlowHandler
import com.r3.corda.sdk.token.workflow.flows.redeem.RedeemFungibleTokensFlow
import com.r3.corda.sdk.token.workflow.flows.redeem.RedeemNonFungibleTokensFlow
import com.r3.corda.sdk.token.workflow.flows.redeem.RedeemTokensFlowHandler
import com.r3.corda.sdk.token.workflow.utilities.sessionsForParties
import net.corda.core.contracts.Amount
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.flows.InitiatedBy
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.StartableByRPC
import net.corda.core.flows.StartableByService
import net.corda.core.identity.Party
import net.corda.core.transactions.SignedTransaction

@StartableByService
@StartableByRPC
@InitiatingFlow
class RedeemFungibleTokens<T : TokenType>
@JvmOverloads
constructor(
        val amount: Amount<T>,
        val issuer: Party,
        val observers: List<Party> = emptyList()
//        val queryCriteria: QueryCriteria? = null,
) : FlowLogic<SignedTransaction>() {

    @Suspendable
    override fun call(): SignedTransaction {
        val observerSessions = sessionsForParties(observers)
        val issuerSession = initiateFlow(issuer)
        return subFlow(RedeemFungibleTokensFlow(amount, issuer, ourIdentity, issuerSession, observerSessions))
    }
}

@InitiatedBy(RedeemFungibleTokens::class)
class RedeemFungibleTokensHandler<T : TokenType>(val otherSession: FlowSession) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() = subFlow(RedeemTokensFlowHandler<T>(otherSession))
}

@StartableByService
@StartableByRPC
@InitiatingFlow
class RedeemNonFungibleTokens<T : TokenType>
@JvmOverloads
constructor(
        val ownedToken: T,
        val issuer: Party,
        val observers: List<Party> = emptyList()
//        val queryCriteria: QueryCriteria? = null
) : FlowLogic<SignedTransaction>() {
    @Suspendable
    override fun call(): SignedTransaction {
        val observerSessions = sessionsForParties(observers)
        val issuerSession = initiateFlow(issuer)
        return subFlow(RedeemNonFungibleTokensFlow(ownedToken, issuer, issuerSession, observerSessions))
    }
}

@InitiatedBy(RedeemNonFungibleTokens::class)
class RedeemNonFungibleTokensHandler<T : TokenType>(val otherSession: FlowSession) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() = subFlow(RedeemTokensFlowHandler<T>(otherSession))
}

/* Confidential flows. */
// We don't need confidential non fungible redeem, because there are no outputs.
@StartableByService
@StartableByRPC
@InitiatingFlow
class ConfidentialRedeemFungibleTokens<T : TokenType>
@JvmOverloads
constructor(
        val amount: Amount<T>,
        val issuer: Party,
        val observers: List<Party> = emptyList()
//        val queryCriteria: QueryCriteria? = null
) : FlowLogic<SignedTransaction>() {
    @Suspendable
    override fun call(): SignedTransaction {
        val observerSessions = sessionsForParties(observers)
        val issuerSession = initiateFlow(issuer)
        return subFlow(ConfidentialRedeemFungibleTokensFlow(amount, issuer, issuerSession, observerSessions))
    }
}

@InitiatedBy(ConfidentialRedeemFungibleTokens::class)
class ConfidentialRedeemFungibleTokensHandler<T : TokenType>(val otherSession: FlowSession) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() = subFlow(ConfidentialRedeemFungibleTokensFlowHandler<T>(otherSession))
}
