package com.r3.corda.lib.tokens.workflows

import com.r3.corda.lib.tokens.contracts.states.EvolvableTokenType
import com.r3.corda.lib.tokens.contracts.types.TokenType
import com.r3.corda.lib.tokens.contracts.utilities.heldBy
import com.r3.corda.lib.tokens.contracts.utilities.issuedBy
import com.r3.corda.lib.tokens.contracts.utilities.withNotary
import com.r3.corda.lib.tokens.workflows.flows.rpc.*
import com.r3.corda.lib.tokens.workflows.types.PartyAndAmount
import com.r3.corda.lib.tokens.workflows.types.PartyAndToken
import com.r3.corda.lib.tokens.workflows.utilities.heldBy
import net.corda.core.concurrent.CordaFuture
import net.corda.core.contracts.Amount
import net.corda.core.contracts.StateAndRef
import net.corda.core.identity.Party
import net.corda.core.transactions.SignedTransaction
import net.corda.testing.node.StartedMockNode

/** Create an evolvable token. */
fun <T : EvolvableTokenType> StartedMockNode.createEvolvableToken(evolvableToken: T, notary: Party): CordaFuture<SignedTransaction> {
    return transaction { startFlow(CreateEvolvableTokens(transactionState = evolvableToken withNotary notary)) }
}

/** Update an evolvable token. */
fun <T : EvolvableTokenType> StartedMockNode.updateEvolvableToken(old: StateAndRef<T>, new: T): CordaFuture<SignedTransaction> {
    return transaction { startFlow(UpdateEvolvableToken(oldStateAndRef = old, newState = new)) }
}

fun StartedMockNode.issueFungibleTokens(
        issueTo: StartedMockNode,
        amount: Amount<TokenType>,
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

fun StartedMockNode.moveFungibleTokens(
        amount: Amount<TokenType>,
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

fun StartedMockNode.redeemTokens(
        token: TokenType,
        issuer: StartedMockNode,
        amount: Amount<TokenType>? = null,
        anonymous: Boolean = true
): CordaFuture<SignedTransaction> {
    return if (anonymous && amount != null) {
        startFlow(ConfidentialRedeemFungibleTokens(amount, issuer.legalIdentity()))
    } else if (amount == null) {
        startFlow(RedeemNonFungibleTokens(token, issuer.legalIdentity()))
    } else {
        startFlow(RedeemFungibleTokens(amount, issuer.legalIdentity()))
    }
}