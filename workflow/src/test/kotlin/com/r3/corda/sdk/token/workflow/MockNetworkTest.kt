package com.r3.corda.sdk.token.workflow

import com.r3.corda.sdk.token.contracts.states.EvolvableTokenType
import com.r3.corda.sdk.token.contracts.types.TokenType
import com.r3.corda.sdk.token.contracts.utilities.withNotary
import com.r3.corda.sdk.token.workflow.flows.CreateEvolvableToken
import com.r3.corda.sdk.token.workflow.flows.IssueToken
import com.r3.corda.sdk.token.workflow.flows.MoveToken
import com.r3.corda.sdk.token.workflow.flows.UpdateEvolvableToken
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
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.internal.chooseIdentity
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.StartedMockNode
import org.junit.After
import org.junit.Before
import java.util.concurrent.TimeUnit

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

    fun StartedMockNode.legalIdentity() = services.myInfo.legalIdentities.first()

    protected val NOTARY: StartedMockNode get() = network.defaultNotaryNode

    /** From a transaction which produces a single output, retrieve that output. */
    inline fun <reified T : ContractState> SignedTransaction.singleOutput() = tx.outRefsOfType<T>().single()

    /** Gets the linearId from a LinearState. */
    inline fun <reified T : LinearState> StateAndRef<T>.linearId() = state.data.linearId

    /** Check to see if a node recorded a transaction with a particular hash. Return a future signed transaction. */
    fun StartedMockNode.watchForTransaction(txId: SecureHash): CordaFuture<SignedTransaction> {
        return transaction { services.validatedTransactions.trackTransaction(txId) }
    }

    fun StartedMockNode.watchForTransaction(tx: SignedTransaction): CordaFuture<SignedTransaction> {
        return watchForTransaction(tx.id)
    }

    /** Create an evolvable token. */
    fun <T : EvolvableTokenType> StartedMockNode.createEvolvableToken(evolvableToken: T, notary: Party): CordaFuture<SignedTransaction> {
        return transaction { startFlow(CreateEvolvableToken.Initiator(transactionState = evolvableToken withNotary notary)) }
    }

    /** Update an evolvable token. */
    fun <T : EvolvableTokenType> StartedMockNode.updateEvolvableToken(old: StateAndRef<T>, new: T): CordaFuture<SignedTransaction> {
        return transaction { startFlow(UpdateEvolvableToken(old = old, new = new)) }
    }

    fun <T : TokenType> StartedMockNode.issueTokens(
            token: T,
            owner: StartedMockNode,
            notary: StartedMockNode,
            amount: Amount<T>? = null,
            anonymous: Boolean = true
    ): CordaFuture<SignedTransaction> {
        return transaction {
            startFlow(IssueToken.Initiator(
                    token = token,
                    owner = owner.legalIdentity(),
                    notary = notary.legalIdentity(),
                    amount = amount,
                    anonymous = anonymous
            ))
        }
    }

    fun <T : TokenType> StartedMockNode.moveTokens(
            token: T,
            owner: StartedMockNode,
            amount: Amount<T>? = null,
            anonymous: Boolean = true
    ): CordaFuture<SignedTransaction> {
        return transaction {
            startFlow(MoveToken.Initiator(
                    ownedToken = token,
                    owner = owner.legalIdentity(),
                    amount = amount,
                    anonymous = anonymous
            ))
        }

    }

}