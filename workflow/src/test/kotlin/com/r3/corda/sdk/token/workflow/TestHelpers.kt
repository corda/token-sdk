package com.r3.corda.sdk.token.workflow

import net.corda.core.concurrent.CordaFuture
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.LinearState
import net.corda.core.contracts.StateAndRef
import net.corda.core.crypto.SecureHash
import net.corda.core.node.services.Vault
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.getOrThrow
import net.corda.testing.node.StartedMockNode
import java.time.Duration
import java.util.concurrent.TimeoutException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

const val DEFAULT_WATCH_FOR_RECORDS_TRANSACTION_TIMEOUT = 10L
const val DEFAULT_WATCH_FOR_NOT_RECORDS_TRANSACTION_TIMEOUT = 5L

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

fun assertRecordsTransaction(txId: SecureHash, vararg nodes: StartedMockNode, timeout: Long = DEFAULT_WATCH_FOR_RECORDS_TRANSACTION_TIMEOUT) {
    nodes.map {
        it.watchForTransaction(txId)
    }.map {
        assertEquals(txId, it.getOrThrow(Duration.ofSeconds(timeout)).id)
    }
}

fun assertRecordsTransaction(tx: SignedTransaction, vararg nodes: StartedMockNode, timeout: Long = DEFAULT_WATCH_FOR_RECORDS_TRANSACTION_TIMEOUT) {
    assertRecordsTransaction(tx.id, *nodes, timeout = timeout)
}

fun assertNotRecordsTransaction(txId: SecureHash, vararg nodes: StartedMockNode, timeout: Long = DEFAULT_WATCH_FOR_NOT_RECORDS_TRANSACTION_TIMEOUT) {
    nodes.map {
        it.watchForTransaction(txId)
    }.map {
        assertFailsWith<TimeoutException> { it.getOrThrow(Duration.ofSeconds(timeout)) }
    }
}

fun assertNotRecordsTransaction(tx: SignedTransaction, vararg nodes: StartedMockNode, timeout: Long = DEFAULT_WATCH_FOR_NOT_RECORDS_TRANSACTION_TIMEOUT) {
    assertNotRecordsTransaction(tx.id, *nodes, timeout = timeout)
}

inline fun <reified T : ContractState> assertHasStateAndRef(stateAndRef: StateAndRef<T>, vararg nodes: StartedMockNode, stateStatus: Vault.StateStatus = Vault.StateStatus.UNCONSUMED) {
    val criteria = QueryCriteria.VaultQueryCriteria(stateStatus)
    nodes.forEach {
        assert(it.services.vaultService.queryBy<T>(criteria).states.contains(stateAndRef)) { "Could not find $stateAndRef in ${it.legalIdentity()} vault." }
    }
}

inline fun <reified T : ContractState> assertNotHasStateAndRef(stateAndRef: StateAndRef<T>, vararg nodes: StartedMockNode, stateStatus: Vault.StateStatus = Vault.StateStatus.UNCONSUMED) {
    val criteria = QueryCriteria.VaultQueryCriteria(stateStatus)
    nodes.forEach {
        assert(!it.services.vaultService.queryBy<T>(criteria).states.contains(stateAndRef)) { "Found $stateAndRef in ${it.legalIdentity()} vault." }
    }
}
