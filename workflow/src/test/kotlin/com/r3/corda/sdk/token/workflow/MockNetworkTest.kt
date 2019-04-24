package com.r3.corda.sdk.token.workflow

import net.corda.core.identity.CordaX500Name
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.internal.chooseIdentity
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.StartedMockNode
import org.junit.After
import org.junit.Before

abstract class MockNetworkTest(val names: List<CordaX500Name>) {

    constructor(vararg names: String) : this(names.map { CordaX500Name(it, "London", "GB") })

    constructor(numberOfNodes: Int) : this(*(1..numberOfNodes).map { "Party${it.toChar() + 64}" }.toTypedArray())

    protected val network = MockNetwork(
            cordappPackages = listOf(
                    "com.r3.corda.sdk.token.money",
                    "com.r3.corda.sdk.token.contracts",
                    "com.r3.corda.sdk.token.workflow"
            ),
            threadPerNode = true,
            networkParameters = testNetworkParameters(minimumPlatformVersion = 4)
    )

    /** The nodes which makes up the network. */
    protected lateinit var nodes: List<StartedMockNode>
    protected lateinit var nodesByName: Map<Any, StartedMockNode>

    /** Override this to assign each node to a variable for ease of use. */
    @Before
    abstract fun initialiseNodes()

    @Before
    fun setupNetwork() {
        nodes = names.map { network.createPartyNode(it) }

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

    protected val NOTARY: StartedMockNode get() = network.defaultNotaryNode

}