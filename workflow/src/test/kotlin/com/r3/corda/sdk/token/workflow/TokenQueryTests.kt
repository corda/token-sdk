package com.r3.corda.sdk.token.workflow

import com.r3.corda.sdk.token.contracts.states.FungibleToken
import com.r3.corda.sdk.token.contracts.states.NonFungibleToken
import com.r3.corda.sdk.token.contracts.types.FixedTokenType
import com.r3.corda.sdk.token.contracts.utilities.issuedBy
import com.r3.corda.sdk.token.contracts.utilities.sumTokensOrThrow
import com.r3.corda.sdk.token.money.BTC
import com.r3.corda.sdk.token.money.GBP
import com.r3.corda.sdk.token.money.USD
import com.r3.corda.sdk.token.workflow.utilities.ownedTokenAmountsByToken
import com.r3.corda.sdk.token.workflow.utilities.ownedTokensByToken
import com.r3.corda.sdk.token.workflow.utilities.ownedTokensByTokenIssuer
import com.r3.corda.sdk.token.workflow.utilities.tokenAmountWithIssuerCriteria
import com.r3.corda.sdk.token.workflow.utilities.tokenBalance
import com.r3.corda.sdk.token.workflow.utilities.tokenBalanceForIssuer
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
    lateinit var I2: StartedMockNode

    @Before
    override fun initialiseNodes() {
        A = nodes[0]
        I = nodes[1]
        I2 = nodes[2]
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
    private val bazToken = SomeNonFungibleToken("BAZ")
    private val allOtherTokens = listOf(fooToken, barToken, bazToken)

    @Before
    fun setUp() {
        // Create some new token amounts.
        I.issueTokens(GBP, A, 100.GBP).getOrThrow()
        I.issueTokens(GBP, A, 50.GBP).getOrThrow()
        I.issueTokens(GBP, A, 25.GBP).getOrThrow()
        I.issueTokens(USD, A, 200.USD).getOrThrow()
        I.issueTokens(USD, A, 100.USD).getOrThrow()
        I.issueTokens(BTC, A, 500.BTC).getOrThrow()
        // Non-fungible tokens.
        I.issueTokens(fooToken, A)
        I.issueTokens(barToken, A)
        I2.issueTokens(bazToken, A) // Different issuer.
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

    @Test
    fun `query for sum of an owned token amount by issuer`() {
        val issueTx = I2.issueTokens(GBP, A, 13.GBP).getOrThrow()
        A.watchForTransaction(issueTx.id).getOrThrow()
        // Perform a custom query to get the balance for a specific token type.
        val gbpBalanceI = A.services.vaultService.tokenBalanceForIssuer(GBP, I.legalIdentity())
        val gbpBalanceI2 = A.services.vaultService.tokenBalanceForIssuer(GBP, I2.legalIdentity())
        assertEquals(gbpTokens.sumTokensOrThrow(), gbpBalanceI)
        assertEquals(13.GBP, gbpBalanceI2)
    }

    @Test
    fun `query owned token amounts with given issuer`() {
        // Fungible
        val issueTx = I2.issueTokens(GBP, A, 13.GBP).getOrThrow()
        A.watchForTransaction(issueTx.id).getOrThrow()
        val issuerCriteria = tokenAmountWithIssuerCriteria(GBP, I2.legalIdentity())
        val gbpI2 = A.services.vaultService.queryBy<FungibleToken<*>>(issuerCriteria).states
        assertEquals(1, gbpI2.size)
        val gbp = gbpI2.first().state.data.amount
        assertEquals(13.GBP issuedBy I2.legalIdentity(), gbp)
    }

    @Test
    fun `query owned token with given issuer`() {
        // Non-fungible
        val fooI2 = A.services.vaultService.ownedTokensByTokenIssuer(fooToken, I2.legalIdentity()).states
        assertEquals(0, fooI2.size)
        val bazI = A.services.vaultService.ownedTokensByTokenIssuer(bazToken, I.legalIdentity()).states
        assertEquals(0, bazI.size)
        val bazI2 = A.services.vaultService.ownedTokensByTokenIssuer(bazToken, I2.legalIdentity()).states
        assertEquals(1, bazI2.size)
    }
}
