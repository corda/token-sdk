package com.r3.corda.lib.tokens.workflows

import com.r3.corda.lib.tokens.contracts.states.FungibleToken
import com.r3.corda.lib.tokens.contracts.types.TokenType
import com.r3.corda.lib.tokens.contracts.utilities.issuedBy
import com.r3.corda.lib.tokens.contracts.utilities.sumTokenStateAndRefs
import com.r3.corda.lib.tokens.money.FiatCurrency
import com.r3.corda.lib.tokens.money.GBP
import com.r3.corda.lib.tokens.money.USD
import com.r3.corda.lib.tokens.workflows.utilities.ownedTokenAmountCriteria
import com.r3.corda.lib.tokens.workflows.utilities.ownedTokensByToken
import com.r3.corda.lib.tokens.workflows.utilities.tokenAmountsByToken
import net.corda.core.node.services.queryBy
import net.corda.core.utilities.getOrThrow
import net.corda.testing.node.StartedMockNode
import org.assertj.core.api.Assertions
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class RedeemTokenTest : MockNetworkTest(numberOfNodes = 3) {
    lateinit var A: StartedMockNode
    lateinit var B: StartedMockNode
    lateinit var I: StartedMockNode

    // TODO Refactor to test utils. It's used in other tests too.
    private data class SomeNonFungibleToken(
            override val tokenIdentifier: String = "FOO",
            override val fractionDigits: Int = 0
    ) : TokenType

    private val fooToken = SomeNonFungibleToken("FOO")

    @Before
    override fun initialiseNodes() {
        A = nodes[0]
        B = nodes[1]
        I = nodes[2]
    }

    @Test
    fun `redeem fungible happy path`() {
        val issueTokenTx = I.issueFungibleTokens(A, 100.GBP).getOrThrow()
        A.watchForTransaction(issueTokenTx.id).getOrThrow()
        A.redeemTokens(GBP, I, 100.GBP, true).getOrThrow()
        assertThat(A.services.vaultService.tokenAmountsByToken(GBP).states).isEmpty()
        assertThat(I.services.vaultService.tokenAmountsByToken(GBP).states).isEmpty()
    }

    @Test
    fun `redeem fungible with change`() {
        val issueTokenTx = I.issueFungibleTokens(A, 100.GBP).getOrThrow()
        A.watchForTransaction(issueTokenTx.id).getOrThrow()
        A.redeemTokens(GBP, I, 80.GBP, true).getOrThrow()
        val ownedStates = A.services.vaultService.tokenAmountsByToken(GBP).states
        assertThat(ownedStates).isNotEmpty()
        assertThat(ownedStates.sumTokenStateAndRefs()).isEqualTo(20.GBP issuedBy I.legalIdentity())
        assertThat(I.services.vaultService.queryBy<FungibleToken<FiatCurrency>>(ownedTokenAmountCriteria(GBP, I.legalIdentity())).states).isEmpty()
    }

    @Test
    fun `isufficient balance`() {
        val issueTokenTx = I.issueFungibleTokens(A, 100.GBP).getOrThrow()
        A.watchForTransaction(issueTokenTx.id).getOrThrow()
        Assertions.assertThatThrownBy {
            A.redeemTokens(GBP, I, 200.GBP, true).getOrThrow()
        }.hasMessageContaining("Insufficient spendable states identified for")
    }

    @Test
    fun `different issuers for fungible tokens`() {
        val issueTokenTx = I.issueFungibleTokens(A, 100.GBP).getOrThrow()
        A.watchForTransaction(issueTokenTx.id).getOrThrow()
        val issueTokenTx2 = B.issueFungibleTokens(A, 100.USD).getOrThrow()
        A.watchForTransaction(issueTokenTx2.id).getOrThrow()
        Assertions.assertThatThrownBy {
            A.redeemTokens(USD, I, 100.USD, true).getOrThrow()
        }.hasMessageContaining("Insufficient spendable states identified for ${100.USD}")
    }

    @Test
    fun `redeem non-fungible happy path`() {
        val issueTokenTx = I.issueNonFungibleTokens(fooToken, A).getOrThrow()
        A.watchForTransaction(issueTokenTx.id).getOrThrow()
        assertThat(A.services.vaultService.ownedTokensByToken(fooToken).states).isNotEmpty()
        A.redeemTokens(fooToken, I, null, true).getOrThrow()
        assertThat(A.services.vaultService.ownedTokensByToken(fooToken).states).isEmpty()
        assertThat(I.services.vaultService.ownedTokensByToken(fooToken).states).isEmpty()
    }

    @Test
    fun `redeem tokens from different issuer - non fungible`() {
        val issueTokenTx = I.issueNonFungibleTokens(fooToken, A).getOrThrow()
        A.watchForTransaction(issueTokenTx.id).getOrThrow()
        assertThat(A.services.vaultService.ownedTokensByToken(fooToken).states).isNotEmpty()
        Assertions.assertThatThrownBy {
            A.redeemTokens(fooToken, B, null, true).getOrThrow()
        }.hasMessageContaining("Exactly one owned token of a particular type $fooToken should be in the vault at any one time.")
    }

    @Test
    fun `non fungible two same tokens`() {
        I.issueNonFungibleTokens(fooToken, A).getOrThrow()
        I.issueNonFungibleTokens(fooToken, A).getOrThrow()
        assertThat(A.services.vaultService.ownedTokensByToken(fooToken).states).isNotEmpty()
        Assertions.assertThatThrownBy {
            A.redeemTokens(fooToken, B, null, true).getOrThrow()
        }.hasMessageContaining("Exactly one owned token of a particular type $fooToken should be in the vault at any one time.")
    }

    @Test
    fun `redeem states with confidential identities not known to issuer`() {
        val issueTokenTx = I.issueFungibleTokens(A, 100.GBP).getOrThrow()
        A.watchForTransaction(issueTokenTx.id).getOrThrow()
        // Check to see that A was added to I's distribution list.
        val moveTokenTx = A.moveFungibleTokens(50.GBP, B, anonymous = true).getOrThrow()
        B.watchForTransaction(moveTokenTx.id).getOrThrow()
        val redeemTx = B.redeemTokens(GBP, I, 30.GBP, anonymous = true).getOrThrow()
        B.watchForTransaction(redeemTx.id).getOrThrow()
    }
}