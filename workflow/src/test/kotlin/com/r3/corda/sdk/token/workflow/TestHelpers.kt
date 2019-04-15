package com.r3.corda.sdk.token.workflow

import com.r3.corda.sdk.token.contracts.states.EvolvableTokenType
import com.r3.corda.sdk.token.contracts.types.TokenType
import com.r3.corda.sdk.token.contracts.utilities.withNotary
import com.r3.corda.sdk.token.workflow.flows.*
import net.corda.core.concurrent.CordaFuture
import net.corda.core.contracts.Amount
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.LinearState
import net.corda.core.contracts.StateAndRef
import net.corda.core.crypto.SecureHash
import net.corda.core.identity.Party
import net.corda.core.node.services.queryBy
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.getOrThrow
import net.corda.testing.node.StartedMockNode
import java.time.Duration
import java.util.concurrent.TimeoutException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

const val DEFAULT_WATCH_FOR_TRANSACTION_TIMEOUT = 6L

fun StartedMockNode.legalIdentity() = services.myInfo.legalIdentities.first()

/** From a transaction which produces a single output, retrieve that output. */
inline fun <reified T : ContractState> SignedTransaction.singleOutput() = tx.outRefsOfType<T>().single()

/** Gets the linearId from a LinearState. */
inline fun <reified T : LinearState> StateAndRef<T>.linearId() = state.data.linearId

/** Check to see if a node recorded a transaction with a particular hash. Return a future signed transaction. */
fun StartedMockNode.watchForTransaction(txId: SecureHash): CordaFuture<SignedTransaction> {
    return transaction { services.validatedTransactions.trackTransaction(txId) }
}

fun StartedMockNode.watchForTransaction(tx: SignedTransaction): CordaFuture<SignedTransaction> {
    return watchForTransaction(tx.id)
}

fun assertRecordsTransaction(txId: SecureHash, vararg nodes: StartedMockNode, timeout: Long = DEFAULT_WATCH_FOR_TRANSACTION_TIMEOUT) {
    nodes.map {
        it.watchForTransaction(txId)
    }.map {
        assertEquals(txId, it.getOrThrow(Duration.ofSeconds(timeout)).id)
    }
}

fun assertRecordsTransaction(tx: SignedTransaction, vararg nodes: StartedMockNode, timeout: Long = DEFAULT_WATCH_FOR_TRANSACTION_TIMEOUT) {
    assertRecordsTransaction(tx.id, *nodes, timeout = timeout)
}

fun assertNotRecordsTransaction(txId: SecureHash, vararg nodes: StartedMockNode, timeout: Long = DEFAULT_WATCH_FOR_TRANSACTION_TIMEOUT) {
    nodes.map {
        it.watchForTransaction(txId)
    }.map {
        assertFailsWith<TimeoutException> { it.getOrThrow(Duration.ofSeconds(timeout)) }
    }
}

fun assertNotRecordsTransaction(tx: SignedTransaction, vararg nodes: StartedMockNode, timeout: Long = DEFAULT_WATCH_FOR_TRANSACTION_TIMEOUT) {
    assertNotRecordsTransaction(tx.id, *nodes, timeout = timeout)
}

inline fun <reified T : ContractState> assertHasStateAndRef(stateAndRef: StateAndRef<T>, vararg nodes: StartedMockNode) {
    nodes.forEach {
        assert(it.services.vaultService.queryBy<T>().states.contains(stateAndRef)) { "Could not find $stateAndRef in ${it.legalIdentity()} vault." }
    }
}

inline fun <reified T : ContractState> assertNotHasStateAndRef(stateAndRef: StateAndRef<T>, vararg nodes: StartedMockNode) {
    nodes.forEach {
        assert(!it.services.vaultService.queryBy<T>().states.contains(stateAndRef)) { "Found $stateAndRef in ${it.legalIdentity()} vault." }
    }
}

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
            startFlow(MoveToken.Initiator(
                    ownedToken = token,
                    holder = owner.legalIdentity(),
                    amount = amount
            ))
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