package com.r3.corda.lib.tokens.workflows

import net.corda.core.concurrent.CordaFuture
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.LinearState
import net.corda.core.contracts.StateAndRef
import net.corda.core.crypto.SecureHash
import net.corda.core.internal.concurrent.doneFuture
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.node.services.Vault
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.toFuture
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.getOrThrow
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.StartedMockNode
import java.time.Duration
import java.util.concurrent.TimeoutException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

const val DEFAULT_WATCH_FOR_RECORDS_TRANSACTION_TIMEOUT = 10L
const val DEFAULT_WATCH_FOR_NOT_RECORDS_TRANSACTION_TIMEOUT = 5L

fun StartedMockNode.legalIdentity() = services.myInfo.legalIdentities.first()

/** From a transaction which produces a single output, retrieve that output. */
inline fun <reified T : ContractState> SignedTransaction.singleOutput() = tx.outRefsOfType<T>().single()

/** Gets the linearId from a LinearState. */
inline fun <reified T : LinearState> StateAndRef<T>.linearId() = state.data.linearId

/**
 * It's internal because it uses deprecated [internalVerifiedTransactionsFeed].
 */
fun CordaRPCOps.watchForTransaction(tx: SignedTransaction): CordaFuture<SignedTransaction> {
    val (snapshot, feed) = internalVerifiedTransactionsFeed()
    return if (tx in snapshot) {
        doneFuture(tx)
    } else {
        feed.filter { it == tx }.toFuture()
    }
}

/**
 * Check that each node has recorded the provided transaction. Since Corda is asynchronous, we must wait for the network
 * to stop moving before checking that transactions are recorded.
 *
 * See [assertNotHasTransaction] for the negative counterpart to this assert method.
 *
 * Unfortunately, there is no way to reach the network from the node; therefore, the network must be provided
 * explicitly.
 */
fun assertHasTransaction(tx: SignedTransaction, network: MockNetwork, vararg nodes: StartedMockNode) {
    network.waitQuiescent()
    nodes.forEach {
        assertNotNull(it.services.validatedTransactions.getTransaction(tx.id), "Could not find ${tx.id} in ${it.legalIdentity()} validated transactions")
    }
}

/**
 * Check that each node has _not_ recorded the provided transaction. Since Corda is asynchronous, we must wait for the
 * network to stop moving before checking that transactions are not recorded.
 *
 * See [assertHasTransaction] for the positive counterpart to this assert method.
 *
 * Unfortunately, there is no way to reach the network from the node; therefore, the network must be provided
 * explicitly.
 */
fun assertNotHasTransaction(tx: SignedTransaction, network: MockNetwork, vararg nodes: StartedMockNode) {
    network.waitQuiescent()
    nodes.forEach {
        assertNull(it.services.validatedTransactions.getTransaction(tx.id), "Found ${tx.id} in ${it.legalIdentity()} validated transactions")
    }
}

inline fun <reified T : ContractState> assertHasStateAndRef(stateAndRef: StateAndRef<T>, vararg nodes: StartedMockNode, stateStatus: Vault.StateStatus = Vault.StateStatus.UNCONSUMED) {
    val criteria = QueryCriteria.VaultQueryCriteria(stateStatus)
    nodes.forEach {
        assert(it.services.vaultService.queryBy<T>(criteria).states.contains(stateAndRef)) { "Could not find $stateAndRef in ${it.legalIdentity()} vault" }
    }
}

inline fun <reified T : ContractState> assertNotHasStateAndRef(stateAndRef: StateAndRef<T>, vararg nodes: StartedMockNode, stateStatus: Vault.StateStatus = Vault.StateStatus.UNCONSUMED) {
    val criteria = QueryCriteria.VaultQueryCriteria(stateStatus)
    nodes.forEach {
        assert(!it.services.vaultService.queryBy<T>(criteria).states.contains(stateAndRef)) { "Found $stateAndRef in ${it.legalIdentity()} vault" }
    }
}
