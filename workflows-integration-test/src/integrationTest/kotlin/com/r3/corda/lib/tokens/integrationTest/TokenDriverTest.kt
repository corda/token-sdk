package com.r3.corda.lib.tokens.integrationTest

import com.r3.corda.lib.ci.workflows.RequestKey
import com.r3.corda.lib.tokens.contracts.states.FungibleToken
import com.r3.corda.lib.tokens.contracts.states.NonFungibleToken
import com.r3.corda.lib.tokens.contracts.types.IssuedTokenType
import com.r3.corda.lib.tokens.contracts.types.TokenType
import com.r3.corda.lib.tokens.contracts.utilities.*
import com.r3.corda.lib.tokens.integration.workflows.*
import com.r3.corda.lib.tokens.money.GBP
import com.r3.corda.lib.tokens.money.USD
import com.r3.corda.lib.tokens.selection.InsufficientNotLockedBalanceException
import com.r3.corda.lib.tokens.testing.states.House
import com.r3.corda.lib.tokens.testing.states.Ruble
import com.r3.corda.lib.tokens.workflows.flows.rpc.ConfidentialIssueTokens
import com.r3.corda.lib.tokens.workflows.flows.rpc.CreateEvolvableTokens
import com.r3.corda.lib.tokens.workflows.flows.rpc.IssueTokens
import com.r3.corda.lib.tokens.workflows.flows.rpc.UpdateEvolvableToken
import com.r3.corda.lib.tokens.workflows.utilities.heldBy
import com.r3.corda.lib.tokens.workflows.utilities.heldTokenAmountCriteria
import com.r3.corda.lib.tokens.workflows.utilities.heldTokenCriteria
import com.r3.corda.lib.tokens.workflows.utilities.tokenAmountCriteria
import net.corda.core.CordaRuntimeException
import net.corda.core.contracts.Amount
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.TransactionVerificationException
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.crypto.SecureHash
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.internal.concurrent.transpose
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.messaging.startFlow
import net.corda.core.messaging.vaultQueryBy
import net.corda.core.transactions.LedgerTransaction
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.millis
import net.corda.core.utilities.seconds
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.core.*
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.OutOfProcess
import net.corda.testing.driver.driver
import net.corda.testing.driver.internal.incrementalPortAllocation
import net.corda.testing.node.TestCordapp
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatExceptionOfType
import org.hamcrest.CoreMatchers.`is`
import org.hamcrest.CoreMatchers.equalTo
import org.junit.Assert
import org.junit.Rule
import org.junit.Test
import rx.Observer
import java.time.Duration
import java.util.concurrent.TimeoutException
import kotlin.test.assertFailsWith

class TokenDriverTest {
    @Rule
    @JvmField
    val testSerialization = SerializationEnvironmentRule()

    @Test
    fun `should allow issuance of inline defined token`() {
        driver(DriverParameters(
                portAllocation = incrementalPortAllocation(),
                startNodesInProcess = false,
                cordappsForAllNodes = listOf(
                        TestCordapp.findCordapp("com.r3.corda.lib.tokens.testing"),
                        TestCordapp.findCordapp("com.r3.corda.lib.tokens.integration.workflows"),
                        TestCordapp.findCordapp("com.r3.corda.lib.tokens.contracts"),
                        TestCordapp.findCordapp("com.r3.corda.lib.tokens.workflows"),
                        TestCordapp.findCordapp("com.r3.corda.lib.ci")
                ),
                networkParameters = testNetworkParameters(minimumPlatformVersion = 6, notaries = emptyList())
        )) {
            val (issuer, otherNode) = listOf(startNode(providedName = BOC_NAME),
                    startNode(providedName = DUMMY_BANK_A_NAME)).map { it.getOrThrow() }

            val issuerParty = issuer.nodeInfo.singleIdentity()
            val otherParty = otherNode.nodeInfo.singleIdentity()

            val customToken = TokenType("CUSTOM_TOKEN", 3)
            val issuedType = IssuedTokenType(issuerParty, customToken)
            val amountToIssue = Amount(100, issuedType)
            val tokenToIssueToIssuer = FungibleToken(amountToIssue, issuerParty)
            val tokenToIssueToOther = FungibleToken(amountToIssue, otherParty)

            issuer.rpc.startFlowDynamic(IssueTokens::class.java, listOf(tokenToIssueToIssuer, tokenToIssueToOther), emptyList<Party>()).returnValue.getOrThrow()
            val queryResult = issuer.rpc.vaultQueryByCriteria(heldTokenAmountCriteria(customToken, issuerParty), FungibleToken::class.java)

            Assert.assertThat(queryResult.states.size, `is`(1))
            Assert.assertThat(queryResult.states.first().state.data.holder, `is`(equalTo((issuerParty as AbstractParty))))

        }
    }

    @Test
    fun `should allow retrieval of tokens by owning key`() {

        driver(DriverParameters(
                portAllocation = incrementalPortAllocation(),
                startNodesInProcess = false,
                cordappsForAllNodes = listOf(
                        TestCordapp.findCordapp("com.r3.corda.lib.tokens.contracts"),
                    TestCordapp.findCordapp("com.r3.corda.lib.tokens.integration.workflows"),
                    TestCordapp.findCordapp("com.r3.corda.lib.tokens.workflows"),
                        TestCordapp.findCordapp("com.r3.corda.lib.ci")
                ),
                networkParameters = testNetworkParameters(minimumPlatformVersion = 6, notaries = emptyList())
        )) {
            val (issuer) = listOf(startNode(providedName = BOC_NAME)).map { it.getOrThrow() }

            val issuerParty = issuer.nodeInfo.singleIdentity()

            val customToken = TokenType("CUSTOM_TOKEN", 3)
            val issuedType = IssuedTokenType(issuerParty, customToken)
            val newCi1 = issuer.rpc.startFlowDynamic(RequestKey::class.java, issuerParty).returnValue.getOrThrow()
            val newCi2 = issuer.rpc.startFlowDynamic(RequestKey::class.java, issuerParty).returnValue.getOrThrow()
            val amountToIssue = Amount(100, issuedType)
            val tokenToIssueToIssuer = FungibleToken(amountToIssue, issuerParty)
            val tokenToIssueToIssuerCi1 = FungibleToken(amountToIssue, newCi1)
            val tokenToIssueToIssuerCi2 = FungibleToken(amountToIssue, newCi2)

            issuer.rpc.startFlowDynamic(IssueTokens::class.java, listOf(tokenToIssueToIssuer, tokenToIssueToIssuerCi1, tokenToIssueToIssuerCi2), emptyList<Party>()).returnValue.getOrThrow()

            val queryResult = issuer.rpc.vaultQueryByCriteria(heldTokenAmountCriteria(customToken, newCi1), FungibleToken::class.java)

            Assert.assertThat(queryResult.states.size, `is`(1))
            Assert.assertThat(queryResult.states.first().state.data.holder, `is`(equalTo((newCi1 as AbstractParty))))

        }
    }

    @Test(expected = TransactionVerificationException.ContractRejection::class)
    fun `should prevent issuance of a token with a null jarHash that does not use an inline tokenType`() {
        driver(DriverParameters(
                portAllocation = incrementalPortAllocation(),
                startNodesInProcess = false,
                cordappsForAllNodes = listOf(
                        TestCordapp.findCordapp("com.r3.corda.lib.tokens.contracts"),
                    TestCordapp.findCordapp("com.r3.corda.lib.tokens.integration.workflows"),
                    TestCordapp.findCordapp("com.r3.corda.lib.tokens.workflows"),
                        TestCordapp.findCordapp("com.r3.corda.lib.tokens.testing"),
                        TestCordapp.findCordapp("com.r3.corda.lib.ci")
                ),
                networkParameters = testNetworkParameters(minimumPlatformVersion = 6, notaries = emptyList())
        )) {
            val issuer = startNode(providedName = BOC_NAME).getOrThrow()

            val issuerParty = issuer.nodeInfo.singleIdentity()
            val issuedTokenType = IssuedTokenType(issuerParty, Ruble())
            val amountToIssue = Amount(100, issuedTokenType)
            val tokenToIssue = FungibleToken(amountToIssue, issuerParty, null)

            issuer.rpc.startFlowDynamic(IssueTokens::class.java, listOf(tokenToIssue), emptyList<Party>()).returnValue.getOrThrow()
        }
    }

    @Test(timeout = 500_000)
    fun `beefy tokens integration test`() {
        driver(DriverParameters(
                portAllocation = incrementalPortAllocation(),
                startNodesInProcess = false,
                cordappsForAllNodes = listOf(
                        TestCordapp.findCordapp("com.r3.corda.lib.tokens.contracts"),
                    TestCordapp.findCordapp("com.r3.corda.lib.tokens.integration.workflows"),
                    TestCordapp.findCordapp("com.r3.corda.lib.tokens.workflows"),
                        TestCordapp.findCordapp("com.r3.corda.lib.tokens.testing"),
                        TestCordapp.findCordapp("com.r3.corda.lib.ci")
                ),
                // TODO this should be default to 4 in main corda no?
                networkParameters = testNetworkParameters(minimumPlatformVersion = 6, notaries = emptyList())
        )) {
            val (issuer, nodeA, nodeB) = listOf(
                    startNode(providedName = BOC_NAME),
                    startNode(providedName = DUMMY_BANK_A_NAME),
                    startNode(providedName = DUMMY_BANK_B_NAME)
            ).transpose().getOrThrow()
            val issuerParty = issuer.nodeInfo.singleIdentity()
            val nodeAParty = nodeA.nodeInfo.singleIdentity()
            val nodeBParty = nodeB.nodeInfo.singleIdentity()
            val nodeATxnObserver = TransactionObserver(nodeA.rpc, Duration.ofSeconds(30))
            val nodeBTxnObserver = TransactionObserver(nodeB.rpc, Duration.ofSeconds(30))
            // Create evolvable house state.
            val house = House("24 Leinster Gardens, Bayswater, London", 900_000.GBP, listOf(issuerParty), linearId = UniqueIdentifier())
            val housePublishTx = issuer.rpc.startFlowDynamic(
                    CreateEvolvableTokens::class.java,
                    house withNotary defaultNotaryIdentity,
                    emptyList<Party>()
            ).returnValue.getOrThrow() // TODO test choosing getPreferredNotary
            // Issue non-fungible evolvable state to node A using confidential identities.
            val housePtr = house.toPointer<House>()
            issuer.rpc.startFlowDynamic(
                    ConfidentialIssueTokens::class.java,
                    listOf(housePtr issuedBy issuerParty heldBy nodeAParty),
                    emptyList<Party>() // TODO this should be handled by JvmOverloads on the constructor, but for some reason corda doesn't see the second constructor
            ).returnValue.getOrThrow()
            // Issue some fungible GBP cash to NodeA, NodeB.
            val moneyA = 1000000 of GBP issuedBy issuerParty heldBy nodeAParty
            val moneyB = 900000 of GBP issuedBy issuerParty heldBy nodeBParty
            val issueA = issuer.rpc.startFlowDynamic(IssueTokens::class.java, listOf(moneyA), emptyList<Party>()).returnValue.getOrThrow()
            val issueB = issuer.rpc.startFlowDynamic(IssueTokens::class.java, listOf(moneyB), emptyList<Party>()).returnValue.getOrThrow()
            nodeATxnObserver.waitForTransaction(issueA)
            nodeBTxnObserver.waitForTransaction(issueB)
            // Check that node A has cash and house.
            assertThat(nodeA.rpc.vaultQuery(House::class.java).states).isNotEmpty
            assertThat(nodeA.rpc.vaultQueryBy<FungibleToken>(tokenAmountCriteria(GBP)).states.sumTokenStateAndRefs())
                    .isEqualTo(1_000_000L.GBP issuedBy issuerParty)
            assertThat(nodeB.rpc.vaultQueryBy<FungibleToken>(tokenAmountCriteria(GBP)).states.sumTokenStateAndRefs())
                    .isEqualTo(900_000L.GBP issuedBy issuerParty)
            // Move house to Node B using conf identities in exchange for cash using DvP flow.
            // TODO use conf identities
            val dvpTx = nodeA.rpc.startFlow(
                    ::DvPFlow,
                    house,
                    nodeBParty
            ).returnValue.getOrThrow()
            // Wait for both nodes to record the transaction.
            nodeATxnObserver.waitForTransaction(dvpTx)
            nodeBTxnObserver.waitForTransaction(dvpTx)
            // NodeB has house, NodeA doesn't.
            assertThat(nodeB.rpc.vaultQueryBy<NonFungibleToken>(heldTokenCriteria(housePtr)).states).isNotEmpty
            assertThat(nodeA.rpc.vaultQueryBy<NonFungibleToken>(heldTokenCriteria(housePtr)).states).isEmpty()
            // NodeA has cash, B doesn't
            assertThat(nodeA.rpc.vaultQueryBy<FungibleToken>(tokenAmountCriteria(GBP)).states.sumTokenStateAndRefs()).isEqualTo(1_900_000L.GBP issuedBy issuerParty)
            assertThat(nodeB.rpc.vaultQueryBy<FungibleToken>(tokenAmountCriteria(GBP)).states.sumTokenStateAndRefsOrZero(GBP issuedBy issuerParty)).isEqualTo(Amount.zero(GBP issuedBy issuerParty))
            // Check that dist list was updated at issuer node.
            val distributionList = issuer.rpc.startFlow(::GetDistributionList, housePtr).returnValue.getOrThrow()
            assertThat(distributionList.map { it.party }).containsExactly(nodeAParty, nodeBParty)
            // Update that evolvable state on issuer node.
            val oldHouse = housePublishTx.singleOutput<House>()
            val newHouse = oldHouse.state.data.copy(valuation = 800_000L.GBP)
            val houseUpdateTx = issuer.rpc.startFlowDynamic(UpdateEvolvableToken::class.java, oldHouse, newHouse, emptyList<Party>()).returnValue.getOrThrow()
            // Check that both nodeA and B got update.
            nodeATxnObserver.waitForTransaction(houseUpdateTx)
            nodeBTxnObserver.waitForTransaction(houseUpdateTx)
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
            }.withMessageContaining("Exactly one held token of a particular type")
            // NodeB redeems house with the issuer (notice that issuer doesn't know about NodeB confidential identity used for the move with NodeA).
            val redeemHouseTx = nodeB.rpc.startFlowDynamic(
                    RedeemNonFungibleHouse::class.java,
                    housePtr,
                    issuerParty
            ).returnValue.getOrThrow()
            nodeBTxnObserver.waitForTransaction(redeemHouseTx)
            assertThat(nodeB.rpc.vaultQueryBy<NonFungibleToken>(heldTokenCriteria(housePtr)).states).isEmpty()
            // NodeA redeems 1_100_000L.GBP with the issuer, check it received 800_000L change, check, that issuer didn't record cash.
            val redeemGBPTx = nodeA.rpc.startFlowDynamic(
                    RedeemFungibleGBP::class.java,
                    1_100_000L.GBP,
                    issuerParty
            ).returnValue.getOrThrow()
            nodeATxnObserver.waitForTransaction(redeemGBPTx)
            assertThat(nodeA.rpc.vaultQueryBy<FungibleToken>(tokenAmountCriteria(GBP)).states.sumTokenStateAndRefs()).isEqualTo(800_000L.GBP issuedBy issuerParty)
            assertThat(issuer.rpc.vaultQueryBy<FungibleToken>(tokenAmountCriteria(GBP)).states.sumTokenStateAndRefsOrZero(GBP issuedBy issuerParty)).isEqualTo(Amount.zero(GBP issuedBy issuerParty))
            nodeATxnObserver.stopObserving()
            nodeBTxnObserver.stopObserving()
        }
    }

    @Test
    fun `tokens locked in memory are still locked after restart`() {
        driver(DriverParameters(
                inMemoryDB = false,
                startNodesInProcess = false,
                cordappsForAllNodes = listOf(
                        TestCordapp.findCordapp("com.r3.corda.lib.tokens.contracts"),
                        TestCordapp.findCordapp("com.r3.corda.lib.tokens.integration.workflows"),
                        TestCordapp.findCordapp("com.r3.corda.lib.tokens.workflows"),
                        TestCordapp.findCordapp("com.r3.corda.lib.tokens.testing"),
                        TestCordapp.findCordapp("com.r3.corda.lib.ci")
                ),
                networkParameters = testNetworkParameters(minimumPlatformVersion = 6, notaries = emptyList()))
        ) {
            val node = startNode(providedName = DUMMY_BANK_A_NAME, customOverrides = mapOf("p2pAddress" to "localhost:30000")).getOrThrow()
            val nodeParty = node.nodeInfo.singleIdentity()
            // Issue 50.GBP to self
            node.rpc.startFlowDynamic(
                    IssueTokens::class.java,
                    listOf(50.GBP issuedBy nodeParty heldBy nodeParty),
                    emptyList<Party>()
            ).returnValue.getOrThrow()
            // Issue 50.USD to self
            node.rpc.startFlowDynamic(
                    IssueTokens::class.java,
                    listOf(50.USD issuedBy nodeParty heldBy nodeParty),
                    emptyList<Party>()
            ).returnValue.getOrThrow()
            // Run select and lock tokens flow with 5 seconds sleep in it.
            node.rpc.startFlowDynamic(
                    SelectAndLockFlow::class.java,
                    50.GBP,
                    5.seconds
            )
            // Stop node
            (node as OutOfProcess).process.destroyForcibly()
            node.stop()

            // Restart the node
            val restartedNode = startNode(providedName = DUMMY_BANK_A_NAME, customOverrides = mapOf("p2pAddress" to "localhost:30000")).getOrThrow()
            // Try to spend same states, they should be locked after restart, so we expect insufficient not locked balance exception to be thrown.
            assertFailsWith<InsufficientNotLockedBalanceException> {
                restartedNode.rpc.startFlowDynamic(
                    SelectAndLockFlow::class.java,
                    50.GBP,
                    10.millis
                ).returnValue.getOrThrow()
            }
            // This should just work.
            restartedNode.rpc.startFlowDynamic(
                    SelectAndLockFlow::class.java,
                    50.USD,
                    10.millis
            ).returnValue.getOrThrow()
        }
    }

    @Test
    fun `tokens are loaded back in memory after restart`() {
        driver(DriverParameters(
                inMemoryDB = false,
                startNodesInProcess = false,
                cordappsForAllNodes = listOf(
                        TestCordapp.findCordapp("com.r3.corda.lib.tokens.contracts"),
                        TestCordapp.findCordapp("com.r3.corda.lib.tokens.workflows"),
                    TestCordapp.findCordapp("com.r3.corda.lib.tokens.integration.workflows"),
                    TestCordapp.findCordapp("com.r3.corda.lib.tokens.testing"),
                        TestCordapp.findCordapp("com.r3.corda.lib.ci")
                ),
                networkParameters = testNetworkParameters(minimumPlatformVersion = 6, notaries = emptyList()))
        ) {
            val node = startNode(providedName = DUMMY_BANK_A_NAME, customOverrides = mapOf("p2pAddress" to "localhost:30000")).getOrThrow()
            val nodeParty = node.nodeInfo.singleIdentity()
            // Issue 50.GBP to self
            val gbpTx = node.rpc.startFlowDynamic(
                    IssueTokens::class.java,
                    listOf(50.GBP issuedBy nodeParty heldBy nodeParty),
                    emptyList<Party>()
            ).returnValue.getOrThrow()
            // Issue 50.USD to self
            val usdTx = node.rpc.startFlowDynamic(
                    IssueTokens::class.java,
                    listOf(50.USD issuedBy nodeParty heldBy nodeParty),
                    emptyList<Party>()
            ).returnValue.getOrThrow()

            // Stop node
            (node as OutOfProcess).process.destroyForcibly()
            node.stop()

            // Restart the node
            val restartedNode = startNode(providedName = DUMMY_BANK_A_NAME, customOverrides = mapOf("p2pAddress" to "localhost:30000")).getOrThrow()

            // Select both states using LocalTokenSelector
            val gbpToken = restartedNode.rpc.startFlowDynamic(
                    JustLocalSelect::class.java,
                    30.GBP
            ).returnValue.getOrThrow().single()
            assertThat(gbpToken).isEqualTo(gbpTx.singleOutput<FungibleToken>())

            val usdToken = restartedNode.rpc.startFlowDynamic(
                    JustLocalSelect::class.java,
                    25.USD
            ).returnValue.getOrThrow().single()
            assertThat(usdToken).isEqualTo(usdTx.singleOutput<FungibleToken>())
        }
    }

    @Test
    fun `Issue 13000 and validate 13000 are read in again`() {
        driver(DriverParameters(
                inMemoryDB = false,
                startNodesInProcess = false,
                cordappsForAllNodes = listOf(
                        TestCordapp.findCordapp("com.r3.corda.lib.tokens.contracts"),
                        TestCordapp.findCordapp("com.r3.corda.lib.tokens.workflows"),
                    TestCordapp.findCordapp("com.r3.corda.lib.tokens.integration.workflows"),
                    TestCordapp.findCordapp("com.r3.corda.lib.tokens.testing"),
                        TestCordapp.findCordapp("com.r3.corda.lib.ci")
                ),
                networkParameters = testNetworkParameters(minimumPlatformVersion = 6, notaries = emptyList()))
        ) {
            val node = startNode(providedName = DUMMY_BANK_A_NAME, customOverrides = mapOf("p2pAddress" to "localhost:30000")).getOrThrow()
            val nodeParty = node.nodeInfo.singleIdentity()
            for (i in 1..13000) {
                    node.rpc.startFlowDynamic(
                    IssueTokens::class.java,
                    listOf(1.USD issuedBy nodeParty heldBy nodeParty),
                    emptyList<Party>()
                ).returnValue.getOrThrow()
            }
            node.stop()
            // Restart the node
            val restartedNode = startNode(providedName = DUMMY_BANK_A_NAME, customOverrides = mapOf("p2pAddress" to "localhost:30000")).getOrThrow()
            val usdTokens = restartedNode.rpc.startFlowDynamic(
                    JustLocalSelect::class.java,
                    20.USD
                ).returnValue.getOrThrow()
            assertThat(usdTokens.size).isEqualTo(20)
        }
    }

    @Test
    fun `Issue 13000, redeem 250 tokens then select 400 tokens`() {
        driver(DriverParameters(
            inMemoryDB = false,
            startNodesInProcess = false,
            cordappsForAllNodes = listOf(
                TestCordapp.findCordapp("com.r3.corda.lib.tokens.contracts"),
                TestCordapp.findCordapp("com.r3.corda.lib.tokens.workflows"),
                TestCordapp.findCordapp("com.r3.corda.lib.tokens.integration.workflows"),
                TestCordapp.findCordapp("com.r3.corda.lib.tokens.testing"),
                TestCordapp.findCordapp("com.r3.corda.lib.ci")
            ),
            networkParameters = testNetworkParameters(minimumPlatformVersion = 6, notaries = emptyList()))
        ) {
            val node = startNode(providedName = DUMMY_BANK_A_NAME, customOverrides = mapOf("p2pAddress" to "localhost:30000")).getOrThrow()
            val nodeParty = node.nodeInfo.singleIdentity()
            for (i in 1..13000) {
                //val usdTx =
                node.rpc.startFlowDynamic(
                    IssueTokens::class.java,
                    listOf(1.USD issuedBy nodeParty heldBy nodeParty),
                    emptyList<Party>()
                ).returnValue.getOrThrow()
            }
            node.stop()

            // Restart the node
            val restartedNode = startNode(providedName = DUMMY_BANK_A_NAME, customOverrides = mapOf("p2pAddress" to "localhost:30000")).getOrThrow()
            repeat(5) {
                val redeemGBPTx = restartedNode.rpc.startFlowDynamic(
                    RedeemFungibleGBP::class.java,
                    50.USD,
                    nodeParty
                ).returnValue.getOrThrow()
            }

            val usdTokens = restartedNode.rpc.startFlowDynamic(
                JustLocalSelect::class.java,
                400.USD
            ).returnValue.getOrThrow()
            assertThat(usdTokens.size).isEqualTo(400)
        }
    }

    @Test
    fun `Issue 13000, then issue 250 more on restart then select 400 tokens`() {
        driver(DriverParameters(
            inMemoryDB = false,
            startNodesInProcess = false,
            cordappsForAllNodes = listOf(
                TestCordapp.findCordapp("com.r3.corda.lib.tokens.contracts"),
                TestCordapp.findCordapp("com.r3.corda.lib.tokens.workflows"),
                TestCordapp.findCordapp("com.r3.corda.lib.tokens.integration.workflows"),
                TestCordapp.findCordapp("com.r3.corda.lib.tokens.testing"),
                TestCordapp.findCordapp("com.r3.corda.lib.ci")
            ),
            networkParameters = testNetworkParameters(minimumPlatformVersion = 6, notaries = emptyList()))
        ) {
            val node = startNode(providedName = DUMMY_BANK_A_NAME, customOverrides = mapOf("p2pAddress" to "localhost:30000")).getOrThrow()
            val nodeParty = node.nodeInfo.singleIdentity()
            for (i in 1..13000) {
                //val usdTx =
                node.rpc.startFlowDynamic(
                    IssueTokens::class.java,
                    listOf(1.USD issuedBy nodeParty heldBy nodeParty),
                    emptyList<Party>()
                ).returnValue.getOrThrow()
            }
            node.stop()

            // Restart the node
            val restartedNode = startNode(providedName = DUMMY_BANK_A_NAME, customOverrides = mapOf("p2pAddress" to "localhost:30000")).getOrThrow()
            repeat(250) {
                restartedNode.rpc.startFlowDynamic(
                    IssueTokens::class.java,
                    listOf(1.USD issuedBy nodeParty heldBy nodeParty),
                    emptyList<Party>()
                ).returnValue.getOrThrow()
            }
            val usdTokens = restartedNode.rpc.startFlowDynamic(
                JustLocalSelect::class.java,
                400.USD
            ).returnValue.getOrThrow()
            assertThat(usdTokens.size).isEqualTo(400)
        }
    }

    @Test
    fun `Issue 4000 tokens, redeem 4000 tokens`() {
        driver(DriverParameters(
            inMemoryDB = false,
            startNodesInProcess = false,
            cordappsForAllNodes = listOf(
                TestCordapp.findCordapp("com.r3.corda.lib.tokens.contracts"),
                TestCordapp.findCordapp("com.r3.corda.lib.tokens.workflows"),
                TestCordapp.findCordapp("com.r3.corda.lib.tokens.integration.workflows"),
                TestCordapp.findCordapp("com.r3.corda.lib.tokens.testing"),
                TestCordapp.findCordapp("com.r3.corda.lib.ci")
            ),
            networkParameters = testNetworkParameters(minimumPlatformVersion = 6, notaries = emptyList()))
        ) {
            val node = startNode(providedName = DUMMY_BANK_A_NAME, customOverrides = mapOf("p2pAddress" to "localhost:30000")).getOrThrow()
            val nodeParty = node.nodeInfo.singleIdentity()
            for (i in 1..4000) {
                //val usdTx =
                node.rpc.startFlowDynamic(
                    IssueTokens::class.java,
                    listOf(1.USD issuedBy nodeParty heldBy nodeParty),
                    emptyList<Party>()
                ).returnValue.getOrThrow()
            }
            node.stop()

            // Restart the node
            val restartedNode = startNode(providedName = DUMMY_BANK_A_NAME, customOverrides = mapOf("p2pAddress" to "localhost:30000")).getOrThrow()
            repeat(40) {
                val redeemTx = restartedNode.rpc.startFlowDynamic(
                    RedeemFungibleGBP::class.java,
                    100.USD,
                    nodeParty
                ).returnValue.getOrThrow()
            }
        }
    }
}

class TransactionObserver(private val rpcOps: CordaRPCOps, private val duration: Duration) {
    val txns = mutableSetOf<SecureHash>()
    val feed = rpcOps.internalVerifiedTransactionsFeed()
    val subscription = feed.updates.subscribe(
            object : Observer<SignedTransaction> {
                override fun onNext(value: SignedTransaction) {
                    txns.add(value.id)
                }

                override fun onCompleted() {
                }

                override fun onError(e: Throwable?) {
                    throw e!!
                }
            })

    fun stopObserving() {
        subscription.unsubscribe()
    }

    fun waitForTransaction(txn: SignedTransaction) {
        val startTime = System.nanoTime()
        while (System.nanoTime() - startTime < duration.toNanos()) {
            if (txns.contains(txn.id)) {
                return
            }
            Thread.sleep(10)
        }
        throw TimeoutException("Timed out waiting for transaction with id: $txn.id")
    }
}

/** Get single input/output from ledger transaction. */
inline fun <reified T : ContractState> LedgerTransaction.singleInput() = inputsOfType<T>().single()

inline fun <reified T : ContractState> LedgerTransaction.singleOutput() = outputsOfType<T>().single()

inline fun <reified T : ContractState> SignedTransaction.singleOutput() = tx.outRefsOfType<T>().single()