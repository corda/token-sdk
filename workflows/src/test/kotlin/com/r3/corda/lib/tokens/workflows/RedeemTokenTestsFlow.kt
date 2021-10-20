package com.r3.corda.lib.tokens.workflows

import com.r3.corda.lib.tokens.contracts.states.FungibleToken
import com.r3.corda.lib.tokens.contracts.states.NonFungibleToken
import com.r3.corda.lib.tokens.contracts.types.IssuedTokenType
import com.r3.corda.lib.tokens.contracts.types.TokenType
import com.r3.corda.lib.tokens.contracts.utilities.heldBy
import com.r3.corda.lib.tokens.contracts.utilities.issuedBy
import com.r3.corda.lib.tokens.contracts.utilities.of
import com.r3.corda.lib.tokens.money.BTC
import com.r3.corda.lib.tokens.money.GBP
import com.r3.corda.lib.tokens.selection.InsufficientBalanceException
import com.r3.corda.lib.tokens.workflows.flows.rpc.ConfidentialIssueTokens
import com.r3.corda.lib.tokens.workflows.flows.rpc.ConfidentialMoveFungibleTokens
import com.r3.corda.lib.tokens.workflows.flows.rpc.ConfidentialMoveNonFungibleTokens
import com.r3.corda.lib.tokens.workflows.flows.rpc.IssueTokens
import com.r3.corda.lib.tokens.workflows.flows.rpc.RedeemFungibleTokens
import com.r3.corda.lib.tokens.workflows.flows.rpc.RedeemNonFungibleTokens
import com.r3.corda.lib.tokens.workflows.types.PartyAndAmount
import com.r3.corda.lib.tokens.workflows.types.PartyAndToken
import com.r3.corda.lib.tokens.workflows.utilities.heldBy
import com.r3.corda.lib.tokens.workflows.utilities.heldTokensByToken
import com.r3.corda.lib.tokens.workflows.utilities.tokenAmountsByToken
import com.r3.corda.lib.tokens.workflows.utilities.tokenBalance
import net.corda.core.identity.Party
import net.corda.core.utilities.getOrThrow
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.MockNetworkParameters
import net.corda.testing.node.StartedMockNode
import net.corda.testing.node.TestCordapp
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals

class RedeemTokenTestsFlow{
    lateinit var network: MockNetwork
    lateinit var nodeA: StartedMockNode
    lateinit var nodeB: StartedMockNode
    lateinit var nodeI: StartedMockNode

    @Before
    fun setup() {
        network =  MockNetwork(
            MockNetworkParameters(
                networkParameters = testNetworkParameters(minimumPlatformVersion = 4),
                    cordappsForAllNodes = listOf(
                            TestCordapp.findCordapp("com.r3.corda.lib.tokens.money"),
                            TestCordapp.findCordapp("com.r3.corda.lib.tokens.contracts"),
                            TestCordapp.findCordapp("com.r3.corda.lib.tokens.workflows"),
                            TestCordapp.findCordapp("com.r3.corda.lib.tokens.testing"),
                            TestCordapp.findCordapp("com.r3.corda.lib.ci")
                )
            )
        )
        nodeA = network.createPartyNode()
        nodeB = network.createPartyNode()
        nodeI = network.createPartyNode()
        network.runNetwork()
    }

    @After
    fun tearDown() {
        network.stopNodes()
    }


    @Test
    fun `should redeem fungible tokens`() {
        val issuer: Party = nodeI.legalIdentity()
        val holder: Party = nodeA.legalIdentity()

        // Creating 10 amount of myIssuedTokenType
        val tenOfMyIssuedTokenType = 10.GBP issuedBy issuer

        // Adding a holder to an amount of a token type, creates a fungible token.
        val fungibleToken: FungibleToken = tenOfMyIssuedTokenType heldBy holder

        // Issuing Fungible Token
        nodeI.startFlow(IssueTokens(listOf(fungibleToken)))
        network.runNetwork()

        // Checking if the tokens are issued to nodeA.
        assertThat(nodeA.services.vaultService.tokenAmountsByToken(GBP).states).isNotEmpty

        // Redeeming the tokens.
        val amountToRedeem = 10.GBP
        nodeA.startFlow(RedeemFungibleTokens(amount = amountToRedeem, issuer = issuer))
        network.runNetwork()

        // Checking if the tokens are redeemed.
        assertThat((nodeA.services.vaultService.tokenAmountsByToken(GBP).states).isEmpty())
        assertThat((nodeI.services.vaultService.tokenAmountsByToken(GBP).states).isEmpty())
    }



    @Test
    fun `should redeem non fungible tokens`() {
        val issuer: Party = nodeI.legalIdentity()
        val holder: Party = nodeA.legalIdentity()

        // Creating an instance of TokenType.
        val myTokenType = TokenType("MyToken", 2)

        // Creating an instance of IssuedTokenType.
        val myIssuedTokenType: IssuedTokenType = myTokenType issuedBy issuer

        // Creating an instance of Non Fungible token.
        val nonFungibleToken: NonFungibleToken = myIssuedTokenType heldBy holder

        // Issuing Non Fungible Token
        nodeI.startFlow(IssueTokens(listOf(nonFungibleToken)))
        network.runNetwork()

        // Checking if the tokens are issued to nodeA.
        assertThat(nodeA.services.vaultService.heldTokensByToken(myTokenType).states).isNotEmpty

        // Redeeming the tokens.
        nodeA.startFlow(RedeemNonFungibleTokens(myTokenType, issuer))
        network.runNetwork()

        // Checking if the tokens are redeemed.
        assertThat(nodeA.services.vaultService.heldTokensByToken(myTokenType).states).isEmpty()
        assertThat(nodeA.services.vaultService.heldTokensByToken(myTokenType).states).isEmpty()
    }

    @Test
        fun `should not redeem same token more than once`() {
        val issuer: Party = nodeI.legalIdentity()
        val holder: Party = nodeA.legalIdentity()

        // Creating an instance of TokenType.
        val myTokenType = TokenType("MyToken", 2)

        // Creating an instance of IssuedTokenType.
        val myIssuedTokenType: IssuedTokenType = myTokenType issuedBy issuer

        // Creating an instance of Non Fungible token.
        val nonFungibleToken: NonFungibleToken = myIssuedTokenType heldBy holder

        // Issuing Non Fungible Token
        nodeI.startFlow(IssueTokens(listOf(nonFungibleToken)))
        network.runNetwork()

        // Checking if the tokens are issued to nodeA.
        assertThat(nodeA.services.vaultService.heldTokensByToken(myTokenType).states).isNotEmpty

        // Redeeming the tokens.
        nodeA.startFlow(RedeemNonFungibleTokens(myTokenType, issuer))
        network.runNetwork()

        // Checking if the tokens are redeemed.
        assertThat(nodeA.services.vaultService.heldTokensByToken(myTokenType).states).isEmpty()
        assertThat(nodeA.services.vaultService.heldTokensByToken(myTokenType).states).isEmpty()
        val exception = nodeA.startFlow(RedeemNonFungibleTokens(myTokenType, issuer))
        network.runNetwork()
        // Redeeming the tokens again.
        assertFailsWith<IllegalStateException> { exception.getOrThrow() }
    }

    @Test
    fun `should redeem a new TokenType instance`() {
        val issuer: Party = nodeI.legalIdentity()
        val holder: Party = nodeA.legalIdentity()

        val myTokenType = TokenType(tokenIdentifier = "TEST", fractionDigits = 2)
        // Creating an instance of IssuedTokenType with GBP token type and nodeI as issuer.
        val myIssuedTokenType: IssuedTokenType = myTokenType issuedBy issuer

        // Creating 10 amount of myIssuedTokenType
        val tenOfMyIssuedTokenType = 10 of myIssuedTokenType

        // Adding a holder to an amount of a token type, creates a fungible token.
        val fungibleToken: FungibleToken = tenOfMyIssuedTokenType heldBy holder

        // Issuing Fungible Token
        nodeI.startFlow(IssueTokens(listOf(fungibleToken)))
        network.runNetwork()

        // Checking if the tokens are issued to nodeA.
        assertThat(nodeA.services.vaultService.tokenAmountsByToken(myTokenType).states).isNotEmpty

        // Redeeming the tokens.
        nodeA.startFlow(RedeemFungibleTokens(amount = 10 of myTokenType, issuer = issuer))
        network.runNetwork()

        // Checking if the tokens are redeemed.
        assertThat(nodeA.services.vaultService.tokenAmountsByToken(myTokenType).states).isEmpty()
        assertThat(nodeI.services.vaultService.tokenAmountsByToken(myTokenType).states).isEmpty()
    }

    @Test
    fun `should redeem multiple instances of TokenType`() {
        val issuer: Party = nodeI.legalIdentity()
        val holder: Party = nodeA.legalIdentity()

        val myTokenType = TokenType(tokenIdentifier = "TEST", fractionDigits = 2)
        // Creating an instance of IssuedTokenType with myTokenType token type and nodeI as issuer.
        val myIssuedTokenType: IssuedTokenType = myTokenType issuedBy issuer

        // Creating 10 amount of myIssuedTokenType
        val tenOfMyIssuedTokenType = 10 of myIssuedTokenType

        // Creating 10 amount of btcTokenType
        val tenOfBTC_TokenType = 10.BTC issuedBy issuer

        // Adding a holder to an amount of a token type, creates a fungible token.
        val fungibleToken: FungibleToken = tenOfMyIssuedTokenType heldBy holder

        // Adding a holder to an amount of a token type, creates a fungible token.
        val btcFungibleToken: FungibleToken = tenOfBTC_TokenType heldBy holder

        // Issuing Fungible Tokens
        nodeI.startFlow(IssueTokens(listOf(fungibleToken, btcFungibleToken)))
        network.runNetwork()
        // Checking if the tokens are issued to nodeA.
        assertThat(nodeA.services.vaultService.tokenAmountsByToken(myTokenType).states).isNotEmpty
        assertThat(nodeA.services.vaultService.tokenAmountsByToken(BTC).states).isNotEmpty
        // Redeeming the myTokenType tokens.
        nodeA.startFlow(RedeemFungibleTokens(amount = 10 of myTokenType, issuer = issuer))
        network.runNetwork()
        // Checking if the tokens are redeemed.
        assertThat(nodeA.services.vaultService.tokenAmountsByToken(myTokenType).states).isEmpty()
        assertThat(nodeI.services.vaultService.tokenAmountsByToken(myTokenType).states).isEmpty()
        // Redeeming the bitcoin tokens.
        nodeA.startFlow(RedeemFungibleTokens(amount = 10.BTC, issuer = issuer))
        network.runNetwork()
        // Checking if the tokens are redeemed.
        assertThat(nodeA.services.vaultService.tokenAmountsByToken(BTC).states).isEmpty()
        assertThat(nodeI.services.vaultService.tokenAmountsByToken(BTC).states).isEmpty()

    }

    @Test
    fun `should redeem fungible tokens and broadcast the transaction to an observer`() {
        val issuer: Party = nodeI.legalIdentity()
        val holder: Party = nodeA.legalIdentity()
        val observer: Party = nodeB.legalIdentity()

        // Creating 10 amount of myIssuedTokenType
        val tenOfMyIssuedTokenType = 10.GBP issuedBy issuer

        // Adding a holder to an amount of a token type, creates a fungible token.
        val fungibleToken: FungibleToken = tenOfMyIssuedTokenType heldBy holder

        // Issuing Fungible Token
        nodeI.startFlow(IssueTokens(listOf(fungibleToken), listOf(observer)))
        network.runNetwork()

        // Checking if the tokens are issued to nodeA.
        assertThat(nodeA.services.vaultService.tokenAmountsByToken(GBP).states).isNotEmpty
        assertThat(nodeB.services.vaultService.tokenAmountsByToken(GBP).states).isNotEmpty

        // Redeeming the tokens.
        val amountToRedeem = 10.GBP
        val redeem = nodeA.startFlow(RedeemFungibleTokens(amount = amountToRedeem, issuer = issuer, observers = listOf(observer)))
        network.runNetwork()
        val redeemedTransactionHash = redeem.getOrThrow()

        // Checking if the tokens are redeemed.
        assertThat(nodeA.services.vaultService.tokenAmountsByToken(GBP).states).isEmpty()
        assertThat(nodeB.services.vaultService.tokenAmountsByToken(GBP).states).isEmpty()
        assertThat(nodeI.services.vaultService.tokenAmountsByToken(GBP).states).isEmpty()
        assertHasTransaction(redeemedTransactionHash, network, nodeI, nodeA, nodeB)
    }

    @Test
    fun `should try to redeem fungible tokens by the issuer`() {
        val issuer: Party = nodeI.legalIdentity()
        val holder: Party = nodeA.legalIdentity()

        // Creating 10 amount of myIssuedTokenType
        val tenOfMyIssuedTokenType = 10.GBP issuedBy issuer

        // Adding a holder to an amount of a token type, creates a fungible token.
        val fungibleToken: FungibleToken = tenOfMyIssuedTokenType heldBy holder

        // Issuing Fungible Token
        nodeI.startFlow(IssueTokens(listOf(fungibleToken)))
        network.runNetwork()

        // Checking if the tokens are issued to nodeA.
        assertThat(nodeA.services.vaultService.tokenAmountsByToken(GBP).states).isNotEmpty
        assertThat(nodeI.services.vaultService.tokenAmountsByToken(GBP).states).isEmpty()

        // Redeeming the tokens.
        val amountToRedeem = 10.GBP
        val exception = nodeI.startFlow(RedeemFungibleTokens(amount = amountToRedeem, issuer = issuer))
        network.runNetwork()
        assertFailsWith<InsufficientBalanceException> { exception.getOrThrow() }
    }


    @Test
    fun `should redeem fungible tokens issued confidentially`() {
        val issuer: Party = nodeI.legalIdentity()
        val holder: Party = nodeA.legalIdentity()
        val observer: Party = nodeB.legalIdentity()

        // Creating 10 amount of myIssuedTokenType
        val tenOfMyIssuedTokenType = 10.GBP issuedBy issuer

        // Adding a holder to an amount of a token type, creates a fungible token.
        val fungibleToken: FungibleToken = tenOfMyIssuedTokenType heldBy holder

        // Issuing Fungible Token
        nodeI.startFlow(ConfidentialIssueTokens(listOf(fungibleToken), listOf(observer)))
        network.runNetwork()

        // Checking if the tokens are issued to nodeA.
        assertThat(nodeA.services.vaultService.tokenAmountsByToken(GBP).states).isNotEmpty
        assertThat(nodeB.services.vaultService.tokenAmountsByToken(GBP).states).isNotEmpty
        assertNotEquals(nodeA.services.vaultService.tokenAmountsByToken(GBP).states.single().state.data.holder, nodeA.legalIdentity())

        // Redeeming the tokens.
        val amountToRedeem = 10.GBP
        nodeA.startFlow(RedeemFungibleTokens(amount = amountToRedeem, issuer = issuer))
        network.runNetwork()

        // Checking if the tokens are redeemed.
        assertThat(nodeA.services.vaultService.tokenAmountsByToken(GBP).states).isEmpty()
        assertThat(nodeI.services.vaultService.tokenAmountsByToken(GBP).states).isEmpty()
    }

    @Test
    fun `should redeem fungible tokens moved confidentially`() {
        val issuer: Party = nodeI.legalIdentity()
        val holder: Party = nodeA.legalIdentity()
        val holder2: Party = nodeB.legalIdentity()

        // Creating 10 amount of myIssuedTokenType
        val tenOfMyIssuedTokenType = 10.GBP issuedBy issuer

        // Adding a holder to an amount of a token type, creates a fungible token.
        val fungibleToken: FungibleToken = tenOfMyIssuedTokenType heldBy holder

        // Issuing Fungible Token
        nodeI.startFlow(IssueTokens(listOf(fungibleToken)))
        network.runNetwork()

        // Checking if the tokens are issued to nodeA.
        assertThat(nodeA.services.vaultService.tokenAmountsByToken(GBP).states).isNotEmpty

        // Moving tokens to holder2.
        nodeA.startFlow(ConfidentialMoveFungibleTokens(PartyAndAmount(holder2,10.GBP), listOf(issuer)))
        network.runNetwork()

        // Checking if the tokens are moved to nodeB.
        assertThat(nodeB.services.vaultService.tokenAmountsByToken(GBP).states).isNotEmpty
        assertNotEquals(nodeB.services.vaultService.tokenAmountsByToken(GBP).states.single().state.data.holder, nodeB.legalIdentity())
        assertThat(nodeA.services.vaultService.tokenAmountsByToken(GBP).states).isEmpty()

        // Redeeming the tokens.
        val amountToRedeem = 10.GBP
        nodeB.startFlow(RedeemFungibleTokens(amount = amountToRedeem, issuer = issuer))
        network.runNetwork()

        // Checking if the tokens are redeemed.
        assertThat(nodeB.services.vaultService.tokenAmountsByToken(GBP).states).isEmpty()
        assertThat(nodeI.services.vaultService.tokenAmountsByToken(GBP).states).isEmpty()
    }

    @Test
    fun `should redeem fungible tokens partially`() {
        val issuer: Party = nodeI.legalIdentity()
        val holder: Party = nodeA.legalIdentity()

        // Creating 10 amount of myIssuedTokenType
        val tenOfMyIssuedTokenType = 100.GBP issuedBy issuer

        // Adding a holder to an amount of a token type, creates a fungible token.
        val fungibleToken: FungibleToken = tenOfMyIssuedTokenType heldBy holder

        // Issuing Fungible Token
        nodeI.startFlow(IssueTokens(listOf(fungibleToken)))
        network.runNetwork()

        // Checking if the tokens are issued to nodeA.
        assertThat(nodeA.services.vaultService.tokenAmountsByToken(GBP).states).isNotEmpty

        // Redeeming the tokens.
        val amountToRedeem = 40.GBP
        nodeA.startFlow(RedeemFungibleTokens(amount = amountToRedeem, issuer = issuer))
        network.runNetwork()

        // Checking if the tokens are redeemed.
        assertEquals(nodeA.services.vaultService.tokenBalance(GBP),60.GBP)
        assertThat(nodeI.services.vaultService.tokenAmountsByToken(GBP).states).isEmpty()
    }

    @Test
    fun `should redeem non fungible tokens issued confidentially`() {
        val issuer: Party = nodeI.legalIdentity()
        val holder: Party = nodeA.legalIdentity()

        // Creating an instance of TokenType.
        val myTokenType = TokenType("MyToken", 2)

        // Creating an instance of IssuedTokenType.
        val myIssuedTokenType: IssuedTokenType = myTokenType issuedBy issuer

        // Creating an instance of Non Fungible token.
        val nonFungibleToken: NonFungibleToken = myIssuedTokenType heldBy holder

        // Issuing Non Fungible Token confidentially.
        nodeI.startFlow(ConfidentialIssueTokens(listOf(nonFungibleToken)))
        network.runNetwork()

        // Checking if the tokens are issued to nodeA.
        assertThat(nodeA.services.vaultService.heldTokensByToken(myTokenType).states).isNotEmpty

        // Redeeming the tokens.
        nodeA.startFlow(RedeemNonFungibleTokens(myTokenType, issuer))
        network.runNetwork()

        // Checking if the tokens are redeemed.
        assertThat(nodeA.services.vaultService.heldTokensByToken(myTokenType).states).isEmpty()
        assertThat(nodeA.services.vaultService.heldTokensByToken(myTokenType).states).isEmpty()
    }

    @Test
    fun `should redeem non fungible tokens moved confidentially`() {
        val issuer: Party = nodeI.legalIdentity()
        val holder: Party = nodeA.legalIdentity()
        val holder2: Party = nodeB.legalIdentity()

        // Creating an instance of TokenType.
        val myTokenType = TokenType("MyToken", 2)

        // Creating an instance of IssuedTokenType.
        val myIssuedTokenType: IssuedTokenType = myTokenType issuedBy issuer

        // Creating an instance of Non Fungible token.
        val nonFungibleToken: NonFungibleToken = myIssuedTokenType heldBy holder

        // Issuing Fungible Token
        nodeI.startFlow(IssueTokens(listOf(nonFungibleToken)))
        network.runNetwork()

        // Checking if the token is issued to nodeA.
        assertThat(nodeA.services.vaultService.heldTokensByToken(myTokenType).states).isNotEmpty

        // Moving tokens to holder2.
        nodeA.startFlow(ConfidentialMoveNonFungibleTokens(PartyAndToken(holder2,myTokenType), listOf(issuer)))
        network.runNetwork()

        // Checking if the token is moved to nodeB.
        assertThat(nodeA.services.vaultService.heldTokensByToken(myTokenType).states).isEmpty()
        assertThat(nodeB.services.vaultService.heldTokensByToken(myTokenType).states).isNotEmpty

        // Redeeming the tokens.
        nodeB.startFlow(RedeemNonFungibleTokens(myTokenType, issuer))
        network.runNetwork()

        // Checking if the token is redeemed.
        assertThat(nodeB.services.vaultService.heldTokensByToken(myTokenType).states).isEmpty()
        assertThat(nodeB.services.vaultService.heldTokensByToken(myTokenType).states).isEmpty()
    }
}


