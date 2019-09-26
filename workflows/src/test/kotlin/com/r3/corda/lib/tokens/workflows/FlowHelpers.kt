package com.r3.corda.lib.tokens.workflows

import com.r3.corda.lib.tokens.contracts.states.EvolvableTokenType
import com.r3.corda.lib.tokens.contracts.types.TokenType
import com.r3.corda.lib.tokens.contracts.utilities.heldBy
import com.r3.corda.lib.tokens.contracts.utilities.issuedBy
import com.r3.corda.lib.tokens.contracts.utilities.withNotary
import com.r3.corda.lib.tokens.workflows.flows.rpc.ConfidentialIssueTokens
import com.r3.corda.lib.tokens.workflows.flows.rpc.ConfidentialMoveFungibleTokens
import com.r3.corda.lib.tokens.workflows.flows.rpc.ConfidentialMoveNonFungibleTokens
import com.r3.corda.lib.tokens.workflows.flows.rpc.ConfidentialRedeemFungibleTokens
import com.r3.corda.lib.tokens.workflows.flows.rpc.CreateEvolvableTokens
import com.r3.corda.lib.tokens.workflows.flows.rpc.IssueTokens
import com.r3.corda.lib.tokens.workflows.flows.rpc.MoveFungibleTokens
import com.r3.corda.lib.tokens.workflows.flows.rpc.MoveNonFungibleTokens
import com.r3.corda.lib.tokens.workflows.flows.rpc.RedeemFungibleTokens
import com.r3.corda.lib.tokens.workflows.flows.rpc.RedeemNonFungibleTokens
import com.r3.corda.lib.tokens.workflows.flows.rpc.UpdateEvolvableToken
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
fun <T : EvolvableTokenType> StartedMockNode.createEvolvableToken(evolvableToken: T, notary: Party, observers: List<Party> = emptyList()): CordaFuture<SignedTransaction> {
    return transaction { startFlow(CreateEvolvableTokens(transactionState = evolvableToken withNotary notary, observers = observers)) }
}

/** Update an evolvable token. */
fun <T : EvolvableTokenType> StartedMockNode.updateEvolvableToken(old: StateAndRef<T>, new: T, observers: List<Party> = emptyList()): CordaFuture<SignedTransaction> {
    return transaction { startFlow(UpdateEvolvableToken(oldStateAndRef = old, newState = new, observers = observers)) }
}

fun StartedMockNode.issueFungibleTokens(
        issueTo: StartedMockNode,
        amount: Amount<TokenType>,
        anonymous: Boolean = true,
        observers: List<Party> = emptyList()
): CordaFuture<SignedTransaction> {
    return transaction {
        if (anonymous) {
            startFlow(ConfidentialIssueTokens(listOf(amount issuedBy legalIdentity() heldBy issueTo.legalIdentity()), observers))
        } else {
            startFlow(IssueTokens(listOf(amount issuedBy legalIdentity() heldBy issueTo.legalIdentity()), observers))
        }
    }
}

fun StartedMockNode.issueNonFungibleTokens(
        token: TokenType,
        issueTo: StartedMockNode,
        anonymous: Boolean = true,
        observers: List<Party> = emptyList()
): CordaFuture<SignedTransaction> {
    return transaction {
        if (anonymous) {
            startFlow(ConfidentialIssueTokens(listOf(token issuedBy legalIdentity() heldBy issueTo.legalIdentity()), observers))
        } else {
            startFlow(IssueTokens(listOf(token issuedBy legalIdentity() heldBy issueTo.legalIdentity()), observers))
        }
    }
}

fun StartedMockNode.moveFungibleTokens(
        amount: Amount<TokenType>,
        owner: StartedMockNode,
        anonymous: Boolean = true,
        observers: List<Party> = emptyList()
): CordaFuture<SignedTransaction> {
    return transaction {
        if (anonymous) {
            startFlow(ConfidentialMoveFungibleTokens(PartyAndAmount(owner.legalIdentity(), amount), observers))
        } else {
            startFlow(MoveFungibleTokens(PartyAndAmount(owner.legalIdentity(), amount), observers))
        }
    }
}

fun StartedMockNode.moveNonFungibleTokens(
        token: TokenType,
        owner: StartedMockNode,
        anonymous: Boolean = true,
        observers: List<Party> = emptyList()
): CordaFuture<SignedTransaction> {
    return transaction {
        if (anonymous) {
            startFlow(ConfidentialMoveNonFungibleTokens(PartyAndToken(owner.legalIdentity(), token), observers))
        } else {
            startFlow(MoveNonFungibleTokens(PartyAndToken(owner.legalIdentity(), token), observers))
        }
    }
}

fun StartedMockNode.redeemTokens(
        token: TokenType,
        issuer: StartedMockNode,
        amount: Amount<TokenType>? = null,
        anonymous: Boolean = true,
        observers: List<Party> = emptyList()
): CordaFuture<SignedTransaction> {
    return if (anonymous && amount != null) {
        startFlow(ConfidentialRedeemFungibleTokens(amount, issuer.legalIdentity(), observers))
    } else if (amount == null) {
        startFlow(RedeemNonFungibleTokens(token, issuer.legalIdentity(), observers))
    } else {
        startFlow(RedeemFungibleTokens(amount, issuer.legalIdentity(), observers))
    }
}