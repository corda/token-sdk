package com.r3.corda.sdk.token.workflow

import com.r3.corda.sdk.token.contracts.states.EvolvableTokenType
import com.r3.corda.sdk.token.contracts.types.TokenType
import com.r3.corda.sdk.token.contracts.utilities.heldBy
import com.r3.corda.sdk.token.contracts.utilities.issuedBy
import com.r3.corda.sdk.token.contracts.utilities.withNotary
import com.r3.corda.sdk.token.workflow.flows.evolvable.CreateEvolvableToken
import com.r3.corda.sdk.token.workflow.flows.evolvable.UpdateEvolvableToken
import com.r3.corda.sdk.token.workflow.flows.redeem.RedeemToken
import com.r3.corda.sdk.token.workflow.flows.shell.*
import com.r3.corda.sdk.token.workflow.types.PartyAndAmount
import com.r3.corda.sdk.token.workflow.types.PartyAndToken
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

fun <T : TokenType> StartedMockNode.issueFungibleTokens(
        issueTo: StartedMockNode,
        amount: Amount<T>,
        anonymous: Boolean = true
): CordaFuture<SignedTransaction> {
    return transaction {
        if (anonymous) {
            startFlow(ConfidentialIssueTokens(listOf(amount issuedBy legalIdentity() heldBy issueTo.legalIdentity()), emptyList()))
        } else {
            startFlow(IssueTokens(listOf(amount issuedBy legalIdentity() heldBy issueTo.legalIdentity()), emptyList()))
        }
    }
}

fun <T : TokenType> StartedMockNode.issueNonFungibleTokens(
        token: T,
        issueTo: StartedMockNode,
        anonymous: Boolean = true
): CordaFuture<SignedTransaction> {
    return transaction {
        if (anonymous) {
            startFlow(ConfidentialIssueTokens(listOf(token issuedBy legalIdentity() heldBy issueTo.legalIdentity()), emptyList()))
        } else {
            startFlow(IssueTokens(listOf(token issuedBy legalIdentity() heldBy issueTo.legalIdentity()), emptyList()))
        }
    }
}

fun <T : TokenType> StartedMockNode.moveFungibleTokens(
        amount: Amount<T>,
        owner: StartedMockNode,
        anonymous: Boolean = true
): CordaFuture<SignedTransaction> {
    return transaction {
        if (anonymous) {
            startFlow(ConfidentialMoveFungibleTokens(PartyAndAmount(owner.legalIdentity(), amount), emptyList()))
        } else {
            startFlow(MoveFungibleTokens(PartyAndAmount(owner.legalIdentity(), amount), emptyList()))
        }
    }
}

fun <T : TokenType> StartedMockNode.moveNonFungibleTokens(
        token: T,
        owner: StartedMockNode,
        anonymous: Boolean = true
): CordaFuture<SignedTransaction> {
    return transaction {
        if (anonymous) {
            startFlow(ConfidentialMoveNonFungibleTokens(PartyAndToken(owner.legalIdentity(), token), emptyList()))
        } else {
            startFlow(MoveNonFungibleTokens(PartyAndToken(owner.legalIdentity(), token), emptyList()))
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