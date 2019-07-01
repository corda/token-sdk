package com.r3.corda.lib.tokens.workflows

import com.r3.corda.lib.tokens.contracts.states.FungibleToken
import com.r3.corda.lib.tokens.contracts.states.NonFungibleToken
import com.r3.corda.lib.tokens.contracts.types.TokenPointer
import com.r3.corda.lib.tokens.contracts.utilities.issuedBy
import com.r3.corda.lib.tokens.contracts.utilities.sumTokenStateAndRefs
import com.r3.corda.lib.tokens.contracts.utilities.sumTokenStateAndRefsOrZero
import com.r3.corda.lib.tokens.contracts.utilities.withNotary
import com.r3.corda.lib.tokens.money.FiatCurrency
import com.r3.corda.lib.tokens.money.GBP
import com.r3.corda.lib.tokens.testing.statesAndContracts.House
import com.r3.corda.lib.tokens.workflows.flows.evolvable.CreateEvolvableToken
import com.r3.corda.lib.tokens.workflows.flows.evolvable.UpdateEvolvableToken
import com.r3.corda.lib.tokens.workflows.flows.rpc.ConfidentialIssueTokens
import com.r3.corda.lib.tokens.workflows.flows.rpc.IssueTokens
import com.r3.corda.lib.tokens.workflows.internal.testflows.*
import com.r3.corda.lib.tokens.workflows.utilities.heldBy
import com.r3.corda.lib.tokens.workflows.utilities.ownedTokenCriteria
import com.r3.corda.lib.tokens.workflows.utilities.tokenAmountCriteria
import net.corda.core.CordaRuntimeException
import net.corda.core.contracts.Amount
import net.corda.core.identity.Party
import net.corda.core.internal.concurrent.transpose
import net.corda.core.messaging.startFlow
import net.corda.core.messaging.vaultQueryBy
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.seconds
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.core.BOC_NAME
import net.corda.testing.core.DUMMY_BANK_A_NAME
import net.corda.testing.core.DUMMY_BANK_B_NAME
import net.corda.testing.core.singleIdentity
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.driver
import net.corda.testing.driver.internal.incrementalPortAllocation
import net.corda.testing.node.TestCordapp
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatExceptionOfType
import org.junit.Test

class TokenDriverTest {
    @Test(timeout = 300_000)
    fun `beefy tokens integration test`() {
        driver(DriverParameters(
                portAllocation = incrementalPortAllocation(20000),
                startNodesInProcess = true, //todo false
                cordappsForAllNodes = listOf(
                        TestCordapp.findCordapp("com.r3.corda.lib.tokens.money"),
                        TestCordapp.findCordapp("com.r3.corda.lib.tokens.contracts"),
                        TestCordapp.findCordapp("com.r3.corda.lib.tokens.workflows"),
                        TestCordapp.findCordapp("com.r3.corda.lib.tokens.testing")
                ),
                // TODO this should be default to 4 in main corda no?
                networkParameters = testNetworkParameters(minimumPlatformVersion = 4, notaries = emptyList())
        )) {
            val (issuer, nodeA, nodeB) = listOf(
                    startNode(providedName = BOC_NAME),
                    startNode(providedName = DUMMY_BANK_A_NAME),
                    startNode(providedName = DUMMY_BANK_B_NAME)
            ).transpose().getOrThrow()
            val issuerParty = issuer.nodeInfo.singleIdentity()
            val nodeAParty = nodeA.nodeInfo.singleIdentity()
            val nodeBParty = nodeB.nodeInfo.singleIdentity()
            // Create evolvable house state.
            val house = House("24 Leinster Gardens, Bayswater, London", 900_000.GBP, listOf(issuerParty))
            val housePublishTx = issuer.rpc.startFlowDynamic(
                    CreateEvolvableToken::class.java,
                    house withNotary defaultNotaryIdentity
            ).returnValue.getOrThrow() // TODO test choosing getPreferredNotary
            // Issue non-fungible evolvable state to node A using confidential identities.
            val housePtr = house.toPointer<House>()
            issuer.rpc.startFlowDynamic(
                    ConfidentialIssueTokens::class.java,
                    listOf(housePtr issuedBy issuerParty heldBy nodeAParty),
                    emptyList<Party>() // TODO this should be handled by JvmOverloads on the constructor, but for some reason corda doesn't see the second constructor
            ).returnValue.getOrThrow()
            // Issue some fungible GBP cash to NodeA, NodeB.
            issuer.rpc.run {
                startFlowDynamic(IssueTokens::class.java, 1_000_000.GBP, issuerParty, nodeAParty, emptyList<Party>()).returnValue.getOrThrow()
                startFlowDynamic(IssueTokens::class.java, 900_000.GBP, issuerParty, nodeBParty, emptyList<Party>()).returnValue.getOrThrow()
            }
            // Check that node A has cash and house.
            assertThat(nodeA.rpc.vaultQuery(House::class.java).states).isNotEmpty
            assertThat(nodeA.rpc.vaultQueryBy<FungibleToken<FiatCurrency>>(tokenAmountCriteria(GBP)).states.sumTokenStateAndRefs())
                    .isEqualTo(1_000_000L.GBP issuedBy issuerParty)
            assertThat(nodeB.rpc.vaultQueryBy<FungibleToken<FiatCurrency>>(tokenAmountCriteria(GBP)).states.sumTokenStateAndRefs())
                    .isEqualTo(900_000L.GBP issuedBy issuerParty)
            // Move house to Node B using conf identities in exchange for cash using DvP flow.
            // TODO use conf identities
            val dvpTx = nodeA.rpc.startFlowDynamic(
                    DvPFlow::class.java,
                    house,
                    nodeBParty
            ).returnValue.getOrThrow()

            // Wait for both nodes to record the transaction.
            nodeA.rpc.watchForTransaction(dvpTx).getOrThrow(5.seconds)
            nodeB.rpc.watchForTransaction(dvpTx).getOrThrow(5.seconds)
            // NodeB has house, NodeA doesn't.
            assertThat(nodeB.rpc.vaultQueryBy<NonFungibleToken<TokenPointer<House>>>(ownedTokenCriteria(housePtr)).states).isNotEmpty
            assertThat(nodeA.rpc.vaultQueryBy<NonFungibleToken<TokenPointer<House>>>(ownedTokenCriteria(housePtr)).states).isEmpty()
            // NodeA has cash, B doesn't
            assertThat(nodeA.rpc.vaultQueryBy<FungibleToken<FiatCurrency>>(tokenAmountCriteria(GBP)).states.sumTokenStateAndRefs()).isEqualTo(1_900_000L.GBP issuedBy issuerParty)
            assertThat(nodeB.rpc.vaultQueryBy<FungibleToken<FiatCurrency>>(tokenAmountCriteria(GBP)).states.sumTokenStateAndRefsOrZero(GBP issuedBy issuerParty)).isEqualTo(Amount.zero(GBP issuedBy issuerParty))
            // Check that dist list was updated at issuer node.
            val distributionList = issuer.rpc.startFlow(::GetDistributionList, housePtr).returnValue.getOrThrow()
            assertThat(distributionList.map { it.party }).containsExactly(nodeAParty, nodeBParty)
            // Update that evolvable state on issuer node.
            val oldHouse = housePublishTx.singleOutput<House>()
            val newHouse = oldHouse.state.data.copy(valuation = 800_000L.GBP)
            val houseUpdateTx = issuer.rpc.startFlowDynamic(UpdateEvolvableToken::class.java, oldHouse, newHouse).returnValue.getOrThrow()
            // Check that both nodeA and B got update.
            nodeA.rpc.watchForTransaction(houseUpdateTx).getOrThrow(5.seconds)
            nodeB.rpc.watchForTransaction(houseUpdateTx).getOrThrow(5.seconds)
            val houseA = nodeA.rpc.startFlow(::CheckTokenPointer, housePtr).returnValue.getOrThrow()
            val houseB = nodeB.rpc.startFlow(::CheckTokenPointer, housePtr).returnValue.getOrThrow()
            assertThat(houseA.valuation).isEqualTo(800_000L.GBP)
            assertThat(houseB.valuation).isEqualTo(800_000L.GBP)

            assertThatExceptionOfType(CordaRuntimeException::class.java).isThrownBy {
                nodeA.rpc.startFlowDynamic(
                        RedeemNonFungibleHouse::class.java,
                        housePtr,
                        issuerParty
                ).returnValue.getOrThrow()
            }.withMessageContaining("Exactly one owned token of a particular type")
            // NodeB redeems house with the issuer (notice that issuer doesn't know about NodeB confidential identity used for the move with NodeA).
            val redeemHouseTx = nodeB.rpc.startFlowDynamic(
                    RedeemNonFungibleHouse::class.java,
                    housePtr,
                    issuerParty
            ).returnValue.getOrThrow()
            nodeB.rpc.watchForTransaction(redeemHouseTx).getOrThrow(5.seconds)
            assertThat(nodeB.rpc.vaultQueryBy<NonFungibleToken<TokenPointer<House>>>(ownedTokenCriteria(housePtr)).states).isEmpty()
            // NodeA redeems 1_100_000L.GBP with the issuer, check it received 800_000L change, check, that issuer didn't record cash.
            val redeemGBPTx = nodeA.rpc.startFlowDynamic(
                    RedeemFungibleGBP::class.java,
                    1_100_000L.GBP,
                    issuerParty
            ).returnValue.getOrThrow()
            nodeA.rpc.watchForTransaction(redeemGBPTx).getOrThrow(5.seconds)
            assertThat(nodeA.rpc.vaultQueryBy<FungibleToken<FiatCurrency>>(tokenAmountCriteria(GBP)).states.sumTokenStateAndRefs()).isEqualTo(800_000L.GBP issuedBy issuerParty)
            assertThat(issuer.rpc.vaultQueryBy<FungibleToken<FiatCurrency>>(tokenAmountCriteria(GBP)).states.sumTokenStateAndRefsOrZero(GBP issuedBy issuerParty)).isEqualTo(Amount.zero(GBP issuedBy issuerParty))
        }
    }
}
