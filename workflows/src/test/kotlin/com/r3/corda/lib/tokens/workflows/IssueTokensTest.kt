package com.r3.corda.lib.tokens.workflows

import com.r3.corda.lib.tokens.contracts.states.AbstractToken
import com.r3.corda.lib.tokens.contracts.states.FungibleToken
import com.r3.corda.lib.tokens.contracts.states.NonFungibleToken
import com.r3.corda.lib.tokens.contracts.types.IssuedTokenType
import com.r3.corda.lib.tokens.contracts.types.TokenType
import com.r3.corda.lib.tokens.contracts.utilities.heldBy
import com.r3.corda.lib.tokens.contracts.utilities.issuedBy
import com.r3.corda.lib.tokens.contracts.utilities.of
import com.r3.corda.lib.tokens.money.EUR
import com.r3.corda.lib.tokens.money.GBP
import com.r3.corda.lib.tokens.money.USD
import com.r3.corda.lib.tokens.workflows.flows.rpc.ConfidentialIssueTokens
import com.r3.corda.lib.tokens.workflows.flows.rpc.IssueTokens
import com.r3.corda.lib.tokens.workflows.flows.rpc.MoveFungibleTokens
import com.r3.corda.lib.tokens.workflows.types.PartyAndAmount
import com.r3.corda.lib.tokens.workflows.utilities.heldBy
import net.corda.core.contracts.Amount
import net.corda.core.identity.Party
import net.corda.core.node.services.queryBy
import net.corda.core.utilities.getOrThrow
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.MockNetworkParameters
import net.corda.testing.node.StartedMockNode
import net.corda.testing.node.TestCordapp
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals


class IssueTokensTest {
    lateinit var network: MockNetwork
    lateinit var nodeA: StartedMockNode
    lateinit var nodeB: StartedMockNode
    lateinit var nodeC: StartedMockNode
    lateinit var nodeD: StartedMockNode
    lateinit var nodeI: StartedMockNode

    @Before
    fun setup() {
        network = MockNetwork(
                MockNetworkParameters(
                        networkParameters = testNetworkParameters(minimumPlatformVersion = 4),
                        cordappsForAllNodes = listOf(TestCordapp.findCordapp("com.r3.corda.lib.tokens.money"),
                                TestCordapp.findCordapp("com.r3.corda.lib.tokens.contracts"),
                                TestCordapp.findCordapp("com.r3.corda.lib.tokens.workflows"),
                                TestCordapp.findCordapp("com.r3.corda.lib.tokens.testing")
                        )
                )
        )
        nodeA = network.createPartyNode()
        nodeB = network.createPartyNode()
        nodeC = network.createPartyNode()
        nodeD = network.createPartyNode()
        nodeI = network.createPartyNode()
        network.runNetwork()
    }

    @After
    fun tearDown() {
        network.stopNodes()
    }

    /*
    * Should possible to issue tokens to self
    **/
    @Test
    fun `issue token to yourself`() {
        //Creating fungible token
        val token = 100 of GBP issuedBy nodeA.legalIdentity() heldBy nodeA.legalIdentity()
        //Calling IssueToken Flow
        val issuedToken = nodeA.startFlow(IssueTokens(listOf(token)))
        network.runNetwork()
        val issuedGBPSignedTransaction = issuedToken.getOrThrow()
        //Getting the FungibleToken State from transaction hash
        val issuedGBP = issuedGBPSignedTransaction.coreTransaction.outRefsOfType<FungibleToken>().single()
        //Getting the FungibleToken State from node A
        val gbp = nodeA.services.vaultService.queryBy<FungibleToken>().states
        //checking the issued token states
        Assert.assertEquals(listOf(issuedGBP), gbp)
    }

    /*
    * It should be possible to issue a custom token to self
    * */
    @Test
    fun `issue custom token to yourself`() {
        //Creating custome token
        val customToken = TokenType("CUSTOM_TOKEN", 3)
        //Setting issuer and token type
        val issuedType = IssuedTokenType(nodeA.legalIdentity(), customToken)
        //Assigning the amount of token to issue
        val amountToIssue = Amount(100, issuedType)
        //Creating Fungible token by passing Amount and to whom the amount should be issued
        val tokenToIssue = FungibleToken(amountToIssue, nodeA.legalIdentity())
        //Calling IssueToken Flow
        val issuedToken = nodeA.startFlow(IssueTokens(listOf(tokenToIssue)))
        network.runNetwork()
        val issuedcustomTokenSignedTransaction = issuedToken.getOrThrow()
        //Getting the FungibleToken State from transaction hash
        val issuedcustomToken = issuedcustomTokenSignedTransaction.coreTransaction.outRefsOfType<FungibleToken>().single()
        val customTokenType = nodeA.services.vaultService.queryBy<FungibleToken>().states
        //checking the issued token
        Assert.assertEquals(listOf(issuedcustomToken), customTokenType)
    }

    /**
     * It should be possible to issue a single token type in a single flow
     */
    @Test
    fun `should issue a single token type`() {
        //Creating a token type
        val tokenType = 100.GBP issuedBy nodeA.legalIdentity() heldBy nodeB.legalIdentity()

        //node A issue the token type to node B
        val issue = nodeA.startFlow(IssueTokens(listOf(tokenType)))
        network.runNetwork()
        val issueTx = issue.getOrThrow()
        //Store the transaction result output in issuedToken as AbstractToken
        //val issuedToken = issueTx.singleOutput<AbstractToken>()
        val issuedToken = issueTx.coreTransaction.outRefsOfType<FungibleToken>().single()
        // Expect proposed and issued tokens' issuer to match
        assertEquals(tokenType.issuer, issuedToken.state.data.issuer, "Issuers must be equal")

        // Expect proposed and issued tokens' token type to match
        assertEquals(tokenType.issuedTokenType, issuedToken.state.data.issuedTokenType, "Issued Token Type must be equal")

        // Expect proposed and issued tokens' holder to match
        assertEquals(tokenType.holder, issuedToken.state.data.holder, "Holders must be equal")
    }


    /**
     * It should be possible to issue a single token type to a single party in a single flow
     */
    @Test
    fun `should issue tokens to a single party`() {
        //Creating a token type by passing only one party as issueTo parameter. Here node A issues the token to node B
        val tokenType = 100.GBP issuedBy nodeA.legalIdentity() heldBy nodeB.legalIdentity()

        //node A issue the token type to node B
        val issue = nodeA.startFlow(IssueTokens(listOf(tokenType)))
        network.runNetwork()
        val issueTx = issue.getOrThrow()

        //Store the transaction result output in issuedToken as AbstractToken
        val issuedToken = issueTx.singleOutput<AbstractToken>()

        // Expect proposed and issued tokens' issuer to match
        assertEquals(tokenType.issuer, issuedToken.state.data.issuer, "Issuers must be equal")

        // Expect proposed and issued tokens' participants to match
        assertEquals(tokenType.participants, issuedToken.state.data.participants, "Participants must be equal")

        // Expect proposed and issued tokens' holder to match
        assertEquals(tokenType.holder, issuedToken.state.data.holder, "Holders must be equal")
    }


    /*
    * This flow checks to issue same type of tokens to multiple parties with observers added
    * */
    @Test
    fun `should issue tokens to multiple parties`() {
        //Assuming node I as issuer of tokens
        val issuer = nodeI.legalIdentity()
        //Issue tokens to multiple parties
        val issueToNodeA = 100.GBP issuedBy issuer heldBy nodeA.legalIdentity()
        val issueToNodeB = 150.GBP issuedBy issuer heldBy nodeB.legalIdentity()
        //Observer list
        //Execute issueToken flow with node C and node D as observers
        val issueToMultiParties = nodeI.startFlow(IssueTokens(listOf(issueToNodeA, issueToNodeB)))
        network.runNetwork()
        //Get the signed transaction
        val issueToMultiplePartiesTx = issueToMultiParties.getOrThrow()
        //Check whether the above transaction is recorded by all parties involved in the transaction
        assertHasTransaction(issueToMultiplePartiesTx, network, nodeI, nodeA, nodeB)
        //Getting the states and references from parties involved in the transaction
        val statesInNodeA = nodeA.services.vaultService.queryBy<FungibleToken>().states[0]
        val statesInNodeB = nodeB.services.vaultService.queryBy<FungibleToken>().states[0]
        //Getting state and references from the transaction
        val statesFromIssueToMultiplePartiesTx = issueToMultiplePartiesTx.coreTransaction.outRefsOfType<FungibleToken>()
        //Comparing the states with nodes
        assertEquals(statesFromIssueToMultiplePartiesTx[0], statesInNodeA)
        assertEquals(statesFromIssueToMultiplePartiesTx[1], statesInNodeB)
    }

    /*
   * This flow checks to issue tokens without observers
   **/
    @Test
    fun `should issue tokens without observers`() {
        //Empty observer list
        val observer = emptyList<Party>()
        //Assuming node I as issuer of token
        val issuer = nodeI.legalIdentity()
        //Issue tokens to node A
        val issueToNodeA = 100.GBP issuedBy issuer heldBy nodeA.legalIdentity()
        //Execute issueToken flow with no observers
        val issue = nodeI.startFlow(IssueTokens(listOf(issueToNodeA), observer))
        network.runNetwork()
        //Get the signed transaction
        val issueTx = issue.getOrThrow()
        //Checking whether the above transaction is recorded in each node
        assertHasTransaction(issueTx, network, nodeI, nodeA)
        //Get the state and reference from transaction
        val issueToken = issueTx.singleOutput<FungibleToken>()
        //Get the state and reference from nodes
        val statesInNodeA = nodeA.services.vaultService.queryBy<FungibleToken>().states[0]
        //Compare the states
        assertEquals(issueToken, statesInNodeA)

    }

    /*
    * This flow test will issue tokens with observers added
    **/
    @Test
    fun `should issue tokens with observers`() {
        //Assuming node I as issuer of token
        val issuer = nodeI.legalIdentity()
        //Issue tokens to node A
        val issueToNodeA = 100.GBP issuedBy issuer heldBy nodeA.legalIdentity()
        //Execute issueToken flow with observer as node B
        val issue = nodeI.startFlow(IssueTokens(listOf(issueToNodeA), listOf(nodeB.legalIdentity())))
        network.runNetwork()
        val issueTx = issue.getOrThrow()
        //Checking whether the above transaction is recorded in each node
        assertHasTransaction(issueTx, network, nodeI, nodeA, nodeB)
        //Get the state and reference from transaction
        val issueToken = issueTx.singleOutput<FungibleToken>()
        //Get the state and reference from nodes
        val statesInNodeA = nodeA.services.vaultService.queryBy<FungibleToken>().states[0]
        val statesInNodeB = nodeB.services.vaultService.queryBy<FungibleToken>().states[0]
        //Compare the states
        assertEquals(issueToken, statesInNodeA)
        assertEquals(issueToken, statesInNodeB)
    }


    /**
     * It should be possible to issue multiple token types in a single flow
     */
    @Test
    fun `should issue multiple token types`() {
        //Creating two token types
        val tokenType1 = 100.GBP issuedBy nodeA.legalIdentity() heldBy nodeB.legalIdentity()
        val tokenType2 = 50.USD issuedBy nodeA.legalIdentity() heldBy nodeB.legalIdentity()

        //node A issue both token types to node B
        val issueSignedTransaction = nodeA.startFlow(IssueTokens(listOf(tokenType1, tokenType2)))
        network.runNetwork()
        val issueTx = issueSignedTransaction.getOrThrow()
        val issuedToken = issueTx.coreTransaction.outRefsOfType<AbstractToken>()

        // Check that issued tokens' issuer match with token type's issuers
        assertEquals(tokenType1.issuer, issuedToken[0].state.data.issuer, "Issuers must be equal")
        assertEquals(tokenType2.issuer, issuedToken[1].state.data.issuer, "Issuers must be equal")

        // Check that issued tokens' holder match with token type's holders
        assertEquals(tokenType1.holder, issuedToken[0].state.data.holder, "Holders must be equal")
        assertEquals(tokenType2.holder, issuedToken[1].state.data.holder, "Holders must be equal")

        //Check that issued tokens from transaction are same as that of the created token types
        Assert.assertEquals(issuedToken[0].state.data.issuedTokenType.tokenType, GBP)
        Assert.assertEquals(issuedToken[1].state.data.issuedTokenType.tokenType, USD)
    }


    /*
    *This flow test checks the issue of fungible tokens.
    **/
    @Test
    fun `should issue fungible tokens`() {
        //Created a fungible token
        val fungibleToken = 100.USD issuedBy nodeI.legalIdentity() heldBy nodeA.legalIdentity()
        //Fungible token issued to nodeA by nodeI without observers
        val tokenIssuedtoNodeA = nodeI.startFlow(IssueTokens(listOf(fungibleToken)))
        network.runNetwork()
        //Getting the transaction
        val tokenIssuedtoNodeA_Tx = tokenIssuedtoNodeA.getOrThrow()
        //Getting the state from transaction
        val stateFromtokenIssuedtoNodeA_Tx = tokenIssuedtoNodeA_Tx.coreTransaction.outRefsOfType<FungibleToken>().single()
        //Getting the state information from node A
        val usdA = nodeA.services.vaultService.queryBy<FungibleToken>().states.single()
        //Checking the above states are same
        assertEquals(stateFromtokenIssuedtoNodeA_Tx, usdA)
    }


    /*
    * This flow test will issue non-fungible tokens
    * */

    @Test
    fun `should issue non-fungible tokens`() {

        //Creating a non-fungible token of type Appartment
        val fooToken = TokenType("MyToken", 0)
        //Creating token type with the non-fungible token with node A as issuer and node B as holder
        val tokenType = fooToken issuedBy nodeA.legalIdentity() heldBy nodeB.legalIdentity()
        //Set empty list of observers
        val observers = emptyList<Party>()
        //Issue tokens to node B
        val issuedToken = nodeA.startFlow(IssueTokens(listOf(tokenType), observers))
        network.runNetwork()
        val issuedNonFungibleTokenSignedTransaction = issuedToken.getOrThrow()
        //Get the issued non fungible token's transaction reference
        val issuedNonFungibleToken = issuedNonFungibleTokenSignedTransaction.coreTransaction.outRefsOfType<NonFungibleToken>().single()
        //Get states of node B from vault
        val nonFungibleToken = nodeB.services.vaultService.queryBy<NonFungibleToken>().states
        //Check if both the result matches
        Assert.assertEquals(listOf(issuedNonFungibleToken), nonFungibleToken)
    }

    /*
   * This flow test will issue confidential fungible tokens
   * */
    @Test
    fun `should issue tokens to confidential identities`() {
        //Creating a token type where nodeA issues a token with nodeB as holder
        val tokenType = 100.GBP issuedBy nodeA.legalIdentity() heldBy nodeB.legalIdentity()
        //Create observers of empty list
        val observers = emptyList<Party>()
        //ConfidentialIssueTokens flow is called from node A which makes the identity of node B as anonymous
        val issue = nodeA.startFlow(ConfidentialIssueTokens(listOf(tokenType), observers))
        network.runNetwork()
        val issueTx = issue.getOrThrow()
        //Store the transaction reference output in issuedToken
        val issuedToken = issueTx.coreTransaction.outRefsOfType<FungibleToken>().single()
        //Checking the owning key of issued token's holder and owning key of node B do not matches
        Assert.assertNotEquals(issuedToken.state.data.holder.owningKey, nodeB.legalIdentity().owningKey)
        //Checking the holder of issued token and node B do not matches
        Assert.assertNotEquals(issuedToken.state.data.holder, nodeB.legalIdentity())
    }

    /*
   * This flow test will check if the Participant session are generated on its own in IssueTokenFlow
   * */

    @Test
    fun `should generate participants sessions on its own`() {
        //Creating a token type
        val tokenType = 100.GBP issuedBy nodeA.legalIdentity() heldBy nodeA.legalIdentity()
        //node A issue the token type to node B
        val issueTransaction = nodeA.startFlow(IssueTokens(listOf(tokenType)))
        network.runNetwork()
        val issueSignedTransaction = issueTransaction.getOrThrow()
        //Store the transaction reference output in issuedToken
        val issuedToken = issueSignedTransaction.coreTransaction.outRefsOfType<FungibleToken>().single()
        println("issuedToken.state.data.participants" + issuedToken.state.data.participants)
        //Check if the participants list is not empty since it generated by its own
        Assert.assertEquals(issuedToken.state.data.participants.contains(nodeA.legalIdentity()), true)
        //Checking if the participants from transaction result and participants from token type matches
        Assert.assertEquals(issuedToken.state.data.participants, tokenType.participants)
        //Check if transaction is present in node A and node B which indicates a session is generated between node A and node B
        assertHasTransaction(issueSignedTransaction, network, nodeA)
    }

    /*
       *This flow test checks the participant nodes with well known identity receives the token at the recipient side.
       * */
    @Test
    fun `should receive the token at the well-known recipient end`() {
        //Assuming B as token recipient
        val recipient = nodeB.legalIdentity()
        //Create token
        val token = 150.EUR issuedBy nodeI.legalIdentity() heldBy nodeB.legalIdentity()
        //issue token to node B
        val issueTokenToNodeB = nodeI.startFlow(IssueTokens(listOf(token)))
        network.runNetwork()
        //Get state of the above transaction
        val issueTx = issueTokenToNodeB.getOrThrow().coreTransaction.outRefsOfType<FungibleToken>().single()
        //Get the token holder in the transaction
        val holderInIssueTx = issueTx.state.data.holder
        //Checks whether the recipient and token holder are same
        assertEquals(recipient, holderInIssueTx)
    }

    /*
    *This flow test checks the participant nodes with confidential identity receives the token at the recipient side.
    * */
    @Test
    fun `should receive the token at the confidential recipient end`() {
        //Create token
        val token = 150.USD issuedBy nodeI.legalIdentity() heldBy nodeB.legalIdentity()
        //Issue token by making participants identity confidential
        val tokenIssuedtoNodeB = nodeI.startFlow(ConfidentialIssueTokens(listOf(token)))
        network.runNetwork()
        //Get state of the above transaction
        val issueTx = tokenIssuedtoNodeB.getOrThrow().coreTransaction.outRefsOfType<FungibleToken>().single()
        //Get token holder from issueTx
        val holderInIssueTx = issueTx.state.data.holder
        //Get owning key of token holder
        val OwningKeyOfHolderInIssueTx = holderInIssueTx.owningKey
        //Get node B's real identity
        val realIdentityOfNodeB = nodeB.legalIdentity()
        //Get node B's owning key
        val owningKeyOfNodeB = nodeB.legalIdentity().owningKey
        //checking whether node B is in its confidential identity
        Assert.assertNotEquals(holderInIssueTx, realIdentityOfNodeB)
        println("Confidential Identity of node B in ConfidentialIssueTokens Tx: " + holderInIssueTx)
        //checking whether owning keys are same
        Assert.assertNotEquals(OwningKeyOfHolderInIssueTx, owningKeyOfNodeB)
        Assert.assertEquals(issueTx.state, nodeB.services.vaultService.queryBy<FungibleToken>().states[0].state)
    }

    /* It should be possible to issue and move fixed tokens */
    @Test
    fun `issue and move fixed tokens`() {

        val token = 100 of GBP issuedBy nodeA.legalIdentity() heldBy nodeA.legalIdentity()
        val issuedToken = nodeA.startFlow(IssueTokens(listOf(token)))
        network.runNetwork()
        val issuedGBPSignedTransaction = issuedToken.getOrThrow()
        issuedGBPSignedTransaction.coreTransaction.outRefsOfType<FungibleToken>().single()
        val gbp = nodeA.services.vaultService.queryBy<FungibleToken>().states

        nodeA.startFlow(MoveFungibleTokens(PartyAndAmount(nodeB.legalIdentity(), 50.GBP)))
        network.runNetwork()
        val movedGBPSignedTransaction = issuedToken.getOrThrow()
        movedGBPSignedTransaction.coreTransaction.outRefsOfType<FungibleToken>().single()
        val newlyMovedGBPNodeB = nodeB.services.vaultService.queryBy<FungibleToken>().states
        val newBalanceGBPNodeA = nodeA.services.vaultService.queryBy<FungibleToken>().states

        Assert.assertEquals(gbp[0].state.data.amount, 100.GBP issuedBy nodeA.legalIdentity())
        Assert.assertEquals(newlyMovedGBPNodeB[0].state.data.amount, 50.GBP issuedBy nodeA.legalIdentity())
        Assert.assertEquals(newBalanceGBPNodeA[0].state.data.amount, 50.GBP issuedBy nodeA.legalIdentity())
    }
}
