package net.corda.sdk.token.workflow

import net.corda.core.utilities.getOrThrow
import net.corda.sdk.token.money.GBP
import net.corda.sdk.token.money.USD
import net.corda.sdk.token.workflow.utilities.selectOwnedTokenAmountsForSpending
import net.corda.testing.node.StartedMockNode
import org.junit.Before
import org.junit.Test
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

// TODO: Improve these tests. E.g. Order states in a list by state ref, so we can specify exactly which will be picked.
class TokenSelectionTests : MockNetworkTest(numberOfNodes = 3) {

    lateinit var A: StartedMockNode
    lateinit var B: StartedMockNode
    lateinit var I: StartedMockNode

    @Before
    override fun initialiseNodes() {
        A = nodes[0]
        B = nodes[1]
        I = nodes[2]
    }

    // List of tokens to create for the tests.
    private val gbpTokens = listOf(100.GBP, 50.GBP, 25.GBP)
    private val usdTokens = listOf(200.USD, 100.USD)

    @Before
    fun setUp() {
        // Create some new token amounts.
        I.issueToken(GBP, A, NOTARY, 100.GBP).getOrThrow()
        I.issueToken(GBP, A, NOTARY, 50.GBP).getOrThrow()
        I.issueToken(GBP, A, NOTARY, 25.GBP).getOrThrow()
        I.issueToken(USD, A, NOTARY, 200.USD).getOrThrow()
        I.issueToken(USD, A, NOTARY, 100.USD).getOrThrow()
        network.waitQuiescent()
    }

    @Test
    fun `select up to available amount with tokens sorted by state ref`() {
        val uuid = UUID.randomUUID()
        val one = A.transaction { A.services.vaultService.selectOwnedTokenAmountsForSpending(160.GBP, uuid) }
        assertEquals(gbpTokens.size, one.size)
        val two = A.transaction { A.services.vaultService.selectOwnedTokenAmountsForSpending(175.GBP, uuid) }
        assertEquals(gbpTokens.size, two.size)
        val results = A.transaction { A.services.vaultService.selectOwnedTokenAmountsForSpending(25.GBP, uuid) }
        assertEquals(1, results.size)
    }

    @Test
    fun `not enough tokens available`() {
        val uuid = UUID.randomUUID()
        assertFailsWith<IllegalStateException> {
            A.transaction {
                A.services.vaultService.selectOwnedTokenAmountsForSpending(176.GBP, uuid)
            }
        }
    }

}