package com.r3.corda.sdk.token.workflow

import com.r3.corda.sdk.token.contracts.states.EvolvableTokenType
import com.r3.corda.sdk.token.contracts.types.TokenType
import com.r3.corda.sdk.token.contracts.utilities.heldBy
import com.r3.corda.sdk.token.contracts.utilities.issuedBy
import com.r3.corda.sdk.token.contracts.utilities.withNotary
import com.r3.corda.sdk.token.workflow.flows.CreateEvolvableToken
import com.r3.corda.sdk.token.workflow.flows.RedeemToken
import com.r3.corda.sdk.token.workflow.flows.UpdateEvolvableToken
import com.r3.corda.sdk.token.workflow.flows.issue.ConfidentialIssueTokensFlow
import com.r3.corda.sdk.token.workflow.flows.issue.MakeIssueTokensFlow
import com.r3.corda.sdk.token.workflow.flows.move.ConfidentialMoveTokensFlow
import com.r3.corda.sdk.token.workflow.flows.move.MakeMoveTokenFlow
import net.corda.core.concurrent.CordaFuture
import net.corda.core.contracts.Amount
import net.corda.core.contracts.StateAndRef
import net.corda.core.identity.Party
import net.corda.core.transactions.SignedTransaction
import net.corda.testing.node.StartedMockNode

/** Create an evolvable token. */
fun <T : EvolvableTokenType> StartedMockNode.createEvolvableToken(evolvableToken: T, notary: Party): CordaFuture<SignedTransaction> {
    return transaction { startFlow(CreateEvolvableToken(transactionState = evolvableToken withNotary notary)) }
}

/** Update an evolvable token. */
fun <T : EvolvableTokenType> StartedMockNode.updateEvolvableToken(old: StateAndRef<T>, new: T): CordaFuture<SignedTransaction> {
    return transaction { startFlow(UpdateEvolvableToken(oldStateAndRef = old, newState = new)) }
}

fun <T : TokenType> StartedMockNode.issueTokens(
        token: T,
        issueTo: StartedMockNode,
        amount: Amount<T>? = null,
        anonymous: Boolean = true
): CordaFuture<SignedTransaction> {
    return transaction {
        if (anonymous) {
            if (amount == null) {
                startFlow(ConfidentialIssueTokensFlow(listOf(token issuedBy legalIdentity() heldBy issueTo.legalIdentity()), emptySet()))
            } else {
                startFlow(ConfidentialIssueTokensFlow(listOf(amount issuedBy legalIdentity() heldBy issueTo.legalIdentity()), emptySet()))
            }
        } else {
            if (amount == null) {
                startFlow(MakeIssueTokensFlow(token, issueTo.legalIdentity()))
            } else {
                startFlow(MakeIssueTokensFlow(amount, issueTo.legalIdentity()))
            }
        }
    }
}

fun <T : TokenType> StartedMockNode.moveTokens(
        token: T,
        owner: StartedMockNode,
        amount: Amount<T>? = null,
        anonymous: Boolean = true
): CordaFuture<SignedTransaction> {
    return transaction {
        if (anonymous) {
            if (amount == null) {
                startFlow(ConfidentialMoveTokensFlow(token, owner.legalIdentity()
                ))
            } else {
                startFlow(ConfidentialMoveTokensFlow(amount, owner.legalIdentity()
                ))
            }
        } else {
            if (amount == null) {
                startFlow(MakeMoveTokenFlow(token, owner.legalIdentity()))
            } else {
                startFlow(MakeMoveTokenFlow(amount, owner.legalIdentity()))
            }
        }
    }

}

fun <T : TokenType> StartedMockNode.redeemTokens(
        token: T,
        issuer: StartedMockNode,
        amount: Amount<T>? = null,
        anonymous: Boolean = true
): CordaFuture<SignedTransaction> {
    return startFlow(RedeemToken.InitiateRedeem(
            ownedToken = token,
            issuer = issuer.legalIdentity(),
            amount = amount,
            anonymous = anonymous
    ))
}