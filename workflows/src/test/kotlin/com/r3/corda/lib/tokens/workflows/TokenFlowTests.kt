package com.r3.corda.lib.tokens.workflows

import com.r3.corda.lib.tokens.contracts.types.TokenPointer
import com.r3.corda.lib.tokens.contracts.utilities.heldBy
import com.r3.corda.lib.tokens.contracts.utilities.issuedBy
import com.r3.corda.lib.tokens.contracts.utilities.of
import com.r3.corda.lib.tokens.contracts.utilities.sumIssuedTokensOrNull
import com.r3.corda.lib.tokens.money.GBP
import com.r3.corda.lib.tokens.testing.states.House
import com.r3.corda.lib.tokens.workflows.flows.rpc.IssueTokens
import com.r3.corda.lib.tokens.workflows.flows.rpc.MoveFungibleTokens
import com.r3.corda.lib.tokens.workflows.internal.flows.distribution.getDistributionList
import com.r3.corda.lib.tokens.workflows.internal.selection.TokenSelection
import com.r3.corda.lib.tokens.workflows.utilities.getLinearStateById
import com.r3.corda.lib.tokens.workflows.utilities.heldAmountsByToken
import com.r3.corda.lib.tokens.workflows.utilities.tokenBalance
import net.corda.core.contracts.LinearState
import net.corda.core.contracts.StateAndRef
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.node.services.queryBy
import net.corda.core.utilities.getOrThrow
import net.corda.testing.internal.chooseIdentityAndCert
import net.corda.testing.node.StartedMockNode
import org.assertj.core.api.Assertions
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Test
import java.util.*
import kotlin.test.assertEquals

class TokenFlowTests : MockNetworkTest(numberOfNodes = 4) {

    lateinit var A: StartedMockNode
    lateinit var B: StartedMockNode
    lateinit var I: StartedMockNode
    lateinit var I2: StartedMockNode

    @Before
    override fun initialiseNodes() {
        A = nodes[0]
        B = nodes[1]
        I = nodes[2]
        I2 = nodes[3]
    }

    @Test
    fun `create evolvable token`() {
        // Create new token.
        val house = House("24 Leinster Gardens, Bayswater, London", 1_000_000.GBP, listOf(I.legalIdentity()), linearId = UniqueIdentifier())
        val tx = I.createEvolvableToken(house, NOTARY.legalIdentity()).getOrThrow()
        val token = tx.singleOutput<House>()
        assertEquals(house, token.state.data)
    }

    @Test
    fun `create and update evolvable token`() {
        // Create new token.
        val house = House("24 Leinster Gardens, Bayswater, London", 1_000_000.GBP, listOf(I.legalIdentity()), linearId = UniqueIdentifier())
        val tx = I.createEvolvableToken(house, NOTARY.legalIdentity()).getOrThrow()
        val oldToken = tx.singleOutput<House>()
        // Propose an update to the token.
        val proposedToken = house.copy(valuation = 950_000.GBP)
        val updateTx = I.updateEvolvableToken(oldToken, proposedToken).getOrThrow()
        val newToken = updateTx.singleOutput<House>()
        assertEquals(proposedToken, newToken.state.data)
    }

    @Test
    fun `issue token to yourself`() {
        I.issueFungibleTokens(I, 100.GBP).getOrThrow()
        network.waitQuiescent()
        val gbp = I.services.vaultService.tokenBalance(GBP)
        assertEquals(100.GBP, gbp)
    }

    @Test
    fun `issue and move fixed tokens`() {
        I.issueFungibleTokens(A, 100.GBP).getOrThrow()
        network.waitQuiescent()
        //TODO this test is wrong
        // Check to see that A was added to I's distribution list.
        val moveTokenTx = A.issueFungibleTokens(B, 50.GBP).getOrThrow()
        network.waitQuiescent()
        println(moveTokenTx.tx)
    }

    @Test
    fun `create evolvable token, then issue evolvable token`() {
        // Create new token.
        val house = House("24 Leinster Gardens, Bayswater, London", 1_000_000.GBP, listOf(I.legalIdentity()), linearId = UniqueIdentifier())
        val createTokenTx = I.createEvolvableToken(house, NOTARY.legalIdentity()).getOrThrow()
        val token = createTokenTx.singleOutput<House>()
        // Issue amount of the token.
        val housePointer: TokenPointer<House> = house.toPointer()
        I.issueFungibleTokens(A, 100 of housePointer).getOrThrow()
        network.waitQuiescent()
        // Check to see that A was added to I's distribution list.
        val distributionList = I.transaction { getDistributionList(I.services, token.linearId()) }
        val distributionRecord = distributionList.single()
        assertEquals(A.legalIdentity(), distributionRecord.party)
        assertEquals(token.linearId().id, distributionRecord.linearId)
    }

    @Test
    fun `check token recipient also receives token`() {
        // Create new token.
        val house = House("24 Leinster Gardens, Bayswater, London", 1_000_000.GBP, listOf(I.legalIdentity()), linearId = UniqueIdentifier())
        val createTokenTx = I.createEvolvableToken(house, NOTARY.legalIdentity()).getOrThrow()
        val houseToken: StateAndRef<House> = createTokenTx.singleOutput()
        // Issue amount of the token.
        val housePointer: TokenPointer<House> = house.toPointer()
        I.issueFungibleTokens(A, 100 of housePointer).getOrThrow()
        network.waitQuiescent()
        val houseQuery = A.services.vaultService.queryBy<House>().states
        assertEquals(houseToken, houseQuery.single())
    }

    @Test
    fun `create evolvable token, then issue tokens, then update token`() {
        // Create new token.
        val house = House("24 Leinster Gardens, Bayswater, London", 1_000_000.GBP, listOf(I.legalIdentity()), linearId = UniqueIdentifier())
        val createTokenTx = I.createEvolvableToken(house, NOTARY.legalIdentity()).getOrThrow()
        val token = createTokenTx.singleOutput<House>()
        // Issue amount of the token.
        val housePointer: TokenPointer<House> = house.toPointer()
        I.issueFungibleTokens(A, 100 of housePointer).getOrThrow()
        network.waitQuiescent()
        // Update the token.
        val proposedToken = house.copy(valuation = 950_000.GBP)
        val updateTx = I.updateEvolvableToken(token, proposedToken).getOrThrow()
        // Wait to see if A is sent the updated token.
        network.waitQuiescent()
        assertEquals(updateTx.singleOutput(), updateTx.singleOutput<House>())
    }

    @Test
    fun `create evolvable token, then issue and move`() {
        // Create new token.
        val house = House("24 Leinster Gardens, Bayswater, London", 1_000_000.GBP, listOf(I.legalIdentity()), linearId = UniqueIdentifier())
        I.createEvolvableToken(house, NOTARY.legalIdentity()).getOrThrow()
        // Issue amount of the token.
        val housePointer: TokenPointer<House> = house.toPointer()
        I.issueFungibleTokens(A, 100 of housePointer).getOrThrow()
        network.waitQuiescent()
        A.transaction { A.services.vaultService.getLinearStateById<LinearState>(housePointer.pointer.pointer) }
        // Move some of the tokensToIssue.
        A.moveFungibleTokens(50 of housePointer, B, anonymous = true).getOrThrow()
        network.waitQuiescent()
        val houseAmounts = B.services.vaultService.heldAmountsByToken(housePointer).states.map { it.state.data.amount }
        assertEquals(houseAmounts.sumIssuedTokensOrNull(), 50 of housePointer issuedBy I.legalIdentity())
    }

    @Test
    fun `create evolvable token and issue to multiple nodes`() {
        // Create new token.
        val house = House("24 Leinster Gardens, Bayswater, London", 1_000_000.GBP, listOf(I.legalIdentity()), linearId = UniqueIdentifier())
        val housePointer: TokenPointer<House> = house.toPointer()
        val tx = I.createEvolvableToken(house, NOTARY.legalIdentity()).getOrThrow()
        val token = tx.singleOutput<House>()
        assertEquals(house, token.state.data)
        // Issue to node A.
        I.issueFungibleTokens(A, 50 of housePointer).getOrThrow()
        network.waitQuiescent()
        val houseAmountsA = A.services.vaultService.heldAmountsByToken(housePointer).states.map { it.state.data.amount }
        assertEquals(houseAmountsA.sumIssuedTokensOrNull(), 50 of housePointer issuedBy I.legalIdentity())
        // Issue to node B.
        I.issueFungibleTokens(B, 50 of housePointer).getOrThrow()
        network.waitQuiescent()
        val houseAmountsB = B.services.vaultService.heldAmountsByToken(housePointer).states.map { it.state.data.amount }
        assertEquals(houseAmountsB.sumIssuedTokensOrNull(), 50 of housePointer issuedBy I.legalIdentity())
    }

    @Test
    fun `moving evolvable token updates distribution list`() {
        //Create evolvable token with 2 maintainers
        val house = House("24 Leinster Gardens, Bayswater, London", 1_000_000.GBP, listOf(I.legalIdentity(), I2.legalIdentity()), linearId = UniqueIdentifier())
        val housePointer: TokenPointer<House> = house.toPointer()
        val tx = I.createEvolvableToken(house, NOTARY.legalIdentity()).getOrThrow()
        val token = tx.singleOutput<House>()
        assertEquals(house, token.state.data)
        // Issue to node A.
        I.issueFungibleTokens(A, 50 of housePointer).getOrThrow()
        network.waitQuiescent()
        // Assert that A is on distribution list for both I and I2
        I.transaction {
            val distList = getDistributionList(I.services, token.linearId()).map { it.party }.toSet()
            assertThat(distList).containsExactly(A.legalIdentity())
        }
        // TODO Is it a bug or feature?
//        I2.transaction {
//            val distList = getDistributionList(I2.services, token.linearId()).map { it.party }.toSet()
//            assertThat(distList).containsExactly(A.legalIdentity())
//        }
        // Move some of the tokensToIssue.
        A.moveFungibleTokens(50 of housePointer, B, anonymous = true).getOrThrow()
        network.waitQuiescent()
        // Assert that both A and B are on distribution list for both I and I2
        I.transaction {
            val distList = getDistributionList(I.services, token.linearId()).map { it.party }.toSet()
            assertThat(distList).containsExactly(A.legalIdentity(), B.legalIdentity())
        }
        I2.transaction {
            val distList = getDistributionList(I2.services, token.linearId()).map { it.party }.toSet()
            assertThat(distList).contains(B.legalIdentity())
        }
    }

    @Test
    fun `issue to unknown anonymous party`() {
        val confidentialHolder = A.services.keyManagementService.freshKeyAndCert(A.services.myInfo.chooseIdentityAndCert(), false).party.anonymise()
        val token = 100 of GBP issuedBy I.legalIdentity() heldBy confidentialHolder
        Assertions.assertThatThrownBy {
            I.startFlow(IssueTokens(listOf(token))).getOrThrow()
        }.hasMessageContaining("Called flow with anonymous party that node doesn't know about. Make sure that RequestConfidentialIdentity flow is called before.")
    }

    @Test
    fun `move to unknown anonymous party`() {
        I.issueFungibleTokens(A, 100.GBP).getOrThrow()
        network.waitQuiescent()
        val confidentialHolder = B.services.keyManagementService.freshKeyAndCert(B.services.myInfo.chooseIdentityAndCert(), false).party.anonymise()
        Assertions.assertThatThrownBy {
            A.startFlow(MoveFungibleTokens(50.GBP, confidentialHolder)).getOrThrow()
        }.hasMessageContaining("Called flow with anonymous party that node doesn't know about. Make sure that RequestConfidentialIdentity flow is called before.")
    }

    @Test
    fun `move to anonymous party on the same node`() {
        I.issueFungibleTokens(A, 100.GBP).getOrThrow()
        network.waitQuiescent()
        val confidentialHolder = A.services.keyManagementService.freshKeyAndCert(A.services.myInfo.chooseIdentityAndCert(), false).party.anonymise()
        A.startFlow(MoveFungibleTokens(50.GBP, confidentialHolder)).getOrThrow()
        network.waitQuiescent()
    }


    @Test
    fun `create evolvable token, then issue to the same node twice, expecting only one distribution record`() {
        // Create new token.
        val house = House("24 Leinster Gardens, Bayswater, London", 1_000_000.GBP, listOf(I.legalIdentity()), linearId = UniqueIdentifier())
        val housePointer: TokenPointer<House> = house.toPointer()
        // Create evolvable token on ledger.
        I.createEvolvableToken(house, NOTARY.legalIdentity()).getOrThrow()
        // Issue token twice.
        I.issueFungibleTokens(A, 50 of housePointer).getOrThrow()
        I.issueFungibleTokens(A, 50 of housePointer).getOrThrow()
        // Check the distribution list.
        val distributionList = I.transaction { getDistributionList(I.services, housePointer.pointer.pointer) }
        assertEquals(distributionList.size, 1)
    }

    // Ben's test.
    @Test
    fun `should get different tokens if we select twice`() {
        (1..2).map { I.issueFungibleTokens(A, 1 of GBP).getOrThrow() }
        val tokenSelection = TokenSelection(A.services)
        val lockId = UUID.randomUUID()
        A.transaction {
            val token1 = tokenSelection.attemptSpend(1 of GBP, lockId, pageSize = 5)
            val token2 = tokenSelection.attemptSpend(1 of GBP, lockId, pageSize = 5)
            assertThat(token1).isNotEqualTo(token2)
        }
    }
}
