package com.r3.corda.sdk.token.workflow

import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.StartedMockNode
import org.junit.After
import org.junit.Before

abstract class JITMockNetworkTests(val names: List<CordaX500Name> = emptyList()) {

    companion object {
        const val DEFAULT_LOCALITY: String = "London"
        const val DEFAULT_COUNTRY: String = "GB"
    }

    constructor(vararg names: String) : this(names.map { CordaX500Name(it, DEFAULT_LOCALITY, DEFAULT_COUNTRY) })

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
    protected val nodes: LinkedHashMap<CordaX500Name, StartedMockNode> = LinkedHashMap()

    fun node(organisation: String, locality: String = DEFAULT_LOCALITY, country: String = DEFAULT_COUNTRY): StartedMockNode {
        return node(CordaX500Name(organisation, locality, country))
    }

    fun node(name: CordaX500Name): StartedMockNode {
        return nodes.getOrPut(name) { network.createPartyNode(name) }
    }

    fun identity(organisation: String, locality: String = DEFAULT_LOCALITY, country: String = DEFAULT_COUNTRY): Party {
        return identity(CordaX500Name(organisation, locality, country))
    }

    fun identity(name: CordaX500Name): Party {
        return nodes.getOrPut(name) { network.createPartyNode(name) }.legalIdentity()
    }

    @Before
    fun startNetwork() {
        network.startNodes()
        names.forEach { node(it) }
    }

    @After
    fun stopNetwork() {
        network.stopNodes()
    }

    fun restartNetwork() {
        stopNetwork()
        startNetwork()
    }

    protected val notary: StartedMockNode get() = network.defaultNotaryNode

    protected val notaryIdentity: Party get() = notary.legalIdentity()

}