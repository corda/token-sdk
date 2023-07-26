package com.r3.corda.lib.tokens.ci

import com.r3.corda.lib.ci.workflows.RequestKey
import com.r3.corda.lib.ci.workflows.SyncKeyMappingInitiator
import com.r3.corda.lib.tokens.contracts.states.FungibleToken
import com.r3.corda.lib.tokens.contracts.utilities.heldBy
import com.r3.corda.lib.tokens.contracts.utilities.issuedBy
import com.r3.corda.lib.tokens.contracts.utilities.of
import com.r3.corda.lib.tokens.money.USD
import com.r3.corda.lib.tokens.workflows.flows.rpc.IssueTokens
import net.corda.core.identity.Party
import net.corda.core.utilities.getOrThrow
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.core.BOB_NAME
import net.corda.testing.core.CHARLIE_NAME
import net.corda.testing.core.singleIdentity
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.MockNetworkParameters
import net.corda.testing.node.StartedMockNode
import net.corda.testing.node.TestCordapp
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SyncKeyMappingFlowTests {

    private lateinit var mockNet: MockNetwork
    private lateinit var aliceNode: StartedMockNode
    private lateinit var bobNode: StartedMockNode
    private lateinit var charlieNode: StartedMockNode
    private lateinit var alice: Party
    private lateinit var bob: Party
    private lateinit var charlie: Party
    private lateinit var notary: Party

    @Before
    fun before() {
        mockNet = MockNetwork(
                MockNetworkParameters(
                        networkParameters = testNetworkParameters(minimumPlatformVersion = 4),
                        cordappsForAllNodes = listOf(
                                TestCordapp.findCordapp("com.r3.corda.lib.tokens.contracts"),
                                TestCordapp.findCordapp("com.r3.corda.lib.tokens.workflows"),
                                TestCordapp.findCordapp("com.r3.corda.lib.tokens.money"),
                                TestCordapp.findCordapp("com.r3.corda.lib.ci.workflows")
                        ),
                        threadPerNode = true

                )
        )
        aliceNode = mockNet.createPartyNode(ALICE_NAME)
        bobNode = mockNet.createPartyNode(BOB_NAME)
        charlieNode = mockNet.createPartyNode(CHARLIE_NAME)
        alice = aliceNode.info.singleIdentity()
        bob = bobNode.info.singleIdentity()
        charlie = charlieNode.info.singleIdentity()
        notary = mockNet.defaultNotaryIdentity

        mockNet.startNodes()
    }

    @After
    fun after() {
        // Mysterious KryoException thrown here so having to use this as a workaround
        try {
            mockNet.stopNodes()
        } catch (e: Exception) {
            println(e.localizedMessage)
        }
    }

    @Test
    fun `sync the key mapping between two parties in a transaction`() {
        // Alice issues then pays some cash to a new confidential identity that Bob doesn't know about
        val anonymousParty = aliceNode.startFlow(RequestKey(charlie)).let { it ->
            it.getOrThrow()
        }

        val issueTx = aliceNode.startFlow(IssueTokens(listOf(1000 of USD issuedBy alice heldBy anonymousParty))).let {
            it.getOrThrow()
        }

        val confidentialIdentity = issueTx.tx.outputs.map { it.data }.filterIsInstance<FungibleToken>().single().holder

        assertNull(bobNode.transaction { bobNode.services.identityService.wellKnownPartyFromAnonymous(confidentialIdentity) })

        // Run the flow to sync up the identities
        aliceNode.startFlow(SyncKeyMappingInitiator(bob, issueTx.tx)).let {
            mockNet.waitQuiescent()
            it.getOrThrow()
        }

        val expected = aliceNode.transaction {
            aliceNode.services.identityService.wellKnownPartyFromAnonymous(confidentialIdentity)
        }
        val actual = bobNode.transaction {
            bobNode.services.identityService.wellKnownPartyFromAnonymous(confidentialIdentity)
        }
        assertEquals(expected, actual)
    }

    @Test
    fun `sync identities without a transaction`() {
        val anonymousAlice = aliceNode.startFlow(RequestKey(alice)).let {
            it.getOrThrow()
        }

        val anonymousCharlie = aliceNode.startFlow(RequestKey(charlie)).let {
            it.getOrThrow()
        }

        assertNull(bobNode.transaction { bobNode.services.identityService.wellKnownPartyFromAnonymous(anonymousAlice) })
        assertNull(bobNode.transaction { bobNode.services.identityService.wellKnownPartyFromAnonymous(anonymousCharlie) })

        // Run the flow to sync up the identities
        aliceNode.startFlow(SyncKeyMappingInitiator(bob, listOf(anonymousAlice, anonymousCharlie))).let {
            mockNet.waitQuiescent()
            it.getOrThrow()
        }

        assertEquals(alice, bobNode.services.identityService.wellKnownPartyFromAnonymous(anonymousAlice))
        assertEquals(charlie, bobNode.services.identityService.wellKnownPartyFromAnonymous(anonymousCharlie))
    }
}


