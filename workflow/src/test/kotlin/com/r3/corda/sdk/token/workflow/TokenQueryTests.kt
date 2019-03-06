package com.r3.corda.sdk.token.workflow

import com.r3.corda.sdk.token.contracts.states.FungibleToken
import com.r3.corda.sdk.token.contracts.states.NonFungibleToken
import com.r3.corda.sdk.token.contracts.types.FixedTokenType
import com.r3.corda.sdk.token.contracts.utilities.sumTokensOrThrow
import com.r3.corda.sdk.token.money.BTC
import com.r3.corda.sdk.token.money.GBP
import com.r3.corda.sdk.token.money.USD
import com.r3.corda.sdk.token.workflow.utilities.ownedTokenAmountsByToken
import com.r3.corda.sdk.token.workflow.utilities.ownedTokensByToken
import com.r3.corda.sdk.token.workflow.utilities.tokenBalance
import net.corda.core.node.services.Vault
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.utilities.getOrThrow
import net.corda.testing.node.StartedMockNode
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal
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
    private val gbpTokens = listOf(100.GBP, 50.GBP, 25.GBP)
    private val usdTokens = listOf(200.USD, 100.USD)
    private val btcTokens = listOf(500.BTC)
    private val allTokens = gbpTokens + usdTokens + btcTokens

    private data class SomeNonFungibleToken(
            override val tokenIdentifier: String = "FOO",
            override val displayTokenSize: BigDecimal = BigDecimal.ONE
    ) : FixedTokenType() {
        override val tokenClass: String get() = javaClass.canonicalName
    }

    private val fooToken = SomeNonFungibleToken("FOO")
    private val barToken = SomeNonFungibleToken("BAR")
    private val allOtherTokens = listOf(fooToken, barToken)

    @Before
    fun setUp() {
        // Create some new token amounts.
        I.issueTokens(GBP, A, NOTARY, 100.GBP).getOrThrow()
        I.issueTokens(GBP, A, NOTARY, 50.GBP).getOrThrow()
        I.issueTokens(GBP, A, NOTARY, 25.GBP).getOrThrow()
        I.issueTokens(USD, A, NOTARY, 200.USD).getOrThrow()
        I.issueTokens(USD, A, NOTARY, 100.USD).getOrThrow()
        I.issueTokens(BTC, A, NOTARY, 500.BTC).getOrThrow()
        // Non-fungible tokens.
        I.issueTokens(fooToken, A, NOTARY)
        I.issueTokens(barToken, A, NOTARY)
        network.waitQuiescent()
    }

    @Test
    fun `query for all owned token amounts`() {
        // Query for all tokens and check they are all returned.
        val query = QueryCriteria.FungibleStateQueryCriteria(
                contractStateTypes = setOf(FungibleToken::class.java),
                relevancyStatus = Vault.RelevancyStatus.RELEVANT
        )
        val states = A.services.vaultService.queryBy<FungibleToken<*>>(query).states
        assertEquals(allTokens.size, states.size)
    }

    @Test
    fun `query for all owned tokens`() {
        // Query for all tokens and check they are all returned.
        val query = QueryCriteria.VaultQueryCriteria(
                contractStateTypes = setOf(NonFungibleToken::class.java),
                relevancyStatus = Vault.RelevancyStatus.RELEVANT
        )
        val states = A.services.vaultService.queryBy<NonFungibleToken<*>>(query).states
        assertEquals(allOtherTokens.size, states.size)
    }

    @Test
    fun `query owned token amounts by token`() {
        // Perform a   custom query for GBP only tokens.
        val gbp = A.services.vaultService.ownedTokenAmountsByToken(GBP).states
        assertEquals(gbpTokens.size, gbp.size)
        val usd = A.services.vaultService.ownedTokenAmountsByToken(USD).states
        assertEquals(usdTokens.size, usd.size)
        val btc = A.services.vaultService.ownedTokenAmountsByToken(BTC).states
        assertEquals(btcTokens.size, btc.size)
    }

    @Test
    fun `query owned tokens by token`() {
        // Perform a custom query for GBP only tokens.
        val foo = A.services.vaultService.ownedTokensByToken(fooToken).states
        assertEquals(1, foo.size)
        val bar = A.services.vaultService.ownedTokensByToken(barToken).states
        assertEquals(1, bar.size)
    }

    @Test
    fun `query for sum of an owned token amount`() {
        // Perform a custom query to get the balance for a specific token type.
        val gbpBalance = A.services.vaultService.tokenBalance(GBP)
        assertEquals(gbpTokens.sumTokensOrThrow(), gbpBalance)
    }

}