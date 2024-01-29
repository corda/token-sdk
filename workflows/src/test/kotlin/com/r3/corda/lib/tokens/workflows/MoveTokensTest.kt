package com.r3.corda.lib.tokens.workflows

import com.r3.corda.lib.tokens.contracts.states.FungibleToken
import com.r3.corda.lib.tokens.contracts.states.NonFungibleToken
import com.r3.corda.lib.tokens.contracts.types.TokenType
import com.r3.corda.lib.tokens.contracts.utilities.heldBy
import com.r3.corda.lib.tokens.contracts.utilities.issuedBy
import com.r3.corda.lib.tokens.contracts.utilities.of
import com.r3.corda.lib.tokens.money.GBP
import com.r3.corda.lib.tokens.money.USD
import com.r3.corda.lib.tokens.selection.InsufficientBalanceException
import com.r3.corda.lib.tokens.workflows.flows.rpc.*
import com.r3.corda.lib.tokens.workflows.types.PartyAndAmount
import com.r3.corda.lib.tokens.workflows.types.PartyAndToken
import com.r3.corda.lib.tokens.workflows.utilities.heldBy
import net.corda.core.identity.Party
import net.corda.core.node.services.queryBy
import net.corda.core.utilities.getOrThrow
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.MockNetworkParameters
import net.corda.testing.node.StartedMockNode
import net.corda.testing.node.TestCordapp
import org.junit.*
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class MoveTokensTest {

    lateinit var network: MockNetwork
    lateinit var nodeA: StartedMockNode
    lateinit var nodeB: StartedMockNode
    lateinit var nodeC: StartedMockNode
    lateinit var nodeI: StartedMockNode

    @Before
    fun setup() {
        network = MockNetwork(
                MockNetworkParameters(
                        networkParameters = testNetworkParameters(minimumPlatformVersion = 6),
                        cordappsForAllNodes = listOf(TestCordapp.findCordapp("com.r3.corda.lib.tokens.money"),
                                TestCordapp.findCordapp("com.r3.corda.lib.tokens.contracts"),
                                TestCordapp.findCordapp("com.r3.corda.lib.tokens.workflows"),
                                TestCordapp.findCordapp("com.r3.corda.lib.tokens.testing")
                        )
                )
        )
        nodeA = network.createPartyNode()
        nodeB = network.createPartyNode()
        nodeI = network.createPartyNode()
        nodeC = network.createPartyNode()
        network.runNetwork()
    }

    @After
    fun tearDown() {
        network.stopNodes()
    }

    //This flow test should allow to move newly issued fungible token
    @Test(timeout = 300_000)
    fun `should move issued fungible tokens`() {
        //create token
        val token = 100.GBP issuedBy nodeI.legalIdentity() heldBy nodeA.legalIdentity()
        //token issued to nodeA
        val issuedToken = nodeI.startFlow(IssueTokens(listOf(token)))
        network.runNetwork()
        //getting the above transaction
        val issuedTokenSignedTransaction = issuedToken.getOrThrow()
        //Get the issued fungible token's transaction reference
        issuedTokenSignedTransaction.coreTransaction.outRefsOfType<FungibleToken>().single()
        //getting the state and reference of nodeA
        val issuedGBPOfNodeA = nodeA.services.vaultService.queryBy<FungibleToken>().states
        //move issued token to nodeB
        val moveToken = nodeA.startFlow(MoveFungibleTokens(PartyAndAmount(nodeB.legalIdentity(), 50.GBP)))
        network.runNetwork()
        //getting the transaction
        val movedGBPSignedTransaction = moveToken.getOrThrow()
        //Get the moved issued fungible token's transaction reference
        movedGBPSignedTransaction.coreTransaction.outRefsOfType<FungibleToken>()
        //getting the state and reference of nodeB
        val newlyMovedGBPNodeB = nodeB.services.vaultService.queryBy<FungibleToken>().states
        //getting the state and reference of nodeA
        val newBalanceGBPNodeA = nodeA.services.vaultService.queryBy<FungibleToken>().states
        //checking whether the conditions are true
        Assert.assertEquals(issuedGBPOfNodeA[0].state.data.amount, 100.GBP issuedBy nodeI.legalIdentity())
        Assert.assertEquals(newlyMovedGBPNodeB[0].state.data.amount, 50.GBP issuedBy nodeI.legalIdentity())
        Assert.assertEquals(newBalanceGBPNodeA[0].state.data.amount, 50.GBP issuedBy nodeI.legalIdentity())

    }

    //This flow test should allow to move newly issued non fungible token
    @Test(timeout = 300_000)
    fun `should move issued non fungible tokens`() {
        //Creating a non-fungible token of type Appartment
        val fooToken = TokenType("MyToken", 0)
        //Creating token type with the non-fungible token with node A as issuer and node B as holder
        val tokenType = fooToken issuedBy nodeI.legalIdentity() heldBy nodeB.legalIdentity()
        //Set empty list of observers
        val observers = emptyList<Party>()
        //Issue tokens to node B
        val issuedToken = nodeI.startFlow(IssueTokens(listOf(tokenType), observers))
        network.runNetwork()
        val issuedNonFungibleTokenSignedTransaction = issuedToken.getOrThrow()
        //Get the issued non fungible token's transaction reference
        issuedNonFungibleTokenSignedTransaction.coreTransaction.outRefsOfType<NonFungibleToken>().single()
        //Get states of node B from vault
        nodeB.services.vaultService.queryBy<NonFungibleToken>().states
        //moving the node
        val moveToken = nodeB.startFlow(MoveNonFungibleTokens(PartyAndToken(nodeI.legalIdentity(), fooToken)))
        network.runNetwork()
        val movedGBPSignedTransaction = moveToken.getOrThrow()
        val movedGBP = movedGBPSignedTransaction.coreTransaction.outRefsOfType<NonFungibleToken>()
        val newlyMovedGBPNodeI = nodeI.services.vaultService.queryBy<NonFungibleToken>().states
        nodeB.services.vaultService.queryBy<NonFungibleToken>().states
        Assert.assertEquals(movedGBP, newlyMovedGBPNodeI)
    }

    //This flow test should allow to move newly issued confidential fungible token
    @Test(timeout = 300_000)
    fun `should move issued confidential fungible tokens`() {
        //create and issue token
        val token = 100 of GBP issuedBy nodeI.legalIdentity() heldBy nodeA.legalIdentity()
        //executing confidential issue token flow
        val issuedConfidentialFungibleToken = nodeI.startFlow(ConfidentialIssueTokens(listOf(token)))
        //set observer list
        val observerList = listOf(nodeB.legalIdentity())
        network.runNetwork()
        //getting the transaction
        issuedConfidentialFungibleToken.getOrThrow()

        //move confidential  fungible token to nodeI
        val moveConfidentialFungibleToken = nodeA.startFlow(ConfidentialMoveFungibleTokens(PartyAndAmount(nodeI.legalIdentity(), 50.GBP), observerList))
        network.runNetwork()

        //getting the transction
        val movedCFTSignedTransaction = moveConfidentialFungibleToken.getOrThrow()

        //getting state and reference of the above transaction
        val movedCFT = movedCFTSignedTransaction.coreTransaction.outRefsOfType<FungibleToken>()

        //getting states of nodeI
        val newlyMovedCFTNodeI = nodeI.services.vaultService.queryBy<FungibleToken>().states
        //getting states of nodeA
        val newBalanceCFTNodeA = nodeA.services.vaultService.queryBy<FungibleToken>().states

        //checking both are same
        assertEquals(movedCFT, listOf(newlyMovedCFTNodeI[0], newBalanceCFTNodeA[0]))
        Assert.assertEquals(newlyMovedCFTNodeI[0].state.data.amount, 50.GBP issuedBy nodeI.legalIdentity())
        Assert.assertEquals(newBalanceCFTNodeA[0].state.data.amount, 50.GBP issuedBy nodeI.legalIdentity())
    }


    //This flow test should allow to move newly issued confidential non fungible token
    @Test(timeout = 300_000)
    fun `should move issued confidential non-fungible tokens`() {
        //create token
        val myNewToken = TokenType("MyToken", 0)
        //issue non fungible token
        val token = myNewToken issuedBy nodeI.legalIdentity() heldBy nodeA.legalIdentity()

        //executing confidential issue token flow
        val issuedNonFungibleTx = nodeI.startFlow(ConfidentialIssueTokens(listOf(token)))

        //set observer list
        val observerList = listOf(nodeB.legalIdentity())
        network.runNetwork()

        //getting the transaction
        issuedNonFungibleTx.getOrThrow()
        //move confidential non fungible token to nodeI
        val moveConfidentialNonFungibleToken = nodeA.startFlow(ConfidentialMoveNonFungibleTokens(PartyAndToken(nodeI.legalIdentity(), myNewToken), observerList))
        network.runNetwork()
        //getting the transaction
        val movedCNFTSignedTransaction = moveConfidentialNonFungibleToken.getOrThrow()

        //getting state and reference of the above transaction
        val movedCNFT = movedCNFTSignedTransaction.coreTransaction.outRefsOfType<NonFungibleToken>()

        //getting state of nodeI
        val newlyMovedCNFTNodeI = nodeI.services.vaultService.queryBy<NonFungibleToken>().states

        //comparing state and references of node I with moved token
        Assert.assertEquals(newlyMovedCNFTNodeI[0], movedCNFT[0])
    }

    /*
* This flow test return the real identities of the participants involved in fungible token move.
* */
    @Test(timeout = 300_000)
    fun `should the parties involved in fungible token move are not anonymous`() {
        //Creating fungible token
        val fungibleToken = 100.USD issuedBy nodeI.legalIdentity() heldBy nodeA.legalIdentity()
        //Fungible token issued to nodeA by nodeI without observers
        val tokenIssuedtoNodeA = nodeI.startFlow(IssueTokens(listOf(fungibleToken)))
        network.runNetwork()
        //Getting the transaction
        tokenIssuedtoNodeA.getOrThrow()
        //Move some fungible token to node B
        val moveToNodeB = nodeA.startFlow(MoveFungibleTokens(PartyAndAmount(nodeB.legalIdentity(), 50 of USD)))
        network.runNetwork()
        //Getting transaction
        val moveToNodeBTx = moveToNodeB.getOrThrow()
        assertHasTransaction(moveToNodeBTx, network, nodeB)
        //Getting the State and Reference of above transaction
        val moveTxStateRef = moveToNodeBTx.coreTransaction.outRefsOfType<FungibleToken>()
        //Getting participants involved in move fungible token
        val participantsInTx = moveTxStateRef[0].state.data.participants
        //Checking whether participants involved have real identities
        assertEquals(participantsInTx, listOf(nodeB.legalIdentity()))
    }


    /*
    * This flow test must return the real identities of the participants involved in non-fungible token move.
    * */
    @Test(timeout = 300_000)
    fun `should the parties involved in non-fungible token move are not anonymous`() {
        //Creating non-fungible token
        val myNewToken = TokenType("MyToken", 0)
        val nonFungibleToken = myNewToken issuedBy nodeI.legalIdentity() heldBy nodeA.legalIdentity()
        //Non-Fungible token issued to nodeA by nodeI without observers
        val tokenIssuedtoNodeA = nodeI.startFlow(IssueTokens(listOf(nonFungibleToken)))
        network.runNetwork()
        //Getting the transaction
        tokenIssuedtoNodeA.getOrThrow()
        //Move non-fungible token to node B
        val moveToNodeB = nodeA.startFlow(MoveNonFungibleTokens(PartyAndToken(nodeB.legalIdentity(), nonFungibleToken.tokenType)))
        network.runNetwork()
        //Getting transaction
        val moveToNodeBTx = moveToNodeB.getOrThrow()
        //Getting the participants in transaction
        val moveTxParties = moveToNodeBTx.coreTransaction.outRefsOfType<NonFungibleToken>().single().state.data.participants
        //Checking whether participants involved have real identities
        Assert.assertEquals(moveTxParties, listOf(nodeB.legalIdentity()))
    }


    //Checks if the flow moves a single fungible token from nodeA to nodeB
    @Test(timeout = 300_000)
    fun `should add a single move of fungible token `() {
        val token = 100.GBP issuedBy nodeI.legalIdentity() heldBy nodeA.legalIdentity()
        //Issue token
        nodeI.startFlow(IssueTokens(listOf(token)))
        network.runNetwork()

        nodeA.services.vaultService.queryBy<FungibleToken>().states
        //nodeA moves the  token to nodeB
        val moveTokenToB = nodeA.startFlow(MoveFungibleTokens(PartyAndAmount(nodeB.legalIdentity(), 100.GBP)))

        network.runNetwork()
        moveTokenToB.getOrThrow()
        assertFailsWith<InsufficientBalanceException> {
            val moveTokenToC = nodeA.startFlow(MoveFungibleTokens(PartyAndAmount(nodeC.legalIdentity(), 100.GBP)))
            network.runNetwork()
            moveTokenToC.getOrThrow()
        }
    }

    //checks if a node can move a single non-fungible token to another participant in a transaction
    @Test(timeout = 300_000)
    fun `should add a single move of non-fungible token `() {
        //create a non-fungible token
        val myTokenType = TokenType("MyToken", 0)
        val nonFungibleToken = myTokenType issuedBy nodeI.legalIdentity() heldBy nodeA.legalIdentity()
        //nodeI issue the token
        nodeI.startFlow(IssueTokens(listOf(nonFungibleToken)))
        network.runNetwork()
        //nodeA moves the token to nodeB
        val moveToB = nodeA.startFlow(MoveNonFungibleTokens(PartyAndToken(nodeB.legalIdentity(), nonFungibleToken.tokenType)))
        network.runNetwork()
        moveToB.getOrThrow()
        assertFailsWith<IllegalArgumentException> {
            val moveTokenToC = nodeA.startFlow(MoveNonFungibleTokens(PartyAndToken(nodeC.legalIdentity(), nonFungibleToken.tokenType)))
            network.runNetwork()
            moveTokenToC.getOrThrow()
        }
    }

    /*
    *This flow test combine multiple fungible token amounts from single issuer and move to other parties.
    * */
    @Test(timeout = 300_000)
    @Ignore("TODO JDK17: Fixme - timeout")
    fun `should combine multiple fungible tokens from a single issuer and move`() {
        //Creating fungible tokens
        val fungibleToken1 = 100.USD issuedBy nodeI.legalIdentity() heldBy nodeA.legalIdentity()
        val fungibleToken2 = 50.USD issuedBy nodeI.legalIdentity() heldBy nodeA.legalIdentity()
        //Fungible token issued to nodeA by nodeI without observers
        val tokenIssuedtoNodeA = nodeI.startFlow(IssueTokens(listOf(fungibleToken1, fungibleToken2)))
        network.runNetwork()
        //Getting the transaction
        val tokenIssuedtoNodeA_Tx = tokenIssuedtoNodeA.getOrThrow()
        assertHasTransaction(tokenIssuedtoNodeA_Tx, network, nodeI, nodeA)

        //Move some fungible token to node B
        val moveToNodeB = nodeA.startFlow(MoveFungibleTokens(PartyAndAmount(nodeB.legalIdentity(), 125 of USD)))
        network.runNetwork()
        //Getting transaction
        val moveToNodeBTx = moveToNodeB.getOrThrow()
        assertHasTransaction(moveToNodeBTx, network, nodeB, nodeA)
        //Getting the State and Reference of above transaction
        val moveTxStateRef = moveToNodeBTx.coreTransaction.outRefsOfType<FungibleToken>()
        //Getting the State and Reference from node A
        val usdA = nodeA.services.vaultService.queryBy<FungibleToken>().states[0]
        //Getting the State and Reference from node B
        val usdB = nodeB.services.vaultService.queryBy<FungibleToken>().states[0]
        //Comparing State and References
        assertEquals(moveTxStateRef, listOf(usdB, usdA))
        //Checking balance in node A
        assertEquals(usdA.state.data.amount, 25.USD issuedBy nodeI.legalIdentity())
    }

    /*
        Should have anonymous identity for the sender of moved confidential fungible token. Here the identity of holder is checked
        * before move and after move that they are not equal
        * */
    @Test(timeout = 300_000)
    fun ` should have anonymous identity for sender of moved confidential fungible token`() {
        //Create a fungible token type
        val token = 100 of GBP issuedBy nodeI.legalIdentity() heldBy nodeA.legalIdentity()
        //Issue the created fungible token type
        val issuedToken = nodeI.startFlow(ConfidentialIssueTokens(listOf(token)))
        network.runNetwork()
        //Set the signed transaction result in IssuedTx
        val issuedTx = issuedToken.getOrThrow()
        val issuedGBP = issuedTx.coreTransaction.outRefsOfType<FungibleToken>().single()

        //Get the states of node A from its vault before move
        val gbp = nodeA.services.vaultService.queryBy<FungibleToken>().states
        //Create empty observers list
        val observers = emptyList<Party>()
        //Call ConfidentialMoveFungibleTokens flow which moves 50 GBP to node B
        val moveToken = nodeA.startFlow(ConfidentialMoveFungibleTokens(PartyAndAmount(nodeB.legalIdentity(), 50.GBP), observers))
        network.runNetwork()
        val movedGBPSignedTransaction = moveToken.getOrThrow().coreTransaction.outRefsOfType<FungibleToken>()[0]
        //Get the states of node B from vault after the move
        val newlyMovedGBPNodeB = nodeB.services.vaultService.queryBy<FungibleToken>().states
        //Get the states of node A from vault after the move
        val newBalanceGBPNodeA = nodeA.services.vaultService.queryBy<FungibleToken>().states
        //Check the amount in node A before move that if it's 100 GBP
        Assert.assertEquals(gbp[0].state.data.amount, 100.GBP issuedBy nodeI.legalIdentity())
        //Check the amount in node B after move that if it's 50 GBP
        Assert.assertEquals(newlyMovedGBPNodeB[0].state.data.amount, 50.GBP issuedBy nodeI.legalIdentity())//should not get node a identity
        //Check the amount in node A after move that if it's 50 GBP
        Assert.assertEquals(newBalanceGBPNodeA[0].state.data.amount, 50.GBP issuedBy nodeI.legalIdentity())
        Assert.assertNotEquals(issuedGBP.state.data.holder, nodeA.legalIdentity())
        Assert.assertNotEquals(movedGBPSignedTransaction.state.data.holder, nodeB.legalIdentity())
    }

    /*
    Should have anonymous identity for the sender of moved confidential non-fungible token. Here the identity of holder is checked
    * before move and after move that they are not equal
    * */
    @Test(timeout = 300_000)
    fun `should have anonymous identity for sender of moved confidential non-fungible token`() {
        //Create a non-fungible token type
        val nonFungibleTokenType = TokenType("MyToken", 0)
        //Create a non-fungible token in which node A issues the token to node B
        val nonFungibleToken = nonFungibleTokenType issuedBy nodeI.legalIdentity() heldBy nodeA.legalIdentity()
        //Issue the non-fungible token
        val issuedToken = nodeI.startFlow(ConfidentialIssueTokens(listOf(nonFungibleToken)))
        network.runNetwork()
        val issuedTokenTx = issuedToken.getOrThrow().coreTransaction.outRefsOfType<NonFungibleToken>()[0]
        //Create a list of observers
        val observers = emptyList<Party>()
        //Call ConfidentialMoveNonFungibleTokens flow in which node A confidentially move non-fungible token to node B
        val moveToken = nodeA.startFlow(ConfidentialMoveNonFungibleTokens(PartyAndToken(nodeB.legalIdentity(), token = nonFungibleTokenType), observers))
        network.runNetwork()
        val moveTokenState = moveToken.getOrThrow().coreTransaction.outRefsOfType<NonFungibleToken>()[0]
        //Get the states of node B from vault after the move
        nodeB.services.vaultService.queryBy<NonFungibleToken>().states
        //Get the states of node A from vault after the move
        nodeA.services.vaultService.queryBy<NonFungibleToken>().states
        //Check that after Confidential move, the owning key of issuer is same as that of node A's owning key

        Assert.assertNotEquals(issuedTokenTx.state.data.holder, nodeA.legalIdentity())
        Assert.assertNotEquals(moveTokenState.state.data.holder, nodeB.legalIdentity())
    }


    //checks if a node can split token amounts and move them to different participants in a single transaction
    @Test(timeout = 300_000)
    fun `should move different amount of  fungible tokens to different participants in a transaction`() {
        val token = 100.GBP issuedBy nodeI.legalIdentity() heldBy nodeA.legalIdentity()
        //nodeI issues token
        nodeI.startFlow(IssueTokens(listOf(token)))

        network.runNetwork()
        val tokenInA = nodeA.services.vaultService.queryBy<FungibleToken>().states
        //verify that the token in nodeA's vault is same as the token issued
        Assert.assertEquals(tokenInA[0].state.data.amount, 100.GBP issuedBy nodeI.legalIdentity())
        //nodeA splits and  move different amounts of the token to nodeB and nodec
        nodeA.startFlow(MoveFungibleTokens(listOf(PartyAndAmount(nodeB.legalIdentity(), 60 of GBP), PartyAndAmount(nodeC.legalIdentity(), 30 of GBP))))
        network.runNetwork()

        val tokenInB = nodeB.services.vaultService.queryBy<FungibleToken>().states
        val tokenInC = nodeC.services.vaultService.queryBy<FungibleToken>().states
        val tokenBalanceInA = nodeA.services.vaultService.queryBy<FungibleToken>().states
        //verifies that the amount of token in nodeB is the actual amount moved to nodeB
        Assert.assertEquals(tokenInB[0].state.data.amount, 60 of GBP issuedBy nodeI.legalIdentity())
        //verifies that the amount of token in nodeC is the actual amount moved to nodeC
        Assert.assertEquals(tokenInC[0].state.data.amount, 30 of GBP issuedBy nodeI.legalIdentity())
        //verifies the token balance in nodeA after the move
        Assert.assertEquals(tokenBalanceInA[0].state.data.amount, 10 of GBP issuedBy nodeI.legalIdentity())
    }
}
