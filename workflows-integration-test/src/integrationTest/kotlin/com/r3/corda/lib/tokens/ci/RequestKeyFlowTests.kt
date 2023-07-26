package com.r3.corda.lib.tokens.ci

import com.r3.corda.lib.ci.workflows.RequestKey
import com.r3.corda.lib.ci.workflows.RequestKeyForUUIDInitiator
import com.r3.corda.lib.ci.workflows.VerifyAndAddKey
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
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RequestKeyFlowTests {

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
        mockNet.stopNodes()
    }

    @Test
    fun `request a new key`() {
        // Alice requests that bob generates a new key for an account
        val anonymousParty = aliceNode.startFlow(RequestKey(bob)).getOrThrow()

        // Bob has the newly generated key as well as the owning key
        val bobKeys = bobNode.services.keyManagementService.keys
        val aliceKeys = aliceNode.services.keyManagementService.keys
        assertThat(bobKeys).hasSize(2)
        assertThat(aliceKeys).hasSize(1)

        // Check that bob stored the key as expected.
        assertEquals(listOf(anonymousParty.owningKey), bobNode.services.keyManagementService.filterMyKeys(listOf(anonymousParty.owningKey)))

        val resolvedBobParty = aliceNode.services.identityService.wellKnownPartyFromAnonymous(anonymousParty)
        assertThat(resolvedBobParty).isEqualTo(bob)
    }

    @Test
    fun `request new key with a uuid provided`() {
        // Alice requests that bob generates a new key for an account
        val anonymousParty = aliceNode.startFlow(RequestKeyForUUIDInitiator(bob, UUID.randomUUID())).getOrThrow()

        // Bob has the newly generated key as well as the owning key
        val bobKeys = bobNode.services.keyManagementService.keys
        val aliceKeys = aliceNode.services.keyManagementService.keys
        assertThat(bobKeys).hasSize(2)
        assertThat(aliceKeys).hasSize(1)

        // Check that bob stored the key as expected.
        assertEquals(listOf(anonymousParty.owningKey), bobNode.services.keyManagementService.filterMyKeys(listOf(anonymousParty.owningKey)))

        val partyOnAlice = aliceNode.services.identityService.wellKnownPartyFromAnonymous(anonymousParty)
        assertThat(bob).isEqualTo(partyOnAlice)

        val partyOnBob = bobNode.services.identityService.wellKnownPartyFromAnonymous(anonymousParty)
        assertThat(bob).isEqualTo(partyOnBob)
    }

    @Test
    fun `verify a known key with another party`() {
        // Charlie issues then pays some cash to a new confidential identity
        val anonymousParty = charlieNode.startFlow(RequestKey(alice)).getOrThrow()

        val issueTx = charlieNode.startFlow(
                IssueTokens(listOf(1000 of USD issuedBy charlie heldBy anonymousParty))
        ).getOrThrow()
        val confidentialIdentity = issueTx.tx.outputs.map { it.data }.filterIsInstance<FungibleToken>().single().holder

        // Verify Bob cannot resolve the CI before we create a signed mapping of the CI key
        assertNull(bobNode.transaction { bobNode.services.identityService.wellKnownPartyFromAnonymous(confidentialIdentity) })

        // Request a new key mapping for the CI
       bobNode.startFlow(VerifyAndAddKey(alice, confidentialIdentity.owningKey)).getOrThrow()

        val expected = charlieNode.transaction {
            charlieNode.services.identityService.wellKnownPartyFromAnonymous(confidentialIdentity)
        }
        val actual = bobNode.transaction {
            bobNode.services.identityService.wellKnownPartyFromAnonymous(confidentialIdentity)
        }
        assertEquals(expected, actual)
    }
}
