package com.r3.corda.lib.tokens.workflows

import com.r3.corda.lib.tokens.contracts.utilities.issuedBy
import com.r3.corda.lib.tokens.contracts.utilities.of
import com.r3.corda.lib.tokens.contracts.utilities.sumTokenStateAndRefs
import com.r3.corda.lib.tokens.money.BTC
import com.r3.corda.lib.tokens.money.CHF
import com.r3.corda.lib.tokens.money.GBP
import com.r3.corda.lib.tokens.money.USD
import com.r3.corda.lib.tokens.workflows.flows.move.addMoveFungibleTokens
import com.r3.corda.lib.tokens.workflows.internal.selection.TokenSelection
import com.r3.corda.lib.tokens.workflows.types.PartyAndAmount
import com.r3.corda.lib.tokens.workflows.utilities.tokenAmountWithIssuerCriteria
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.getOrThrow
import net.corda.testing.node.StartedMockNode
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import java.util.*
import java.util.concurrent.CompletableFuture
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
        // We need to release the soft lock after acquiring it, this is because we before we used LOCK_AND_SPECIFIED
        // and now we use UNLOCKED_ONLY. The difference is that LOCK_AND_SPECIFIED lets you re lock tokens you have
        // already locked, where as with UNLOCKED_ONLY, the tokens which have already been locked are out of scope for
        // future selections. This _is_ a behavioural change but should only affect unit tests.
        A.transaction { A.services.vaultService.softLockRelease(uuid) }
        assertEquals(gbpTokens.size, one.size)
        val two = A.transaction { tokenSelection.attemptSpend(175.GBP, uuid) }
        A.transaction { A.services.vaultService.softLockRelease(uuid) }
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
            addMoveFungibleTokens(transactionBuilder, A.services, moves, A.legalIdentity())
        }
        println(transactionBuilder.toWireTransaction(A.services))
        // Just using this to check and see if the output is as expected.
        // TODO: Assert something...
    }

    @Test
    fun `select tokens by issuer`() {
        // Issue some more tokens and wait til we are all done.
        val one = I.issueFungibleTokens(A, 1.BTC).toCompletableFuture()
        val two = I.issueFungibleTokens(A, 3.BTC).toCompletableFuture()
        val three = J.issueFungibleTokens(A, 2.BTC).toCompletableFuture()
        val four = J.issueFungibleTokens(A, 4.BTC).toCompletableFuture()
        CompletableFuture.allOf(one, two, three, four)

        val tokenSelection = TokenSelection(A.services)
        val uuid = UUID.randomUUID()

        val resultOne = A.transaction { tokenSelection.attemptSpend(4.BTC, uuid, additionalCriteria = tokenAmountWithIssuerCriteria(BTC, I.legalIdentity())) }
        assertEquals(4.BTC issuedBy I.legalIdentity(), resultOne.sumTokenStateAndRefs())

        // Not enough tokens as only 4 BTC on issuer I.
        assertFailsWith<IllegalStateException> {
            A.transaction { tokenSelection.attemptSpend(5.BTC, uuid, additionalCriteria = tokenAmountWithIssuerCriteria(BTC, I.legalIdentity())) }
        }

        val resultTwo = A.transaction { tokenSelection.attemptSpend(6.BTC, uuid, additionalCriteria = tokenAmountWithIssuerCriteria(BTC, J.legalIdentity())) }
        assertEquals(6.BTC issuedBy J.legalIdentity(), resultTwo.sumTokenStateAndRefs())
    }

    @Test
    fun `should be able to select tokens if you need more than one page to fulfill`() {
        (1..12).map { I.issueFungibleTokens(A, 1 of CHF).getOrThrow() }
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