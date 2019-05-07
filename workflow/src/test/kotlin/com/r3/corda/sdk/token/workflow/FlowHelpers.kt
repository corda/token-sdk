package com.r3.corda.sdk.token.workflow

import com.r3.corda.sdk.token.contracts.states.EvolvableTokenType
import com.r3.corda.sdk.token.contracts.types.TokenType
import com.r3.corda.sdk.token.contracts.utilities.withNotary
import com.r3.corda.sdk.token.workflow.flows.*
import com.r3.corda.sdk.token.workflow.flows.issue.ConfidentialIssueTokensFlow
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
        notary: StartedMockNode,
        amount: Amount<T>? = null,
        anonymous: Boolean = true
): CordaFuture<SignedTransaction> {
    return transaction {
        if (anonymous) {
            val tokens = emptyList<>()
                    startFlow(ConfidentialIssueTokensFlow)

            startFlow(ConfidentialIssueFlow.Initiator(
                    token = token,
                    holder = issueTo.legalIdentity(),
                    notary = notary.legalIdentity(),
                    amount = amount
            ))
        } else {
            startFlow(IssueToken.Initiator(
                    token = token,
                    issueTo = issueTo.legalIdentity(),
                    notary = notary.legalIdentity(),
                    amount = amount
            ))
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
            startFlow(ConfidentialMoveFlow.Initiator(
                    ownedToken = token,
                    holder = owner.legalIdentity(),
                    amount = amount
            ))
        } else {
            if (amount == null) {
                startFlow(MoveTokenNonFungible(token, owner.legalIdentity()))
            } else {
                startFlow(MoveTokenFungible(amount, owner.legalIdentity()))
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