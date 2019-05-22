package com.r3.corda.sdk.token.workflow

import com.r3.corda.sdk.token.contracts.utilities.of
import com.r3.corda.sdk.token.money.CHF
import com.r3.corda.sdk.token.money.GBP
import com.r3.corda.sdk.token.money.USD
import com.r3.corda.sdk.token.workflow.flows.internal.selection.TokenSelection
import com.r3.corda.sdk.token.workflow.flows.move.addMoveTokens
import com.r3.corda.sdk.token.workflow.types.PartyAndAmount
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.getOrThrow
import net.corda.testing.node.StartedMockNode
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

// TODO: Improve these tests. E.g. Order states in a list by state ref, so we can specify exactly which will be picked.
class TokenSelectionTests : MockNetworkTest(numberOfNodes = 4) {

    lateinit var A: StartedMockNode
    lateinit var B: StartedMockNode
    lateinit var I: StartedMockNode
    lateinit var J: StartedMockNode

    @Before
    override fun initialiseNodes() {
        A = nodes[0]
        B = nodes[1]
        I = nodes[2]
        J = nodes[3]
    }

    // List of tokensToIssue to create for the tests.
    private val gbpTokens = listOf(100.GBP, 50.GBP, 25.GBP)
    private val usdTokens = listOf(200.USD, 100.USD)

    @Before
    fun setUp() {
        // Create some new token amounts.
        I.issueFungibleTokens(A, 100.GBP).getOrThrow()
        I.issueFungibleTokens(A, 50.GBP).getOrThrow()
        J.issueFungibleTokens(A, 25.GBP).getOrThrow()
        I.issueFungibleTokens(A, 200.USD).getOrThrow()
        J.issueFungibleTokens(A, 100.USD).getOrThrow()
        network.waitQuiescent()
    }

    @Test
    fun `select up to available amount with tokens sorted by state ref`() {
        val tokenSelection = TokenSelection(A.services)
        val uuid = UUID.randomUUID()
        val one = A.transaction { tokenSelection.attemptSpend(160.GBP, uuid) }
        assertEquals(gbpTokens.size, one.size)
        val two = A.transaction { tokenSelection.attemptSpend(175.GBP, uuid) }
        assertEquals(gbpTokens.size, two.size)
        val results = A.transaction { tokenSelection.attemptSpend(25.GBP, uuid) }
        assertEquals(1, results.size)
    }

    @Test
    fun `not enough tokens available`() {
        val tokenSelection = TokenSelection(A.services)
        val uuid = UUID.randomUUID()
        assertFailsWith<IllegalStateException> {
            A.transaction {
                tokenSelection.attemptSpend(176.GBP, uuid)
            }
        }
    }

    @Test
    fun `generate move test`() {
        val transactionBuilder = TransactionBuilder()
        val moves = listOf(
                PartyAndAmount(B.legalIdentity(), 140.GBP),
                PartyAndAmount(I.legalIdentity(), 30.GBP)
        )

        A.transaction {
            addMoveTokens(transactionBuilder, A.services, moves)
        }
        println(transactionBuilder.toWireTransaction(A.services))
        // Just using this to check and see if the output is as expected.
        // TODO: Assert something...
    }

    @Test
    fun `should be able to select tokens if you need more than one page to fulfill`() {
        (1..12).map{ I.issueFungibleTokens(A, 1 of CHF).getOrThrow() }
        val tokenSelection = TokenSelection(A.services)
        A.transaction {
            val tokens = tokenSelection.attemptSpend(12 of CHF, UUID.randomUUID(), pageSize = 5)
            val value = tokens.fold(0L) { acc, token ->
                acc + token.state.data.amount.quantity
            }
            Assert.assertEquals("should be 12 tokens", 12, tokens.size)
            Assert.assertEquals("value should be 1200", 1200L, value)
        }
    }

    // TODO: Test with different notaries.

}