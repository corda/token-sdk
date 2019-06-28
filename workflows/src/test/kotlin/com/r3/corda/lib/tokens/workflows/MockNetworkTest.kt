package com.r3.corda.lib.tokens.workflows

import net.corda.core.identity.CordaX500Name
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.internal.chooseIdentity
import net.corda.testing.node.StartedMockNode
import net.corda.testing.node.TestCordapp
import net.corda.testing.node.internal.InternalMockNetwork
import net.corda.testing.node.internal.TestCordappInternal
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.rules.Timeout
import java.util.concurrent.TimeUnit

abstract class MockNetworkTest(val names: List<CordaX500Name>) {

    @get:Rule
    val timeoutRule = Timeout(5, TimeUnit.MINUTES)

    constructor(vararg names: String) : this(names.map { CordaX500Name(it, "London", "GB") })

    constructor(numberOfNodes: Int) : this(*(1..numberOfNodes).map { "Party${it.toChar() + 64}" }.toTypedArray())

    protected val network = InternalMockNetwork(
            cordappsForAllNodes = listOf(TestCordapp.findCordapp("com.r3.corda.lib.tokens.money"),
                    TestCordapp.findCordapp("com.r3.corda.lib.tokens.contracts"),
                    TestCordapp.findCordapp("com.r3.corda.lib.tokens.workflows"),
                    TestCordapp.findCordapp("com.r3.corda.lib.tokens.testing")) as List<TestCordappInternal>,
            threadPerNode = true,
            initialNetworkParameters = testNetworkParameters(minimumPlatformVersion = 4)
    )

    /** The nodes which makes up the network. */
    protected lateinit var nodes: List<StartedMockNode>
    protected lateinit var nodesByName: Map<Any, StartedMockNode>

    /** Override this to assign each node to a variable for ease of use. */
    @Before
    abstract fun initialiseNodes()

    @Before
    fun setupNetwork() {
        nodes = names.map { convertToStartedMockNode(network.createPartyNode(it)) }
        val nodeMap = LinkedHashMap<Any, StartedMockNode>()
        nodes.forEachIndexed { index, node ->
            nodeMap[index] = node
            nodeMap[node.info.chooseIdentity().name.organisation] = node
        }
        nodesByName = nodeMap
    }

    @After
    fun tearDownNetwork() {
        network.stopNodes()
    }

    protected val NOTARY: StartedMockNode get() = convertToStartedMockNode(network.defaultNotaryNode)
}