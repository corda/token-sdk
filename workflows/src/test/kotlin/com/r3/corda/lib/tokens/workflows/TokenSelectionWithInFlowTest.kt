package com.r3.corda.lib.tokens.workflows

import com.r3.corda.lib.tokens.contracts.states.FungibleToken
import com.r3.corda.lib.tokens.contracts.utilities.heldBy
import com.r3.corda.lib.tokens.contracts.utilities.issuedBy
import com.r3.corda.lib.tokens.contracts.utilities.of
import com.r3.corda.lib.tokens.money.BTC
import com.r3.corda.lib.tokens.workflows.flows.rpc.IssueTokens
import net.corda.core.contracts.Amount
import net.corda.core.utilities.getOrThrow
import net.corda.testing.core.*
import net.corda.testing.node.internal.InternalMockNetwork
import net.corda.testing.node.internal.InternalMockNodeParameters
import net.corda.testing.node.internal.startFlow
import org.hamcrest.CoreMatchers.*
import org.junit.*

class TokenSelectionWithInFlowTest {

    @Test
    fun `should allow selection of tokens already issued from within a flow`() {

        val mockNet = InternalMockNetwork(
            cordappPackages = listOf(
                "com.r3.corda.lib.tokens.money",
                "com.r3.corda.lib.tokens.selection",
                "com.r3.corda.lib.tokens.contracts",
                "com.r3.corda.lib.tokens.workflows"
            )
        )

        try {
            val aliceNode = mockNet.createNode(InternalMockNodeParameters(legalName = ALICE_NAME))
            val issuerNode = mockNet.createNode(InternalMockNodeParameters(legalName = CHARLIE_NAME))
            val alice = aliceNode.info.singleIdentity()
            val issuer = issuerNode.info.singleIdentity()

            val btc = 100000 of BTC issuedBy issuer heldBy alice
            val resultFuture = issuerNode.services.startFlow(IssueTokens(listOf(btc))).resultFuture
            mockNet.runNetwork()
            val issueResultTx = resultFuture.get()
            val issuedStateRef = issueResultTx.coreTransaction.outRefsOfType<FungibleToken>().single()

            val tokensFuture = aliceNode.services.startFlow(SuspendingSelector(alice.owningKey, Amount(1, BTC))).resultFuture
            mockNet.runNetwork()
            val selectedToken = tokensFuture.getOrThrow().single()

            Assert.assertThat(issuedStateRef, `is`(equalTo(selectedToken)))
        } finally {
            mockNet.stopNodes()
        }
    }
}
