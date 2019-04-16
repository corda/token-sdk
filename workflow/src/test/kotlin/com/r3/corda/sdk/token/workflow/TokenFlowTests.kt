package com.r3.corda.sdk.token.workflow

import com.r3.corda.sdk.token.contracts.types.TokenPointer
import com.r3.corda.sdk.token.contracts.types.TokenType
import com.r3.corda.sdk.token.contracts.utilities.issuedBy
import com.r3.corda.sdk.token.contracts.utilities.of
import com.r3.corda.sdk.token.contracts.utilities.sumTokenStateAndRefs
import com.r3.corda.sdk.token.money.GBP
import com.r3.corda.sdk.token.money.USD
import com.r3.corda.sdk.token.workflow.flows.IssueToken
import com.r3.corda.sdk.token.workflow.flows.MoveTokenFungible
import com.r3.corda.sdk.token.workflow.selection.TokenSelection
import com.r3.corda.sdk.token.workflow.statesAndContracts.House
import com.r3.corda.sdk.token.workflow.utilities.getDistributionList
import com.r3.corda.sdk.token.workflow.utilities.getLinearStateById
import com.r3.corda.sdk.token.workflow.utilities.ownedTokenAmountsByToken
import com.r3.corda.sdk.token.workflow.utilities.tokenAmountWithIssuerCriteria
import com.r3.corda.sdk.token.workflow.utilities.tokenBalance
import net.corda.core.contracts.Amount
import net.corda.core.contracts.LinearState
import net.corda.core.contracts.StateAndRef
import net.corda.core.flows.FlowSession
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.node.services.queryBy
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.getOrThrow
import net.corda.testing.internal.chooseIdentityAndCert
import net.corda.testing.node.StartedMockNode
import org.assertj.core.api.Assertions
import org.junit.Before
import org.junit.Test
import java.security.PublicKey
import kotlin.test.assertEquals

class TokenFlowTests : MockNetworkTest(numberOfNodes = 3) {

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
    fun `create evolvable token`() {
        // Create new token.
        val house = House("24 Leinster Gardens, Bayswater, London", 1_000_000.GBP, listOf(I.legalIdentity()))
        val tx = I.createEvolvableToken(house, NOTARY.legalIdentity()).getOrThrow()
        val token = tx.singleOutput<House>()
        assertEquals(house, token.state.data)
    }

    @Test
    fun `create and update evolvable token`() {
        // Create new token.
        val house = House("24 Leinster Gardens, Bayswater, London", 1_000_000.GBP, listOf(I.legalIdentity()))
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
        val issueTokenTx = I.issueTokens(GBP, I, NOTARY, 100.GBP).getOrThrow()
        I.watchForTransaction(issueTokenTx.id).getOrThrow()
        val gbp = I.services.vaultService.tokenBalance(GBP)
        assertEquals(100.GBP, gbp)
    }

    @Test
    fun `issue and move fixed tokens`() {
        val issueTokenTx = I.issueTokens(GBP, A, NOTARY, 100.GBP).getOrThrow()
        A.watchForTransaction(issueTokenTx.id).getOrThrow()
        // Check to see that A was added to I's distribution list.
        val moveTokenTx = A.moveTokens(GBP, B, 50.GBP, anonymous = true).getOrThrow()
        B.watchForTransaction(moveTokenTx.id).getOrThrow()
        println(moveTokenTx.tx)
    }

    @Test
    fun `create evolvable token, then issue evolvable token`() {
        // Create new token.
        val house = House("24 Leinster Gardens, Bayswater, London", 1_000_000.GBP, listOf(I.legalIdentity()))
        val createTokenTx = I.createEvolvableToken(house, NOTARY.legalIdentity()).getOrThrow()
        val token = createTokenTx.singleOutput<House>()
        // Issue amount of the token.
        val housePointer: TokenPointer<House> = house.toPointer()
        val issueTokenAmountTx = I.issueTokens(housePointer, A, NOTARY, 100 of housePointer).getOrThrow()
        A.watchForTransaction(issueTokenAmountTx.id).getOrThrow()
        // Check to see that A was added to I's distribution list.
        val distributionList = I.transaction { getDistributionList(I.services, token.linearId()) }
        val distributionRecord = distributionList.single()
        assertEquals(A.legalIdentity(), distributionRecord.party)
        assertEquals(token.linearId().id, distributionRecord.linearId)
    }

    @Test
    fun `check token recipient also receives token`() {
        // Create new token.
        val house = House("24 Leinster Gardens, Bayswater, London", 1_000_000.GBP, listOf(I.legalIdentity()))
        val createTokenTx = I.createEvolvableToken(house, NOTARY.legalIdentity()).getOrThrow()
        val houseToken: StateAndRef<House> = createTokenTx.singleOutput()
        // Issue amount of the token.
        val housePointer: TokenPointer<House> = house.toPointer()
        val issueTokenAmountTx = I.issueTokens(housePointer, A, NOTARY, 100 of housePointer).getOrThrow()
        // Wait for the transaction to be recorded by A.
        A.watchForTransaction(issueTokenAmountTx.id).getOrThrow()
        val houseQuery = A.services.vaultService.queryBy<House>().states
        assertEquals(houseToken, houseQuery.single())
    }

    @Test
    fun `create evolvable token, then issue tokens, then update token`() {
        // Create new token.
        val house = House("24 Leinster Gardens, Bayswater, London", 1_000_000.GBP, listOf(I.legalIdentity()))
        val createTokenTx = I.createEvolvableToken(house, NOTARY.legalIdentity()).getOrThrow()
        val token = createTokenTx.singleOutput<House>()
        // Issue amount of the token.
        val housePointer: TokenPointer<House> = house.toPointer()
        val issueTokenAmountTx = I.issueTokens(housePointer, A, NOTARY, 100 of housePointer).getOrThrow()
        A.watchForTransaction(issueTokenAmountTx.id).toCompletableFuture().getOrThrow()
        // Update the token.
        val proposedToken = house.copy(valuation = 950_000.GBP)
        val updateTx = I.updateEvolvableToken(token, proposedToken).getOrThrow()
        // Wait to see if A is sent the updated token.
        val updatedToken = A.watchForTransaction(updateTx.id).toCompletableFuture().getOrThrow()
        assertEquals(updateTx.singleOutput(), updatedToken.singleOutput<House>())
    }

    @Test
    fun `create evolvable token, then issue and move`() {
        // Create new token.
        val house = House("24 Leinster Gardens, Bayswater, London", 1_000_000.GBP, listOf(I.legalIdentity()))
        val createTokenTx = I.createEvolvableToken(house, NOTARY.legalIdentity()).getOrThrow()
        // Issue amount of the token.
        val housePointer: TokenPointer<House> = house.toPointer()
        val issueTokenTx = I.issueTokens(housePointer, A, NOTARY, 100 of housePointer).getOrThrow()
        A.watchForTransaction(issueTokenTx.id).toCompletableFuture().getOrThrow()
        val houseQuery = A.transaction { A.services.vaultService.getLinearStateById<LinearState>(housePointer.pointer.pointer) }
        // Move some of the tokens.
        val moveTokenTx = A.moveTokens(housePointer, B, 50 of housePointer, anonymous = true).getOrThrow()
        B.watchForTransaction(moveTokenTx.id).getOrThrow()
    }

    @Test
    fun `create evolvable token and issue to multiple nodes`() {
        // Create new token.
        val house = House("24 Leinster Gardens, Bayswater, London", 1_000_000.GBP, listOf(I.legalIdentity()))
        val housePointer: TokenPointer<House> = house.toPointer()
        val tx = I.createEvolvableToken(house, NOTARY.legalIdentity()).getOrThrow()
        val token = tx.singleOutput<House>()
        assertEquals(house, token.state.data)
        // Issue to node A.
        val issueTokenA = I.issueTokens(housePointer, A, NOTARY, 50 of housePointer).getOrThrow()
        A.watchForTransaction(issueTokenA.id).toCompletableFuture().getOrThrow()
        // Issue to node B.
        val issueTokenB = I.issueTokens(housePointer, A, NOTARY, 50 of housePointer).getOrThrow()
        A.watchForTransaction(issueTokenB.id).toCompletableFuture().getOrThrow()
    }

    @Test
    fun `issue to unknown anonymous party`() {
        val confidentialHolder = A.services.keyManagementService.freshKeyAndCert(A.services.myInfo.chooseIdentityAndCert(), false).party.anonymise()
        Assertions.assertThatThrownBy {
            I.startFlow(IssueToken.Initiator(GBP, confidentialHolder, NOTARY.legalIdentity(), 100.GBP)).getOrThrow()
        }.hasMessageContaining("Called IssueToken flow with anonymous party that node doesn't know about. Make sure that RequestConfidentialIdentity flow is called before.")
    }

    @Test
    fun `move to unknown anonymous party`() {
        val issueTokenTx = I.issueTokens(GBP, A, NOTARY, 100.GBP).getOrThrow()
        A.watchForTransaction(issueTokenTx.id).getOrThrow()
        val confidentialHolder = B.services.keyManagementService.freshKeyAndCert(B.services.myInfo.chooseIdentityAndCert(), false).party.anonymise()
        Assertions.assertThatThrownBy {
            A.startFlow(MoveTokenFungible(50.GBP, confidentialHolder)).getOrThrow()
        }.hasMessageContaining("Called MoveToken flow with anonymous party that node doesn't know about. Make sure that RequestConfidentialIdentity flow is called before.")
    }

    @Test
    fun `move to anonymous party on the same node`() {
        val issueTokenTx = I.issueTokens(GBP, A, NOTARY, 100.GBP).getOrThrow()
        A.watchForTransaction(issueTokenTx.id).getOrThrow()
        val confidentialHolder = A.services.keyManagementService.freshKeyAndCert(A.services.myInfo.chooseIdentityAndCert(), false).party.anonymise()
        val moveTokenTx = A.startFlow(MoveTokenFungible(50.GBP, confidentialHolder)).getOrThrow()
        A.watchForTransaction(moveTokenTx.id).getOrThrow()
    }

    private class MyMove<T : TokenType>(
            amount: Amount<T>,
            holder: AbstractParty,
            val changeOwner: AbstractParty,
            val issuer: Party,
            session: FlowSession? = null
    ) : MoveTokenFungible<T>(amount, holder, session) {
        override fun generateMove(): Pair<TransactionBuilder, List<PublicKey>> {
            val tokenSelection = TokenSelection(serviceHub)
            return tokenSelection.generateMove(TransactionBuilder(), amount, holder, tokenAmountWithIssuerCriteria(amount.token, issuer), changeOwner)
        }
    }

    @Test
    fun `provide custom generateMove function`() {
        val issueTokenTxI = I.issueTokens(GBP, A, NOTARY, 100.GBP).getOrThrow()
        A.watchForTransaction(issueTokenTxI.id).getOrThrow()
        val issueTokenTxB = B.issueTokens(USD, A, NOTARY, 100.USD).getOrThrow()
        A.watchForTransaction(issueTokenTxB.id).getOrThrow()
        val changeHolder = A.services.keyManagementService.freshKeyAndCert(A.services.myInfo.chooseIdentityAndCert(), false).party.anonymise()
        // Move from A to B but only I as issuer, change owner - us - assert in transaction that we used external identity
        val moveTx = A.startFlow(MyMove(13.GBP, B.legalIdentity(), changeHolder, I.legalIdentity())).getOrThrow()
        A.watchForTransaction(moveTx.id).getOrThrow()
        B.watchForTransaction(moveTx.id).getOrThrow()
        val moneyA = A.services.vaultService.ownedTokenAmountsByToken(GBP).states.sumTokenStateAndRefs()
        Assertions.assertThat(moneyA).isEqualTo(87.GBP issuedBy I.legalIdentity())
        val moneyB = B.services.vaultService.ownedTokenAmountsByToken(GBP).states.sumTokenStateAndRefs()
        Assertions.assertThat(moneyB).isEqualTo(13.GBP issuedBy I.legalIdentity())
    }
}
