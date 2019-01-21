package net.corda.sdk.token

import net.corda.core.concurrent.CordaFuture
import net.corda.core.contracts.Amount
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.LinearState
import net.corda.core.contracts.StateAndRef
import net.corda.core.crypto.SecureHash
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.toFuture
import net.corda.core.transactions.SignedTransaction
import net.corda.sdk.token.flows.CreateEvolvableToken
import net.corda.sdk.token.flows.IssueToken
import net.corda.sdk.token.flows.UpdateEvolvableToken
import net.corda.sdk.token.types.EmbeddableToken
import net.corda.sdk.token.types.EvolvableToken
import net.corda.sdk.token.utilities.withNotary
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.StartedMockNode
import org.junit.After
import org.junit.Before

abstract class MockNetworkTest(val numberOfNodes: Int) {

    protected val network = MockNetwork(
            cordappPackages = listOf("net.corda.sdk.token"),
            threadPerNode = true,
            networkParameters = testNetworkParameters(minimumPlatformVersion = 4)
    )

    /** The nodes which makes up the network. */
    protected lateinit var nodes: List<StartedMockNode>

    /** Override this to assign each node to a variable for ease of use. */
    @Before
    abstract fun initialiseNodes()

    @Before
    fun setupNetwork() {
        nodes = createSomeNodes(numberOfNodes)
    }

    @After
    fun tearDownNetwork() {
        network.stopNodes()
    }

    private fun createSomeNodes(numberOfNodes: Int = 2): List<StartedMockNode> {
        val partyNodes = (1..numberOfNodes).map { current ->
            val char = current.toChar() + 64
            val name = CordaX500Name("Party$char", "London", "GB")
            network.createPartyNode(name)
        }
        return partyNodes
    }

    fun StartedMockNode.legalIdentity() = services.myInfo.legalIdentities.first()

    protected val NOTARY: StartedMockNode get() = network.defaultNotaryNode

    /** From a transaction which produces a single output, retrieve that output. */
    inline fun <reified T : ContractState> SignedTransaction.singleOutput() = tx.outRefsOfType<T>().single()

    /** Gets the linearId from a LinearState. */
    inline fun <reified T : LinearState> StateAndRef<T>.linearId() = state.data.linearId

    /** Check to see if a node recorded a transaction with a particular hash. Return a future signed transaction. */
    fun StartedMockNode.watchForTransaction(txId: SecureHash): CordaFuture<SignedTransaction> {
        return transaction { services.validatedTransactions.updates.filter { it.id == txId }.toFuture() }
    }

    /** Create an evolvable token. */
    fun <T : EvolvableToken> StartedMockNode.createEvolvableToken(evolvableToken: T, notary: Party): CordaFuture<SignedTransaction> {
        return transaction { startFlow(CreateEvolvableToken(transactionState = evolvableToken withNotary notary)) }
    }

    /** Update an evolvable token. */
    fun <T : EvolvableToken> StartedMockNode.updateEvolvableToken(old: StateAndRef<T>, new: T): CordaFuture<SignedTransaction> {
        return transaction { startFlow(UpdateEvolvableToken(old = old, new = new)) }
    }

    fun <T : EmbeddableToken> StartedMockNode.issueToken(
            embeddableToken: T,
            owner: StartedMockNode,
            notary: StartedMockNode,
            amount: Amount<T>? = null,
            anonymous: Boolean = true
    ): CordaFuture<SignedTransaction> {
        return transaction {
            startFlow(IssueToken.Initiator(
                    token = embeddableToken,
                    owner = owner.legalIdentity(),
                    notary = notary.legalIdentity(),
                    amount = amount,
                    anonymous = anonymous
            ))
        }
    }

}