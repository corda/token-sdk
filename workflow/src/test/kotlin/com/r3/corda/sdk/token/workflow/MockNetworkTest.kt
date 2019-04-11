package com.r3.corda.sdk.token.workflow

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.sdk.token.contracts.states.EvolvableTokenType
import com.r3.corda.sdk.token.contracts.types.TokenType
import com.r3.corda.sdk.token.contracts.utilities.withNotary
import com.r3.corda.sdk.token.workflow.flows.CreateEvolvableToken
import com.r3.corda.sdk.token.workflow.flows.IssueToken
import com.r3.corda.sdk.token.workflow.flows.MoveToken
import com.r3.corda.sdk.token.workflow.flows.RedeemToken
import com.r3.corda.sdk.token.workflow.flows.RequestConfidentialIdentity
import com.r3.corda.sdk.token.workflow.flows.UpdateEvolvableToken
import net.corda.core.concurrent.CordaFuture
import net.corda.core.contracts.Amount
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.LinearState
import net.corda.core.contracts.StateAndRef
import net.corda.core.crypto.SecureHash
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.flows.InitiatedBy
import net.corda.core.flows.InitiatingFlow
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.transactions.SignedTransaction
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
            issueTo: StartedMockNode,
            notary: StartedMockNode,
            amount: Amount<T>? = null,
            anonymous: Boolean = true
    ): CordaFuture<SignedTransaction> {
        return transaction {
            if (anonymous) {
                startFlow(ConfidentialIssueFlow.Initiator(
                        token = token,
                        holder = issueTo.legalIdentity(),
                        notary = notary.legalIdentity(),
                        amount = amount
                ))
            } else {
                startFlow(IssueToken.Initiator(
                        token = token,
                        issueTo = issueTo.legalIdentity(),
                        notary = notary.legalIdentity(),
                        amount = amount
                ))
            }
        }
    }

    fun <T : TokenType> StartedMockNode.moveTokens(
            token: T,
            owner: StartedMockNode,
            amount: Amount<T>? = null,
            anonymous: Boolean = true
    ): CordaFuture<SignedTransaction> {
        return transaction {
            if (anonymous) {
                startFlow(ConfidentialMoveFlow.Initiator(
                        ownedToken = token,
                        holder = owner.legalIdentity(),
                        amount = amount
                ))
            } else {
                startFlow(MoveToken.Initiator(
                        ownedToken = token,
                        holder = owner.legalIdentity(),
                        amount = amount
                ))
            }
        }

    }

    fun <T : TokenType> StartedMockNode.redeemTokens(
            token: T,
            issuer: StartedMockNode,
            amount: Amount<T>? = null,
            anonymous: Boolean = true
    ): CordaFuture<SignedTransaction> {
        return startFlow(RedeemToken.InitiateRedeem(
                ownedToken = token,
                issuer = issuer.legalIdentity(),
                amount = amount,
                anonymous = anonymous
        ))
    }

    object ConfidentialIssueFlow {
        @InitiatingFlow
        class Initiator<T : TokenType>(
                val token: T,
                val holder: Party,
                val notary: Party,
                val amount: Amount<T>? = null
        ) : FlowLogic<SignedTransaction>() {
            @Suspendable
            override fun call(): SignedTransaction {
                val holderSession = initiateFlow(holder)
                val confidentialHolder = subFlow(RequestConfidentialIdentity.Initiator(holderSession)).party.anonymise()
                return subFlow(IssueToken.Initiator(token, confidentialHolder, notary, amount, holderSession))
            }
        }

        @InitiatedBy(Initiator::class)
        class Responder(val otherSession: FlowSession) : FlowLogic<Unit>() {
            @Suspendable
            override fun call() {
                subFlow(RequestConfidentialIdentity.Responder(otherSession))
                subFlow(IssueToken.Responder(otherSession))
            }
        }
    }

    object ConfidentialMoveFlow {
        @InitiatingFlow
        class Initiator<T : TokenType>(
                val ownedToken: T,
                val holder: Party,
                val amount: Amount<T>? = null
        ) : FlowLogic<SignedTransaction>() {
            @Suspendable
            override fun call(): SignedTransaction {
                val holderSession = initiateFlow(holder)
                val confidentialHolder = subFlow(RequestConfidentialIdentity.Initiator(holderSession)).party.anonymise()
                return subFlow(MoveToken.Initiator(ownedToken, confidentialHolder, amount, holderSession))
            }
        }

        @InitiatedBy(Initiator::class)
        class Responder(val otherSession: FlowSession) : FlowLogic<Unit>() {
            @Suspendable
            override fun call() {
                subFlow(RequestConfidentialIdentity.Responder(otherSession))
                subFlow(MoveToken.Responder(otherSession))
            }
        }
    }
}