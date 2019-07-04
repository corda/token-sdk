package com.r3.corda.lib.tokens.workflows

import com.r3.corda.lib.tokens.contracts.states.FungibleToken
import com.r3.corda.lib.tokens.contracts.types.TokenType
import com.r3.corda.lib.tokens.contracts.utilities.issuedBy
import com.r3.corda.lib.tokens.contracts.utilities.sumTokenStateAndRefs
import com.r3.corda.lib.tokens.money.GBP
import com.r3.corda.lib.tokens.money.USD
import com.r3.corda.lib.tokens.testing.states.Appartment
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

    private val fooToken = Appartment()

    @Before
    override fun initialiseNodes() {
        A = nodes[0]
        B = nodes[1]
        I = nodes[2]
    }

    @Test
    fun `redeem fungible happy path`() {
        I.issueFungibleTokens(A, 100.GBP).getOrThrow()
        network.waitQuiescent()
        A.redeemTokens(GBP, I, 100.GBP, true).getOrThrow()
        assertThat(A.services.vaultService.tokenAmountsByToken(GBP).states).isEmpty()
        assertThat(I.services.vaultService.tokenAmountsByToken(GBP).states).isEmpty()
    }

    @Test
    fun `redeem fungible with change`() {
        I.issueFungibleTokens(A, 100.GBP).getOrThrow()
        network.waitQuiescent()
        A.redeemTokens(GBP, I, 80.GBP, true).getOrThrow()
        val ownedStates = A.services.vaultService.tokenAmountsByToken(GBP).states
        assertThat(ownedStates).isNotEmpty()
        assertThat(ownedStates.sumTokenStateAndRefs()).isEqualTo(20.GBP issuedBy I.legalIdentity())
        assertThat(I.services.vaultService.queryBy<FungibleToken<TokenType>>(ownedTokenAmountCriteria(GBP, I.legalIdentity())).states).isEmpty()
    }

    @Test
    fun `isufficient balance`() {
        I.issueFungibleTokens(A, 100.GBP).getOrThrow()
        network.waitQuiescent()
        Assertions.assertThatThrownBy {
            A.redeemTokens(GBP, I, 200.GBP, true).getOrThrow()
        }.hasMessageContaining("Insufficient spendable states identified for")
    }

    @Test
    fun `different issuers for fungible tokens`() {
        I.issueFungibleTokens(A, 100.GBP).getOrThrow()
        network.waitQuiescent()
        B.issueFungibleTokens(A, 100.USD).getOrThrow()
        network.waitQuiescent()
        Assertions.assertThatThrownBy {
            A.redeemTokens(USD, I, 100.USD, true).getOrThrow()
        }.hasMessageContaining("Insufficient spendable states identified for ${100.USD}")
    }

    @Test
    fun `redeem non-fungible happy path`() {
        I.issueNonFungibleTokens(fooToken, A).getOrThrow()
        network.waitQuiescent()
        assertThat(A.services.vaultService.ownedTokensByToken(fooToken).states).isNotEmpty()
        A.redeemTokens(fooToken, I, null, true).getOrThrow()
        assertThat(A.services.vaultService.ownedTokensByToken(fooToken).states).isEmpty()
        assertThat(I.services.vaultService.ownedTokensByToken(fooToken).states).isEmpty()
    }

    @Test
    fun `redeem tokens from different issuer - non fungible`() {
        I.issueNonFungibleTokens(fooToken, A).getOrThrow()
        network.waitQuiescent()
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
        I.issueFungibleTokens(A, 100.GBP).getOrThrow()
        network.waitQuiescent()
        // Check to see that A was added to I's distribution list.
        A.moveFungibleTokens(50.GBP, B, anonymous = true).getOrThrow()
        network.waitQuiescent()
        val states = B.services.vaultService.tokenAmountsByToken(GBP).states
        assertThat(states.sumTokenStateAndRefs()).isEqualTo(50.GBP issuedBy I.legalIdentity())
        B.redeemTokens(GBP, I, 30.GBP, anonymous = true).getOrThrow()
        network.waitQuiescent()
        val statesAfterRedeem = B.services.vaultService.tokenAmountsByToken(GBP).states
        assertThat(statesAfterRedeem.sumTokenStateAndRefs()).isEqualTo(20.GBP issuedBy I.legalIdentity())
    }
}