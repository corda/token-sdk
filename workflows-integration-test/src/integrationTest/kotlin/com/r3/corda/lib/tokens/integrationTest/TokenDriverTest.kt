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
import net.corda.core.concurrent.CordaFuture
import net.corda.core.contracts.Amount
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.TransactionVerificationException
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.internal.concurrent.doneFuture
import net.corda.core.internal.concurrent.transpose
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.messaging.startFlow
import net.corda.core.messaging.vaultQueryBy
import net.corda.core.toFuture
import net.corda.core.transactions.LedgerTransaction
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.millis
import net.corda.core.utilities.seconds
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.core.BOC_NAME
import net.corda.testing.core.DUMMY_BANK_A_NAME
import net.corda.testing.core.DUMMY_BANK_B_NAME
import net.corda.testing.core.singleIdentity
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
import org.junit.Test
import java.util.concurrent.CountDownLatch
import kotlin.test.assertFailsWith

class TokenDriverTest {

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
                networkParameters = testNetworkParameters(minimumPlatformVersion = 4, notaries = emptyList())
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
                networkParameters = testNetworkParameters(minimumPlatformVersion = 4, notaries = emptyList())
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
                networkParameters = testNetworkParameters(minimumPlatformVersion = 4, notaries = emptyList())
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
            nodeA.rpc.watchForTransaction(issueA).getOrThrow()
            nodeB.rpc.watchForTransaction(issueB).getOrThrow()
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
            nodeA.rpc.watchForTransaction(dvpTx).getOrThrow()
            nodeB.rpc.watchForTransaction(dvpTx).getOrThrow()
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
            nodeA.rpc.watchForTransaction(houseUpdateTx).getOrThrow()
            nodeB.rpc.watchForTransaction(houseUpdateTx).getOrThrow()
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
            nodeB.rpc.watchForTransaction(redeemHouseTx).getOrThrow()
            assertThat(nodeB.rpc.vaultQueryBy<NonFungibleToken>(heldTokenCriteria(housePtr)).states).isEmpty()
            // NodeA redeems 1_100_000L.GBP with the issuer, check it received 800_000L change, check, that issuer didn't record cash.
            val redeemGBPTx = nodeA.rpc.startFlowDynamic(
                    RedeemFungibleGBP::class.java,
                    1_100_000L.GBP,
                    issuerParty
            ).returnValue.getOrThrow()
            nodeA.rpc.watchForTransaction(redeemGBPTx).getOrThrow()
            assertThat(nodeA.rpc.vaultQueryBy<FungibleToken>(tokenAmountCriteria(GBP)).states.sumTokenStateAndRefs()).isEqualTo(800_000L.GBP issuedBy issuerParty)
            assertThat(issuer.rpc.vaultQueryBy<FungibleToken>(tokenAmountCriteria(GBP)).states.sumTokenStateAndRefsOrZero(GBP issuedBy issuerParty)).isEqualTo(Amount.zero(GBP issuedBy issuerParty))
        }
    }

    @Test(timeout = 300_000)
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
                networkParameters = testNetworkParameters(minimumPlatformVersion = 4, notaries = emptyList()))
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
            // Run select and lock tokens flow
            val pt = node.rpc.startTrackedFlowDynamic(
                    SelectAndLockFlow::class.java,
                    50.GBP,
                    50.seconds
            ).returnValue

            Thread.sleep(10_000)

            // Stop node
            (node as OutOfProcess).process.destroyForcibly()
            node.stop()

            // Restart the node
            val restartedNode = startNode(providedName = DUMMY_BANK_A_NAME, customOverrides = mapOf("p2pAddress" to "localhost:30000")).getOrThrow()
            // Try to spend same states, they should be locked after restart, so we expect insufficient not locked balance exception to be thrown.
            Thread.sleep(15000) // because token loading is now async, we must wait a bit of time before we can attempt to select.

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
                networkParameters = testNetworkParameters(minimumPlatformVersion = 4, notaries = emptyList()))
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
}

/**
 * It's internal because it uses deprecated [internalVerifiedTransactionsFeed].
 */
fun CordaRPCOps.watchForTransaction(tx: SignedTransaction): CordaFuture<SignedTransaction> {
    val (snapshot, feed) = internalVerifiedTransactionsFeed()
    return if (tx in snapshot) {
        doneFuture(tx)
    } else {
        feed.filter { it == tx }.toFuture()
    }
}

/** Get single input/output from ledger transaction. */
inline fun <reified T : ContractState> LedgerTransaction.singleInput() = inputsOfType<T>().single()

inline fun <reified T : ContractState> LedgerTransaction.singleOutput() = outputsOfType<T>().single()

inline fun <reified T : ContractState> SignedTransaction.singleOutput() = tx.outRefsOfType<T>().single()