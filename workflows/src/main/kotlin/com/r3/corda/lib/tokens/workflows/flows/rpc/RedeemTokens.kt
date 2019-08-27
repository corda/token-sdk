package com.r3.corda.lib.tokens.workflows.flows.rpc

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.lib.tokens.contracts.types.TokenType
import com.r3.corda.lib.tokens.workflows.flows.redeem.ConfidentialRedeemFungibleTokensFlow
import com.r3.corda.lib.tokens.workflows.flows.redeem.ConfidentialRedeemFungibleTokensFlowHandler
import com.r3.corda.lib.tokens.workflows.flows.redeem.RedeemFungibleTokensFlow
import com.r3.corda.lib.tokens.workflows.flows.redeem.RedeemNonFungibleTokensFlow
import com.r3.corda.lib.tokens.workflows.flows.redeem.RedeemTokensFlowHandler
import com.r3.corda.lib.tokens.workflows.utilities.sessionsForParties
import net.corda.core.contracts.Amount
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.flows.InitiatedBy
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.StartableByRPC
import net.corda.core.flows.StartableByService
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.transactions.SignedTransaction

@StartableByService
@StartableByRPC
@InitiatingFlow
class RedeemFungibleTokens
@JvmOverloads
constructor(
        val amount: Amount<TokenType>,
        val issuer: Party,
        val observers: List<Party> = emptyList(),
        val queryCriteria: QueryCriteria? = null,
        val changeHolder: AbstractParty? = null
) : FlowLogic<SignedTransaction>() {
    @Suspendable
    override fun call(): SignedTransaction {
        val observerSessions = sessionsForParties(observers)
        val issuerSession = initiateFlow(issuer)
        return subFlow(RedeemFungibleTokensFlow(amount, issuerSession, changeHolder
                ?: ourIdentity, observerSessions, queryCriteria))
    }
}

@InitiatedBy(RedeemFungibleTokens::class)
class RedeemFungibleTokensHandler(val otherSession: FlowSession) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() = subFlow(RedeemTokensFlowHandler(otherSession))
}

@StartableByService
@StartableByRPC
@InitiatingFlow
class RedeemNonFungibleTokens
@JvmOverloads
constructor(
        val heldToken: TokenType,
        val issuer: Party,
        val observers: List<Party> = emptyList()
) : FlowLogic<SignedTransaction>() {
    @Suspendable
    override fun call(): SignedTransaction {
        val observerSessions = sessionsForParties(observers)
        val issuerSession = initiateFlow(issuer)
        return subFlow(RedeemNonFungibleTokensFlow(heldToken, issuerSession, observerSessions))
    }
}

@InitiatedBy(RedeemNonFungibleTokens::class)
class RedeemNonFungibleTokensHandler(val otherSession: FlowSession) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() = subFlow(RedeemTokensFlowHandler(otherSession))
}

/* Confidential flows. */
// We don't need confidential non fungible redeem, because there are no outputs.
@StartableByService
@StartableByRPC
@InitiatingFlow
class ConfidentialRedeemFungibleTokens
@JvmOverloads
constructor(
        val amount: Amount<TokenType>,
        val issuer: Party,
        val observers: List<Party> = emptyList(),
        val queryCriteria: QueryCriteria? = null
) : FlowLogic<SignedTransaction>() {
    @Suspendable
    override fun call(): SignedTransaction {
        val observerSessions = sessionsForParties(observers)
        val issuerSession = initiateFlow(issuer)
        return subFlow(ConfidentialRedeemFungibleTokensFlow(amount, issuerSession, observerSessions, queryCriteria))
    }
}

@InitiatedBy(ConfidentialRedeemFungibleTokens::class)
class ConfidentialRedeemFungibleTokensHandler(val otherSession: FlowSession) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() = subFlow(ConfidentialRedeemFungibleTokensFlowHandler(otherSession))
}
