package com.r3.corda.sdk.token.workflow

import com.r3.corda.sdk.token.contracts.types.FixedTokenType
import com.r3.corda.sdk.token.contracts.utilities.issuedBy
import com.r3.corda.sdk.token.contracts.utilities.sumTokenStateAndRefs
import com.r3.corda.sdk.token.money.GBP
import com.r3.corda.sdk.token.money.USD
import com.r3.corda.sdk.token.workflow.utilities.ownedTokenAmountsByToken
import com.r3.corda.sdk.token.workflow.utilities.ownedTokensByToken
import net.corda.core.utilities.getOrThrow
import net.corda.testing.node.StartedMockNode
import org.assertj.core.api.Assertions
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal

class RedeemTokenTest : MockNetworkTest(numberOfNodes = 3) {
    lateinit var A: StartedMockNode
    lateinit var B: StartedMockNode
    lateinit var I: StartedMockNode

    // TODO Refactor to test utils. It's used in other tests too.
    private data class SomeNonFungibleToken(
            override val tokenIdentifier: String = "FOO",
            override val displayTokenSize: BigDecimal = BigDecimal.ONE
    ) : FixedTokenType() {
        override val tokenClass: String get() = javaClass.canonicalName
    }

    private val fooToken = SomeNonFungibleToken("FOO")

    @Before
    override fun initialiseNodes() {
        A = nodes[0]
        B = nodes[1]
        I = nodes[2]
    }

    @Test
    fun `redeem fungible happy path`() {
        val issueTokenTx = I.issueTokens(GBP, A, 100.GBP).getOrThrow()
        A.watchForTransaction(issueTokenTx.id).getOrThrow()
        A.redeemTokens(GBP, I, 100.GBP, true).getOrThrow()
        assertThat(A.services.vaultService.ownedTokenAmountsByToken(GBP).states).isEmpty()
        assertThat(I.services.vaultService.ownedTokenAmountsByToken(GBP).states).isEmpty()
    }

    @Test
    fun `redeem fungible with change`() {
        val issueTokenTx = I.issueTokens(GBP, A, 100.GBP).getOrThrow()
        A.watchForTransaction(issueTokenTx.id).getOrThrow()
        A.redeemTokens(GBP, I, 80.GBP, true).getOrThrow()
        val ownedStates = A.services.vaultService.ownedTokenAmountsByToken(GBP).states
        assertThat(ownedStates).isNotEmpty()
        assertThat(ownedStates.sumTokenStateAndRefs()).isEqualTo(20.GBP issuedBy I.legalIdentity())
        assertThat(I.services.vaultService.ownedTokenAmountsByToken(GBP).states).isEmpty()
    }

    @Test
    fun `isufficient balance`() {
        val issueTokenTx = I.issueTokens(GBP, A, 100.GBP).getOrThrow()
        A.watchForTransaction(issueTokenTx.id).getOrThrow()
        Assertions.assertThatThrownBy {
            A.redeemTokens(GBP, I, 200.GBP, true).getOrThrow()
        }.hasMessageContaining("Insufficient spendable states identified for")
    }

    @Test
    fun `different issuers for fungible tokens`() {
        val issueTokenTx = I.issueTokens(GBP, A, 100.GBP).getOrThrow()
        A.watchForTransaction(issueTokenTx.id).getOrThrow()
        val issueTokenTx2 = B.issueTokens(USD, A, 100.USD).getOrThrow()
        A.watchForTransaction(issueTokenTx2.id).getOrThrow()
        Assertions.assertThatThrownBy {
            A.redeemTokens(USD, I, 100.USD, true).getOrThrow()
        }.hasMessageContaining("Insufficient spendable states identified for ${100.USD}")
    }

    @Test
    fun `redeem non-fungible happy path`() {
        val issueTokenTx = I.issueTokens(fooToken, A).getOrThrow()
        A.watchForTransaction(issueTokenTx.id).getOrThrow()
        assertThat(A.services.vaultService.ownedTokensByToken(fooToken).states).isNotEmpty()
        A.redeemTokens(fooToken, I, null, true).getOrThrow()
        assertThat(A.services.vaultService.ownedTokensByToken(fooToken).states).isEmpty()
        assertThat(I.services.vaultService.ownedTokensByToken(fooToken).states).isEmpty()
    }

    @Test
    fun `redeem tokens from different issuer - non fungible`() {
        val issueTokenTx = I.issueTokens(fooToken, A).getOrThrow()
        A.watchForTransaction(issueTokenTx.id).getOrThrow()
        assertThat(A.services.vaultService.ownedTokensByToken(fooToken).states).isNotEmpty()
        Assertions.assertThatThrownBy {
            A.redeemTokens(fooToken, B, null, true).getOrThrow()
        }.hasMessageContaining("Exactly one owned token of a particular type $fooToken should be in the vault at any one time.")
    }

    @Test
    fun `non fungible two same tokens`() {
        I.issueTokens(fooToken, A).getOrThrow()
        I.issueTokens(fooToken, A).getOrThrow()
        assertThat(A.services.vaultService.ownedTokensByToken(fooToken).states).isNotEmpty()
        Assertions.assertThatThrownBy {
            A.redeemTokens(fooToken, B, null, true).getOrThrow()
        }.hasMessageContaining("Exactly one owned token of a particular type $fooToken should be in the vault at any one time.")
    }

    @Test
    fun `redeem states with confidential identities not known to issuer`() {
        val issueTokenTx = I.issueTokens(GBP, A, 100.GBP).getOrThrow()
        A.watchForTransaction(issueTokenTx.id).getOrThrow()
        // Check to see that A was added to I's distribution list.
        val moveTokenTx = A.moveTokens(GBP, B, 50.GBP, anonymous = true).getOrThrow()
        B.watchForTransaction(moveTokenTx.id).getOrThrow()
        val redeemTx = B.redeemTokens(GBP, I, 30.GBP, anonymous = true).getOrThrow()
        B.watchForTransaction(redeemTx.id).getOrThrow()
    }
}