package com.r3.corda.lib.tokens.ci

import com.r3.corda.lib.ci.workflows.RequestKey
import com.r3.corda.lib.ci.workflows.SyncKeyMappingInitiator
import com.r3.corda.lib.ci.workflows.SyncKeyMappingResponder
import com.r3.corda.lib.ci.workflows.VerifyAndAddKey
import com.r3.corda.lib.tokens.contracts.states.FungibleToken
import com.r3.corda.lib.tokens.contracts.utilities.heldBy
import com.r3.corda.lib.tokens.contracts.utilities.issuedBy
import com.r3.corda.lib.tokens.contracts.utilities.of
import com.r3.corda.lib.tokens.money.GBP
import com.r3.corda.lib.tokens.workflows.flows.rpc.ConfidentialIssueTokens
import net.corda.core.identity.CordaX500Name
import net.corda.core.messaging.startFlow
import net.corda.core.utilities.getOrThrow
import net.corda.node.services.Permissions
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.core.BOB_NAME
import net.corda.testing.core.CHARLIE_NAME
import net.corda.testing.core.singleIdentity
import net.corda.testing.driver.DriverDSL
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.NodeHandle
import net.corda.testing.driver.driver
import net.corda.testing.node.TestCordapp
import net.corda.testing.node.User
import org.junit.Test
import java.util.concurrent.Future
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DriverBasedTest {

    @Test
    fun `request a key mapping for a confidential identity`() = withDriver {
        val aUser = User("aUser", "testPassword1", permissions = setOf(Permissions.all()))
        val bUser = User("bUser", "testPassword2", permissions = setOf(Permissions.all()))
        val cUser = User("cUser", "testPassword3", permissions = setOf(Permissions.all()))
        val (nodeA, nodeB, nodeC) = listOf(
                startNode(providedName = ALICE_NAME, rpcUsers = listOf(aUser)),
                startNode(providedName = BOB_NAME, rpcUsers = listOf(bUser)),
                startNode(providedName = CHARLIE_NAME, rpcUsers = listOf(cUser))
        ).waitForAll()

        verifyNodesResolve(nodeA, nodeB, nodeC)

        // Charlie issues then pays some cash to a new confidential identity that Bob doesn't know about
        val anonymousParty = nodeC.rpc.startFlow(::RequestKey, nodeA.nodeInfo.singleIdentity()).returnValue.getOrThrow()

        val token = 1000 of GBP issuedBy nodeC.nodeInfo.singleIdentity() heldBy anonymousParty
        val issueTx  = nodeC.rpc.startFlow(::ConfidentialIssueTokens, listOf(token), emptyList()).returnValue.getOrThrow()

        val ci = issueTx.tx.outputs.map { it.data }.filterIsInstance<FungibleToken>().single().holder

        // Verify Bob cannot resolve the CI before we create a key mapping
        assertNull(nodeB.rpc.wellKnownPartyFromAnonymous(ci))

        // Request a new key mapping for the CI
        nodeB.rpc.startFlow(::VerifyAndAddKey, nodeA.nodeInfo.singleIdentity(), ci.owningKey).let {
            it.returnValue
        }.getOrThrow()

        val expected = nodeC.rpc.wellKnownPartyFromAnonymous(ci)

        val actual = nodeB.rpc.wellKnownPartyFromAnonymous(ci)

        assertEquals(expected, actual)
    }

    @Test
    fun `sync confidential identities within a transaction`() = withDriver {
        val aUser = User("aUser", "testPassword1", permissions = setOf(Permissions.all()))
        val bUser = User("bUser", "testPassword2", permissions = setOf(Permissions.all()))
        val cUser = User("cUser", "testPassword3", permissions = setOf(Permissions.all()))

        val (nodeA, nodeB, nodeC) = listOf(
                startNode(providedName = ALICE_NAME, rpcUsers = listOf(aUser)),
                startNode(providedName = BOB_NAME, rpcUsers = listOf(bUser)),
                startNode(providedName = CHARLIE_NAME, rpcUsers = listOf(cUser))
        ).waitForAll()

        val anonymousParty = nodeC.rpc.startFlow(::RequestKey, nodeA.nodeInfo.singleIdentity()).returnValue.getOrThrow()

        val token = 1000 of GBP issuedBy nodeC.nodeInfo.singleIdentity() heldBy anonymousParty
        val issueTx  = nodeC.rpc.startFlow(::ConfidentialIssueTokens, listOf(token), emptyList()).returnValue.getOrThrow()

        val ci = issueTx.tx.outputs.map { it.data }.filterIsInstance<FungibleToken>().single().holder

        assertNull(nodeB.rpc.wellKnownPartyFromAnonymous(ci))

        val idFuture = watchForFlowAdded(nodeB, SyncKeyMappingResponder::class.java.name)
        val anonFuture = nodeC.rpc.startFlow(::SyncKeyMappingInitiator, nodeB.nodeInfo.singleIdentity(), issueTx.tx)
        waitForFlowToBeRemovedThenComplete(idFuture, anonFuture, nodeB)

        val expected = nodeC.rpc.wellKnownPartyFromAnonymous(ci)
        val actual = nodeB.rpc.wellKnownPartyFromAnonymous(ci)

        assertEquals(expected, actual)
    }


    @Test
    fun `sync list of confidential identities`() = withDriver {
        val aUser = User("aUser", "testPassword1", permissions = setOf(Permissions.all()))
        val bUser = User("bUser", "testPassword2", permissions = setOf(Permissions.all()))
        val cUser = User("cUser", "testPassword3", permissions = setOf(Permissions.all()))

        val (nodeA, nodeB, nodeC) = listOf(
                startNode(providedName = ALICE_NAME, rpcUsers = listOf(aUser)),
                startNode(providedName = BOB_NAME, rpcUsers = listOf(bUser)),
                startNode(providedName = CHARLIE_NAME, rpcUsers = listOf(cUser))
        ).waitForAll()

        // Charlie creates two new confidential identities
        val anonymousAlice = nodeC.rpc.startFlow(::RequestKey, nodeA.nodeInfo.singleIdentity()).returnValue.getOrThrow()
        val anonymousCharlie =nodeC.rpc.startFlow(::RequestKey, nodeC.nodeInfo.singleIdentity()).returnValue.getOrThrow()

        assertNull(nodeB.rpc.wellKnownPartyFromAnonymous(anonymousAlice))
        assertNull(nodeB.rpc.wellKnownPartyFromAnonymous(anonymousCharlie))

        val idFuture = watchForFlowAdded(nodeB, SyncKeyMappingResponder::class.java.name)
        val flowFuture = nodeC.rpc.startFlow(::SyncKeyMappingInitiator, nodeB.nodeInfo.singleIdentity(), listOf(anonymousAlice, anonymousCharlie))
        waitForFlowToBeRemovedThenComplete(idFuture, flowFuture, nodeB)

        val expectedAlice = nodeB.rpc.wellKnownPartyFromAnonymous(anonymousAlice)
        val expectedCharlie = nodeB.rpc.wellKnownPartyFromAnonymous(anonymousCharlie)

        assertEquals(nodeA.nodeInfo.singleIdentity(), expectedAlice)
        assertEquals(nodeC.nodeInfo.singleIdentity(), expectedCharlie)
    }

    // Runs a test inside the Driver DSL, which provides useful functions for starting nodes, etc.
    private fun withDriver(test: DriverDSL.() -> Unit) = driver(
        DriverParameters(
            isDebug = true,
            startNodesInProcess = false,
            cordappsForAllNodes = listOf(
                TestCordapp.findCordapp("com.r3.corda.lib.tokens.workflows"),
                TestCordapp.findCordapp("com.r3.corda.lib.tokens.contracts"),
                TestCordapp.findCordapp("com.r3.corda.lib.tokens.money"),
                    TestCordapp.findCordapp("com.r3.corda.lib.ci.workflows")
            )
        )
    ) { test() }

    // Makes an RPC call to retrieve another node's name from the network map.
    private fun NodeHandle.resolveName(name: CordaX500Name) = rpc.wellKnownPartyFromX500Name(name)!!.name

    // Resolves a list of futures to a list of the promised values.
    private fun <T> List<Future<T>>.waitForAll(): List<T> = map { it.getOrThrow() }

    private fun verifyNodesResolve(nodeA: NodeHandle, nodeB: NodeHandle, nodeC: NodeHandle) {
        assertEquals(BOB_NAME, nodeA.resolveName(BOB_NAME))
        assertEquals(CHARLIE_NAME, nodeA.resolveName(CHARLIE_NAME))

        assertEquals(ALICE_NAME, nodeB.resolveName(ALICE_NAME))
        assertEquals(CHARLIE_NAME, nodeB.resolveName(CHARLIE_NAME))

        assertEquals(ALICE_NAME, nodeC.resolveName(ALICE_NAME))
        assertEquals(BOB_NAME, nodeC.resolveName(BOB_NAME))
    }
}
