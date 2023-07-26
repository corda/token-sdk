package com.r3.corda.lib.tokens.workflows

import com.r3.corda.lib.tokens.contracts.states.FungibleToken
import com.r3.corda.lib.tokens.contracts.utilities.issuedBy
import com.r3.corda.lib.tokens.contracts.utilities.sumTokenStateAndRefs
import com.r3.corda.lib.tokens.money.GBP
import com.r3.corda.lib.tokens.money.USD
import com.r3.corda.lib.tokens.selection.InsufficientBalanceException
import com.r3.corda.lib.tokens.testing.states.Appartment
import com.r3.corda.lib.tokens.workflows.utilities.heldTokenAmountCriteria
import com.r3.corda.lib.tokens.workflows.utilities.heldTokensByToken
import com.r3.corda.lib.tokens.workflows.utilities.tokenAmountsByToken
import net.corda.core.identity.CordaX500Name
import net.corda.core.node.services.queryBy
import net.corda.core.utilities.getOrThrow
import net.corda.testing.node.StartedMockNode
import org.assertj.core.api.Assertions
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatExceptionOfType
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

    @Test(timeout = 300_000)
    fun `redeem fungible happy path`() {
        I.issueFungibleTokens(A, 100.GBP).getOrThrow()
        network.waitQuiescent()
        A.redeemTokens(GBP, I, 100.GBP, true).getOrThrow()
        assertThat(A.services.vaultService.tokenAmountsByToken(GBP).states).isEmpty()
        assertThat(I.services.vaultService.tokenAmountsByToken(GBP).states).isEmpty()
    }

    @Test(timeout = 300_000)
    fun `redeem fungible with change`() {
        I.issueFungibleTokens(A, 100.GBP).getOrThrow()
        network.waitQuiescent()
        A.redeemTokens(GBP, I, 80.GBP, true).getOrThrow()
        val ownedStates = A.services.vaultService.tokenAmountsByToken(GBP).states
        assertThat(ownedStates).isNotEmpty()
        assertThat(ownedStates.sumTokenStateAndRefs()).isEqualTo(20.GBP issuedBy I.legalIdentity())
        assertThat(I.services.vaultService.queryBy<FungibleToken>(heldTokenAmountCriteria(GBP, I.legalIdentity())).states).isEmpty()
    }

    @Test(timeout = 300_000)
    fun `isufficient balance`() {
        I.issueFungibleTokens(A, 100.GBP).getOrThrow()
        network.waitQuiescent()
        assertThatExceptionOfType(InsufficientBalanceException::class.java).isThrownBy {
            A.redeemTokens(GBP, I, 200.GBP, true).getOrThrow()
        }
    }

    @Test(timeout = 300_000)
    fun `different issuers for fungible tokens`() {
        I.issueFungibleTokens(A, 100.GBP).getOrThrow()
        network.waitQuiescent()
        B.issueFungibleTokens(A, 100.USD).getOrThrow()
        network.waitQuiescent()
        assertThatExceptionOfType(InsufficientBalanceException::class.java).isThrownBy {
            A.redeemTokens(USD, I, 100.USD, true).getOrThrow()
        }
    }

    @Test(timeout = 300_000)
    fun `redeem non-fungible happy path`() {
        I.issueNonFungibleTokens(fooToken, A).getOrThrow()
        network.waitQuiescent()
        assertThat(A.services.vaultService.heldTokensByToken(fooToken).states).isNotEmpty()
        A.redeemTokens(fooToken, I, null, true).getOrThrow()
        assertThat(A.services.vaultService.heldTokensByToken(fooToken).states).isEmpty()
        assertThat(I.services.vaultService.heldTokensByToken(fooToken).states).isEmpty()
    }

    @Test(timeout = 300_000)
    fun `redeem tokens from different issuer - non fungible`() {
        I.issueNonFungibleTokens(fooToken, A).getOrThrow()
        network.waitQuiescent()
        assertThat(A.services.vaultService.heldTokensByToken(fooToken).states).isNotEmpty()
        Assertions.assertThatThrownBy {
            A.redeemTokens(fooToken, B, null, true).getOrThrow()
        }.hasMessageContaining("Exactly one held token of a particular type $fooToken should be in the vault at any one time.")
    }

    @Test(timeout = 300_000)
    fun `non fungible two same tokens`() {
        I.issueNonFungibleTokens(fooToken, A).getOrThrow()
        I.issueNonFungibleTokens(fooToken, A).getOrThrow()
        assertThat(A.services.vaultService.heldTokensByToken(fooToken).states).isNotEmpty()
        Assertions.assertThatThrownBy {
            A.redeemTokens(fooToken, B, null, true).getOrThrow()
        }.hasMessageContaining("Exactly one held token of a particular type $fooToken should be in the vault at any one time.")
    }

    @Test(timeout = 300_000)
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

    @Test(timeout = 300_000)
    fun `redeem fungible tokens with observers`() {
        val observer: StartedMockNode = network.createPartyNode(CordaX500Name("Observer", "London", "GB"))
        val obs = listOf(observer.legalIdentity())
        // Confidential issue to nodeA with observer
        val itx = I.issueFungibleTokens(A, 100.GBP, true, obs).getOrThrow()
        network.waitQuiescent()
        assertHasTransaction(itx, network, observer)
        // Confidential move to nodeB with observer
        val mtx = A.moveFungibleTokens(50.GBP, B, true, obs).getOrThrow()
        network.waitQuiescent()
        assertHasTransaction(mtx, network, observer)
        // redeem with issuer with observer
        val rtx = B.redeemTokens(GBP, I, 30.GBP, anonymous = true, observers = obs).getOrThrow()
        network.waitQuiescent()
        assertHasTransaction(rtx, network, observer)
    }

    @Test(timeout = 300_000)
    fun `redeem from two different keys on the same node - correct signatures`() {
        val itx1 = I.issueFungibleTokens(A, 100.GBP, true).getOrThrow()
        val itx2 = I.issueFungibleTokens(A, 13.GBP, true).getOrThrow()
        network.waitQuiescent()
        val key1 = itx1.singleOutput<FungibleToken>().state.data.holder.owningKey
        val key2 = itx2.singleOutput<FungibleToken>().state.data.holder.owningKey
        assertThat(key1).isNotEqualTo(key2)
        val rtx = A.redeemTokens(GBP, I, 113.GBP).getOrThrow()
        assertThat(rtx.sigs.map { it.by }).contains(key1, key2, I.legalIdentity().owningKey)
    }

    @Test(timeout = 300_000)
    fun `issue to self and redeem with self`() {
        I.issueFungibleTokens(I, 100.GBP, true).getOrThrow()
        network.waitQuiescent()
        assertThat(I.services.vaultService.tokenAmountsByToken(GBP).states.firstOrNull()?.state?.data?.amount).isEqualTo(100.GBP issuedBy I.legalIdentity())
        I.redeemTokens(GBP, I, 90.GBP).getOrThrow()
        network.waitQuiescent()
        assertThat(I.services.vaultService.tokenAmountsByToken(GBP).states.firstOrNull()?.state?.data?.amount).isEqualTo(10.GBP issuedBy I.legalIdentity())
    }
}
