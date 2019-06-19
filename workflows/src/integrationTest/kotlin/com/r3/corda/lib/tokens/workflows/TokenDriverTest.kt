package com.r3.corda.lib.tokens.workflows

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.lib.tokens.contracts.states.FungibleToken
import com.r3.corda.lib.tokens.contracts.states.NonFungibleToken
import com.r3.corda.lib.tokens.contracts.types.TokenPointer
import com.r3.corda.lib.tokens.contracts.utilities.*
import com.r3.corda.lib.tokens.money.FiatCurrency
import com.r3.corda.lib.tokens.money.GBP
import com.r3.corda.lib.tokens.workflows.flows.evolvable.CreateEvolvableToken
import com.r3.corda.lib.tokens.workflows.flows.evolvable.UpdateEvolvableToken
import com.r3.corda.lib.tokens.workflows.flows.finality.ObserverAwareFinalityFlow
import com.r3.corda.lib.tokens.workflows.flows.finality.ObserverAwareFinalityFlowHandler
import com.r3.corda.lib.tokens.workflows.flows.move.addMoveTokens
import com.r3.corda.lib.tokens.workflows.flows.shell.*
import com.r3.corda.lib.tokens.workflows.internal.flows.distribution.UpdateDistributionListFlow
import com.r3.corda.lib.tokens.workflows.internal.flows.distribution.getDistributionList
import com.r3.corda.lib.tokens.workflows.internal.schemas.DistributionRecord
import com.r3.corda.lib.tokens.workflows.internal.selection.TokenSelection
import com.r3.corda.lib.tokens.workflows.statesAndContracts.House
import com.r3.corda.lib.tokens.workflows.types.PartyAndAmount
import com.r3.corda.lib.tokens.workflows.utilities.*
import net.corda.confidential.IdentitySyncFlow
import net.corda.core.CordaRuntimeException
import net.corda.core.contracts.Amount
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.internal.concurrent.transpose
import net.corda.core.messaging.startFlow
import net.corda.core.messaging.vaultQueryBy
import net.corda.core.serialization.CordaSerializable
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.seconds
import net.corda.core.utilities.unwrap
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.core.BOC_NAME
import net.corda.testing.core.DUMMY_BANK_A_NAME
import net.corda.testing.core.DUMMY_BANK_B_NAME
import net.corda.testing.core.singleIdentity
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.driver
import net.corda.testing.driver.internal.incrementalPortAllocation
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatExceptionOfType
import org.junit.Test

class TokenDriverTest {
    //    @Ignore("There is a bug with type parameters in startRPCFlow in Corda 4.0 that will be fixed in... who knows when, probably 5? You can run this test using 5.0-SNAPSHOT version.")
    @Test(timeout = 300_000)
    fun `beefy tokens integration test`() {
        driver(DriverParameters(
                portAllocation = incrementalPortAllocation(20000),
                startNodesInProcess = true, //todo false
                extraCordappPackagesToScan = listOf("com.r3.corda.lib.tokens.contracts", "com.r3.corda.lib.tokens.workflows", "com.r3.corda.lib.tokens.money"),
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
            println("ISSUING CASH")
            issuer.rpc.run {
                startFlowDynamic(IssueTokens::class.java, GBP, 1_000_000L, issuerParty, nodeAParty, emptyList<Party>()).returnValue.getOrThrow()
                startFlowDynamic(IssueTokens::class.java, GBP, 900_000L, issuerParty, nodeBParty, emptyList<Party>()).returnValue.getOrThrow()
            }
            // Check that node A has cash and house.
            assertThat(nodeA.rpc.vaultQuery(House::class.java).states).isNotEmpty
            assertThat(nodeA.rpc.vaultQueryBy<FungibleToken<FiatCurrency>>(tokenAmountCriteria(GBP)).states.sumTokenStateAndRefs())
                    .isEqualTo(1_000_000L.GBP issuedBy issuerParty)
            assertThat(nodeB.rpc.vaultQueryBy<FungibleToken<FiatCurrency>>(tokenAmountCriteria(GBP)).states.sumTokenStateAndRefs())
                    .isEqualTo(900_000L.GBP issuedBy issuerParty)
            // Move house to Node B using conf identities in exchange for cash using DvP flow.
            // TODO use conf identities
            println("STARTING DVP TX")
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
            val distributionList = issuer.rpc.startFlow(TokenDriverTest::GetDistributionList, housePtr).returnValue.getOrThrow()
            assertThat(distributionList.map { it.party }).containsExactly(nodeAParty, nodeBParty)
            // Update that evolvable state on issuer node.
            val oldHouse = housePublishTx.singleOutput<House>()
            val newHouse = oldHouse.state.data.copy(valuation = 800_000L.GBP)
            val houseUpdateTx = issuer.rpc.startFlowDynamic(UpdateEvolvableToken::class.java, oldHouse, newHouse).returnValue.getOrThrow()
            // Check that both nodeA and B got update.
            nodeA.rpc.watchForTransaction(houseUpdateTx).getOrThrow(5.seconds)
            nodeB.rpc.watchForTransaction(houseUpdateTx).getOrThrow(5.seconds)
            val houseA = nodeA.rpc.startFlow(TokenDriverTest::CheckTokenPointer, housePtr).returnValue.getOrThrow()
            val houseB = nodeB.rpc.startFlow(TokenDriverTest::CheckTokenPointer, housePtr).returnValue.getOrThrow()
            assertThat(houseA.valuation).isEqualTo(800_000L.GBP)
            assertThat(houseB.valuation).isEqualTo(800_000L.GBP)
            assertThatExceptionOfType(CordaRuntimeException::class.java).isThrownBy {
                nodeA.rpc.startFlowDynamic(
                        RedeemNonFungibleTokens::class.java,
                        housePtr,
                        issuerParty,
                        emptyList<Party>()
                ).returnValue.getOrThrow()
            }.withMessageContaining("Exactly one owned token of a particular type")
            // NodeB redeems house with the issuer (notice that issuer doesn't know about NodeB confidential identity used for the move with NodeA).
            val redeemHouseTx = nodeB.rpc.startFlowDynamic(
                    RedeemNonFungibleTokens::class.java,
                    housePtr,
                    issuerParty,
                    emptyList<Party>()
            ).returnValue.getOrThrow()
            nodeB.rpc.watchForTransaction(redeemHouseTx).getOrThrow(5.seconds)
            assertThat(nodeB.rpc.vaultQueryBy<NonFungibleToken<TokenPointer<House>>>(ownedTokenCriteria(housePtr)).states).isEmpty()
            // NodeA redeems 1_100_000L.GBP with the issuer, check it received 800_000L change, check, that issuer didn't record cash.
            val redeemGBPTx = nodeA.rpc.startFlowDynamic(
                    RedeemFungibleTokens::class.java,
                    1_100_000L.GBP,
                    issuerParty,
                    emptyList<Party>()
            ).returnValue.getOrThrow()
            nodeA.rpc.watchForTransaction(redeemGBPTx).getOrThrow(5.seconds)
            assertThat(nodeA.rpc.vaultQueryBy<FungibleToken<FiatCurrency>>(tokenAmountCriteria(GBP)).states.sumTokenStateAndRefs()).isEqualTo(800_000L.GBP issuedBy issuerParty)
            assertThat(issuer.rpc.vaultQueryBy<FungibleToken<FiatCurrency>>(tokenAmountCriteria(GBP)).states.sumTokenStateAndRefsOrZero(GBP issuedBy issuerParty)).isEqualTo(Amount.zero(GBP issuedBy issuerParty))
        }
    }

    // This is very simple test flow for DvP.
    @CordaSerializable
    private class DvPNotification(val amount: Amount<FiatCurrency>)

    @StartableByRPC
    @InitiatingFlow
    class DvPFlow(val house: House, val newOwner: Party) : FlowLogic<SignedTransaction>() {
        @Suspendable
        override fun call(): SignedTransaction {
            val houseStateRef = serviceHub.vaultService.ownedTokensByToken(house.toPointer<House>()).states.singleOrNull()
                    ?: throw IllegalArgumentException("Couldn't find house state: $house in the vault.")
            val txBuilder = TransactionBuilder(notary = getPreferredNotary(serviceHub))
            addMoveTokens(txBuilder, houseStateRef.state.data.token.tokenType, newOwner)
            val session = initiateFlow(newOwner)
            // Ask for input stateAndRefs - send notification with the amount to exchange.
            session.send(DvPNotification(house.valuation))
            // TODO add some checks for inputs and outputs
            val inputs = subFlow(ReceiveStateAndRefFlow<FungibleToken<FiatCurrency>>(session))
            // Receive outputs (this is just quick and dirty, we could calculate them on our side of the flow).
            val outputs = session.receive<List<FungibleToken<FiatCurrency>>>().unwrap { it }
            addMoveTokens(txBuilder, inputs, outputs)
            // Synchronise any confidential identities
            subFlow(IdentitySyncFlow.Send(session, txBuilder.toWireTransaction(serviceHub)))
            val ourSigningKeys = txBuilder.toLedgerTransaction(serviceHub).ourSigningKeys(serviceHub)
            val initialStx = serviceHub.signInitialTransaction(txBuilder, signingPubKeys = ourSigningKeys)
            val stx = subFlow(CollectSignaturesFlow(initialStx, listOf(session), ourSigningKeys))
            // Update distribution list.
            subFlow(UpdateDistributionListFlow(stx))
            return subFlow(ObserverAwareFinalityFlow(stx, listOf(session)))
        }
    }

    @InitiatedBy(DvPFlow::class)
    class DvPFlowHandler(val otherSession: FlowSession) : FlowLogic<Unit>() {
        @Suspendable
        override fun call() {
            // Receive notification with house price.
            val dvPNotification = otherSession.receive<DvPNotification>().unwrap { it }
            // Chose state and refs to send back.
            // TODO This is API pain, we assumed that we could just modify TransactionBuilder, but... it cannot be sent over the wire, because non-serializable
            // We need custom serializer and some custom flows to do checks.
            val (inputs, outputs) = TokenSelection(serviceHub).generateMove(runId.uuid, listOf(PartyAndAmount(otherSession.counterparty, dvPNotification.amount)))
            subFlow(SendStateAndRefFlow(otherSession, inputs))
            otherSession.send(outputs)
            subFlow(IdentitySyncFlow.Receive(otherSession))
            subFlow(object : SignTransactionFlow(otherSession) {
                override fun checkTransaction(stx: SignedTransaction) {}
            }
            )
            subFlow(ObserverAwareFinalityFlowHandler(otherSession))
        }
    }

    @StartableByRPC
    class GetDistributionList(val housePtr: TokenPointer<House>) : FlowLogic<List<DistributionRecord>>() {
        @Suspendable
        override fun call(): List<DistributionRecord> {
            return getDistributionList(serviceHub, housePtr.pointer.pointer)
        }
    }

    @StartableByRPC
    class CheckTokenPointer(val housePtr: TokenPointer<House>) : FlowLogic<House>() {
        @Suspendable
        override fun call(): House {
            return housePtr.pointer.resolve(serviceHub).state.data
        }
    }
}
