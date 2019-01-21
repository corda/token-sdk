package net.corda.sdk.token.flows

import net.corda.core.node.services.Vault
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.utilities.getOrThrow
import net.corda.sdk.token.MockNetworkTest
import net.corda.sdk.token.states.OwnedTokenAmount
import net.corda.sdk.token.types.money.BTC
import net.corda.sdk.token.types.money.GBP
import net.corda.sdk.token.types.money.USD
import net.corda.sdk.token.utilities.ownedTokenAmountsByToken
import net.corda.sdk.token.utilities.sumOrThrow
import net.corda.sdk.token.utilities.tokenBalance
import net.corda.testing.node.StartedMockNode
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals

class TokenQueryTests : MockNetworkTest(numberOfNodes = 3) {

    lateinit var A: StartedMockNode
    lateinit var I: StartedMockNode

    @Before
    override fun initialiseNodes() {
        A = nodes[0]
        I = nodes[2]
    }

    // List of tokens to create for the tests.
    val gbpTokens = listOf(100.GBP, 50.GBP, 25.GBP)
    val usdTokens = listOf(200.USD, 100.USD)
    val btcTokens = listOf(500.BTC)
    val allTokens = gbpTokens + usdTokens + btcTokens

    @Before
    fun setUp() {
        // Create some new tokens.
        I.issueToken(GBP, A, NOTARY, 100.GBP).getOrThrow()
        I.issueToken(GBP, A, NOTARY, 50.GBP).getOrThrow()
        I.issueToken(GBP, A, NOTARY, 25.GBP).getOrThrow()
        I.issueToken(USD, A, NOTARY, 200.USD).getOrThrow()
        I.issueToken(USD, A, NOTARY, 100.USD).getOrThrow()
        I.issueToken(BTC, A, NOTARY, 500.BTC).getOrThrow()
        network.waitQuiescent()
    }

    @Test
    fun `query for all owned token amounts`() {
        // Query for all tokens and check they are all returned.
        val query = QueryCriteria.FungibleStateQueryCriteria(
                contractStateTypes = setOf(OwnedTokenAmount::class.java),
                relevancyStatus = Vault.RelevancyStatus.RELEVANT
        )
        val states = A.services.vaultService.queryBy<OwnedTokenAmount<*>>(query).states
        assertEquals(allTokens.size, states.size)
    }

    @Test
    fun `query owned token amounts by token`() {
        // Perform a custom query for GBP only tokens.
        val gbp = A.services.vaultService.ownedTokenAmountsByToken(GBP).states
        assertEquals(gbpTokens.size, gbp.size)
        val usd = A.services.vaultService.ownedTokenAmountsByToken(USD).states
        assertEquals(usdTokens.size, usd.size)
        val btc = A.services.vaultService.ownedTokenAmountsByToken(BTC).states
        assertEquals(btcTokens.size, btc.size)
    }

    @Test
    fun `query for sum of an owned token amount`() {
        // Perform a custom query to get the balance for a specific token type.
        val gbpBalance = A.services.vaultService.tokenBalance(GBP)
        assertEquals(gbpTokens.sumOrThrow(), gbpBalance)
    }

}