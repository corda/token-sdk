package com.r3.corda.sdk.token.workflow

import com.r3.corda.sdk.token.money.GBP
import com.r3.corda.sdk.token.workflow.statesAndContracts.TradeConditions
import com.r3.corda.sdk.token.workflow.utilities.ownedTokenAmountsByToken
import net.corda.core.node.services.queryBy
import net.corda.core.utilities.getOrThrow
import net.corda.testing.node.StartedMockNode
import org.assertj.core.api.Assertions
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals

class TokenFlowWithTradeConditionsTests : MockNetworkTest(numberOfNodes = 3) {

    lateinit var A: StartedMockNode
    lateinit var B: StartedMockNode
    lateinit var I: StartedMockNode

    @Before
    override fun initialiseNodes() {
        A = nodes[0]
        B = nodes[1]
        I = nodes[2]
    }

    @Test
    fun `issue and move fixed tokens with trade conditions`() {
        val conditions = "The best conditions ever."
        val issueTokenTx = I.issueTokensWithTradeConditions(GBP, A, NOTARY, 100.GBP, conditions).getOrThrow()
        A.watchForTransaction(issueTokenTx.id).getOrThrow()
        val aTradeConditions = A.services.vaultService.queryBy<TradeConditions>().states.single().state.data
        assertEquals(aTradeConditions.owner, A.legalIdentity())
        assertEquals(aTradeConditions.conditions, conditions)

        // Check to see that A was added to I's distribution list.
        val moveTokenTx = A.moveTokensWithTradeConditions(GBP, B, 50.GBP, conditions).getOrThrow()
        B.watchForTransaction(moveTokenTx.id).getOrThrow()
        val bTradeConditions = B.services.vaultService.queryBy<TradeConditions>().states.single().state.data
        assertEquals(bTradeConditions.owner, B.legalIdentity())
        assertEquals(bTradeConditions.conditions, conditions)

        println(moveTokenTx.tx)
    }

    @Test
    fun `redeem fungible with trade conditions happy pat`() {
        val issueConditions = "The best issue conditions ever."
        val redeemConditions = "The best redeem conditions ever."
        val issueTokenTx = I.issueTokensWithTradeConditions(GBP, A, NOTARY, 100.GBP, issueConditions).getOrThrow()
        A.watchForTransaction(issueTokenTx.id).getOrThrow()
        A.redeemTokensWithTradeConditions(GBP, I, 100.GBP, redeemConditions).getOrThrow()
        Assertions.assertThat(A.services.vaultService.ownedTokenAmountsByToken(GBP).states).isEmpty()
        Assertions.assertThat(I.services.vaultService.ownedTokenAmountsByToken(GBP).states).isEmpty()

        assert(I.services.vaultService.queryBy<TradeConditions>().states.isEmpty())


        assertEquals(A.services.vaultService.queryBy<TradeConditions>().states.size, 2)

        val tradeConditions1 = A.services.vaultService.queryBy<TradeConditions>().states.get(0).state.data
        assertEquals(tradeConditions1.owner, A.legalIdentity())
        assertEquals(tradeConditions1.conditions, issueConditions)

        val tradeConditions2 = A.services.vaultService.queryBy<TradeConditions>().states.get(1).state.data
        assertEquals(tradeConditions2.owner, A.legalIdentity())
        assertEquals(tradeConditions2.conditions, redeemConditions)
    }

}