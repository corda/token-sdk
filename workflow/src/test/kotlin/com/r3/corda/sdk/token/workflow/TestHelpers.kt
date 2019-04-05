package com.r3.corda.sdk.token.workflow

import com.r3.corda.sdk.token.contracts.states.EvolvableTokenType
import com.r3.corda.sdk.token.contracts.types.TokenType
import com.r3.corda.sdk.token.contracts.utilities.withNotary
import com.r3.corda.sdk.token.workflow.flows.CreateEvolvableToken
import com.r3.corda.sdk.token.workflow.flows.IssueToken
import com.r3.corda.sdk.token.workflow.flows.MoveToken
import com.r3.corda.sdk.token.workflow.flows.UpdateEvolvableToken
import net.corda.core.concurrent.CordaFuture
import net.corda.core.contracts.Amount
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.LinearState
import net.corda.core.contracts.StateAndRef
import net.corda.core.crypto.SecureHash
import net.corda.core.identity.Party
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.getOrThrow
import net.corda.testing.node.StartedMockNode
import java.time.Duration
import java.util.concurrent.TimeoutException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

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

fun assertRecordsTransaction(txId: SecureHash, vararg nodes: StartedMockNode, timeout: Long = 2) {
    nodes.map {
        it.watchForTransaction(txId)
    }.map {
        assertEquals(txId, it.getOrThrow(Duration.ofSeconds(timeout)).id)
    }
}

fun assertRecordsTransaction(tx: SignedTransaction, vararg nodes: StartedMockNode, timeout: Long = 2) {
    assertRecordsTransaction(tx.id, *nodes, timeout = timeout)
}

fun assertNotRecordsTransaction(txId: SecureHash, vararg nodes: StartedMockNode, timeout: Long = 2) {
    nodes.map {
        it.watchForTransaction(txId)
    }.map {
        assertFailsWith<TimeoutException> { it.getOrThrow(Duration.ofSeconds(timeout)) }
    }
}

fun assertNotRecordsTransaction(tx: SignedTransaction, vararg nodes: StartedMockNode, timeout: Long = 2) {
    assertNotRecordsTransaction(tx.id, *nodes, timeout = timeout)
}

/** Create an evolvable token. */
fun <T : EvolvableTokenType> StartedMockNode.createEvolvableToken(evolvableToken: T, notary: Party): CordaFuture<SignedTransaction> {
    return transaction { startFlow(CreateEvolvableToken.Initiator(transactionState = evolvableToken withNotary notary)) }
}

/** Update an evolvable token. */
fun <T : EvolvableTokenType> StartedMockNode.updateEvolvableToken(old: StateAndRef<T>, new: T): CordaFuture<SignedTransaction> {
    return transaction { startFlow(UpdateEvolvableToken.Initiator(oldStateAndRef = old, newState = new)) }
}

fun <T : TokenType> StartedMockNode.issueTokens(
        token: T,
        owner: StartedMockNode,
        notary: StartedMockNode,
        amount: Amount<T>? = null,
        anonymous: Boolean = true
): CordaFuture<SignedTransaction> {
    return transaction {
        startFlow(IssueToken.Initiator(
                token = token,
                owner = owner.legalIdentity(),
                notary = notary.legalIdentity(),
                amount = amount,
                anonymous = anonymous
        ))
    }
}

fun <T : TokenType> StartedMockNode.moveTokens(
        token: T,
        owner: StartedMockNode,
        amount: Amount<T>? = null,
        anonymous: Boolean = true
): CordaFuture<SignedTransaction> {
    return transaction {
        startFlow(MoveToken.Initiator(
                ownedToken = token,
                owner = owner.legalIdentity(),
                amount = amount,
                anonymous = anonymous
        ))
    }

}