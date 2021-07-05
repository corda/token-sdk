package com.r3.corda.lib.tokens.workflows

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.lib.tokens.contracts.commands.Create
import com.r3.corda.lib.tokens.contracts.states.EvolvableTokenType
import com.r3.corda.lib.tokens.testing.states.TestEvolvableTokenType
import com.r3.corda.lib.tokens.workflows.factories.TestEvolvableTokenTypeFactory
import net.corda.core.node.services.queryBy
import net.corda.core.utilities.getOrThrow
import net.corda.testing.node.StartedMockNode
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import com.r3.corda.lib.tokens.testing.states.House
import com.r3.corda.lib.tokens.workflows.flows.rpc.CreateEvolvableTokens
import com.r3.corda.lib.tokens.contracts.utilities.withNotary
import com.r3.corda.lib.tokens.workflows.flows.rpc.UpdateEvolvableToken
import com.r3.corda.lib.tokens.money.GBP
import com.r3.corda.lib.tokens.testing.states.FakeEvolvableTokenType
import com.r3.corda.lib.tokens.workflows.flows.evolvable.CreateEvolvableTokensFlow
import com.r3.corda.lib.tokens.workflows.flows.evolvable.CreateEvolvableTokensFlowHandler
import net.corda.core.contracts.*
import net.corda.core.flows.*
import kotlin.test.assertFailsWith
import net.corda.core.identity.Party
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.fail

class CreateEvolvableTokenTests : JITMockNetworkTests() {

    private val alice: StartedMockNode get() = node("Alice")
    private val bob: StartedMockNode get() = node("Bob")
    private val charlie: StartedMockNode get() = node("Charlie")
    private val denise: StartedMockNode get() = node("Denise")

    /**
     * Helper factory for assembling [EvolvableTokenType]s reliably.
     */
    private val factory = TestEvolvableTokenTypeFactory(this, "Alice", "Bob", "Charlie", "Denise")

    /**
     * An evolvable token should be creatable with just a maintainer.
     * Maintainers is equal to participants.
     */
    @Test
    fun `with 1 maintainer`() {
        val token = factory.withOneMaintainer()

        val createTx = alice.createEvolvableToken(token, notaryIdentity).getOrThrow()
        val createdToken = createTx.singleOutput<TestEvolvableTokenType>()

        // Expect proposed and created tokens to match
        assertEquals(token, createdToken.state.data, "Original token did not match the published token.")

        // Expect to have one create command with maintainer signature
        val maintainerKeys = token.maintainers.map { it.owningKey }.toSet()
        assertEquals(maintainerKeys, createTx.requiredSigningKeys, "Must be signed by all maintainers")

        // Only Alice should record the transaction
        assertHasTransaction(createTx, alice)
    }

    /**
     * An evolvable token should be creatable with two maintainers. Both must sign.
     * Maintainers is equal to participants.
     */
    @Test
    fun `with 2 maintainers`() {
        val token = factory.withTwoMaintainers()

        val createTx = alice.createEvolvableToken(token, notaryIdentity).getOrThrow()
        val createdToken = createTx.singleOutput<TestEvolvableTokenType>()

        // Expect proposed and created tokens to match
        assertEquals(token, createdToken.state.data, "Original token did not match the published token.")

        // Expect to have one create command with maintainer signature
        val maintainerKeys = token.maintainers.map { it.owningKey }.toSet()
        assertEquals(maintainerKeys, createTx.requiredSigningKeys, "Must be signed by all maintainers")

        // Alice and Bob should record the transaction
        assertHasTransaction(createTx, alice, bob)
    }

    /**
     * An evolvable token should be creatable with a maintainer and an additional participant. Only maintainers must sign.
     * Participants is a superset of maintainers.
     */
    @Test
    fun `with 1 maintainer and 1 additional participant`() {
        val token = factory.withOneMaintainerAndOneParticipant()

        val createTx = alice.createEvolvableToken(token, notaryIdentity).getOrThrow()
        val createdToken = createTx.singleOutput<TestEvolvableTokenType>()

        // Expect proposed and created tokens to match
        assertEquals(token, createdToken.state.data, "Original token did not match the published token.")

        // Expect to have one create command with maintainer signature
        val maintainerKeys = token.maintainers.map { it.owningKey }.toSet()
        assertEquals(maintainerKeys, createTx.requiredSigningKeys, "Must be signed by all maintainers")

        // Alice and Charlie should record the transaction
        assertHasTransaction(createTx, alice, charlie)
    }

    /**
     * An evolvable token should be creatable with a maintainer and two additional participants. Only maintainers must sign.
     * Participants is a superset of maintainers.
     */
    @Test
    fun `with 1 maintainer and 2 additional participants`() {
        val token = factory.withOneMaintainerAndTwoParticipants()

        val createTx = alice.createEvolvableToken(token, notaryIdentity).getOrThrow()
        val createdToken = createTx.singleOutput<TestEvolvableTokenType>()

        // Expect proposed and created tokens to match
        assertEquals(token, createdToken.state.data, "Original token did not match the published token.")

        // Expect to have one create command with maintainer signature
        val maintainerKeys = token.maintainers.map { it.owningKey }.toSet()
        assertEquals(maintainerKeys, createTx.requiredSigningKeys, "Must be signed by all maintainers")

        // Alice, Charlie, and Denise should record the transaction
        assertHasTransaction(createTx, alice, charlie, denise)
    }

    /**
     * An evolvable token should be creatable with two maintainers and an additional participant. Only maintainers must sign.
     * Participants is a superset of maintainers.
     */
    @Test
    fun `with 2 maintainers and 1 additional participant`() {
        val token = factory.withTwoMaintainersAndOneParticipant()

        val createTx = alice.createEvolvableToken(token, notaryIdentity).getOrThrow()
        val createdToken = createTx.singleOutput<TestEvolvableTokenType>()

        // Expect proposed and created tokens to match
        assertEquals(token, createdToken.state.data, "Original token did not match the published token.")

        // Expect to have one create command with maintainer signature
        val maintainerKeys = token.maintainers.map { it.owningKey }.toSet()
        assertEquals(maintainerKeys, createTx.requiredSigningKeys, "Must be signed by all maintainers")

        // Alice, Bob, and Charlie should record the transaction
        assertHasTransaction(createTx, alice, bob, charlie)
    }

    /**
     * An evolvable token should be creatable with two maintainers and two additional participants. Only maintainers must sign.
     * Participants is a superset of maintainers.
     */
    @Test
    fun `with 2 maintainers and 2 additional participants`() {
        val token = factory.withTwoMaintainersAndTwoParticipants()

        val createTx = alice.createEvolvableToken(token, notaryIdentity).getOrThrow()
        val createdToken = createTx.singleOutput<TestEvolvableTokenType>()

        // Expect proposed and created tokens to match
        assertEquals(token, createdToken.state.data, "Original token did not match the published token.")

        // Expect to have one create command with maintainer signature
        val maintainerKeys = token.maintainers.map { it.owningKey }.toSet()
        assertEquals(maintainerKeys, createTx.requiredSigningKeys, "Must be signed by all maintainers")

        // Alice, Bob, Charlie, and Denise should record the transaction
        assertHasTransaction(createTx, alice, bob, charlie, denise)
    }

    /**
     * An evolvable token should *not* be creatable if not all maintainers are participants.
     * Participants is *not* a superset of maintainers.
     */
    @Test
    fun `fails if participants is not a superset of maintainers`() {
        val token = factory.withDifferingMaintainersAndParticipants()

        assertFails("When creating an evolvable token all maintainers must also be participants.") {
            alice.createEvolvableToken(token, notaryIdentity).getOrThrow()
        }
    }

    // Flows with observers
    @Test
    fun `with unrelated observer`() {
        // Denise as observer, Alice as maintainer and issuer, Charlie as participant in the evolvable state.
        val token = factory.withOneMaintainerAndOneParticipant()

        val createTx = alice.createEvolvableToken(token, notaryIdentity, listOf(denise.legalIdentity())).getOrThrow()
        val createdToken = createTx.singleOutput<TestEvolvableTokenType>()

        // Expect to have one create command with maintainer signature
        val maintainerKeys = token.maintainers.map { it.owningKey }.toSet()
        assertEquals(maintainerKeys, createTx.requiredSigningKeys, "Must be signed by all maintainers")

        // Alice, Charlie, Denise should record the transaction
        assertHasTransaction(createTx, alice, charlie, denise)
        val aliceToken = alice.services.vaultService.queryBy<TestEvolvableTokenType>().states
        assertEquals(createdToken, aliceToken.single())
        val charlieToken = charlie.services.vaultService.queryBy<TestEvolvableTokenType>().states
        assertEquals(createdToken, charlieToken.single())
        val deniseQuery = denise.services.vaultService.queryBy<TestEvolvableTokenType>().states
        assertEquals(createdToken, deniseQuery.single())
    }

    @Test
    fun `with observer that is participant`() {
        // Charlie as observer and participant, Alice as maintainer and issuer.
        val token = factory.withOneMaintainerAndOneParticipant()

        val createTx = alice.createEvolvableToken(token, notaryIdentity, listOf(charlie.legalIdentity())).getOrThrow()
        val createdToken = createTx.singleOutput<TestEvolvableTokenType>()

        // Expect to have one create command with maintainer signature
        val maintainerKeys = token.maintainers.map { it.owningKey }.toSet()
        assertEquals(maintainerKeys, createTx.requiredSigningKeys, "Must be signed by all maintainers")

        // Alice, Charlie, Denise should record the transaction
        assertHasTransaction(createTx, alice, charlie)
        val aliceToken = alice.services.vaultService.queryBy<TestEvolvableTokenType>().states
        assertEquals(createdToken, aliceToken.single())
        val charlieToken = charlie.services.vaultService.queryBy<TestEvolvableTokenType>().states
        assertEquals(createdToken, charlieToken.single())
    }

    @Test
    fun `with observer that is maintainer`() {
        // Charlie as participant, Alice as maintainer, issuer and observer.
        val token = factory.withOneMaintainerAndOneParticipant()

        val createTx = alice.createEvolvableToken(token, notaryIdentity, listOf(alice.legalIdentity())).getOrThrow()
        val createdToken = createTx.singleOutput<TestEvolvableTokenType>()

        // Expect to have one create command with maintainer signature
        val maintainerKeys = token.maintainers.map { it.owningKey }.toSet()
        assertEquals(maintainerKeys, createTx.requiredSigningKeys, "Must be signed by all maintainers")

        // Alice, Charlie, Denise should record the transaction
        assertHasTransaction(createTx, alice, charlie)
        val aliceToken = alice.services.vaultService.queryBy<TestEvolvableTokenType>().states
        assertEquals(createdToken, aliceToken.single())
        val charlieToken = charlie.services.vaultService.queryBy<TestEvolvableTokenType>().states
        assertEquals(createdToken, charlieToken.single())
    }

    /*
      This flow test checks whether updation of EvolvableToken by observers is not possible
   */
    @Test
    fun `should not update EvolvableToken by observers`() {

        val house = House("24 Leinster Gardens, Bayswater, London", 1_000_000.GBP, listOf(denise.legalIdentity(), alice.legalIdentity()), linearId = UniqueIdentifier())
        val observers = listOf(bob.legalIdentity(), charlie.legalIdentity())
        // Create an EvolvableToken with two observers bob and charlie
        val createEvolvableTokenTx = denise.startFlow(CreateEvolvableTokens(house withNotary network.defaultNotaryNode.legalIdentity(), observers)).getOrThrow()
        assertHasTransaction(createEvolvableTokenTx, network, denise, alice, bob, charlie)
        // Get the EvolvableToken state
        val oldHouseToken = createEvolvableTokenTx.coreTransaction.outRefsOfType<House>().single()
        // Updating the EvolvableToken
        val newHouseToken = oldHouseToken.state.data.copy(valuation = 900_000.GBP)
        // Expecting error when Observer bob tries to update evolvable token
        assertFailsWith(IllegalArgumentException::class, "Observers could not update Evolvable tokens") {
            // Update the token by observer charlie
            val newUpdationByObserverNodeCTx = charlie.startFlow(UpdateEvolvableToken(oldHouseToken, newHouseToken)).getOrThrow()
            // The above update transaction should not be present in bob
            assertHasTransaction(newUpdationByObserverNodeCTx, network, denise, alice, charlie)
        }
    }

    /*
         This test case checks whether removed maintainers couldn't update the evolvable token anymore
    */
    @Test
    fun `should remove maintainer from maintainers and the party should not be able to update anymore`() {
        // Create Evolvable Token
        val house = House("24 Leinster Gardens, Bayswater, London", 900_000.GBP, listOf(alice.legalIdentity(), bob.legalIdentity()), linearId = UniqueIdentifier())
        // Empty observer list
        val observerList = emptyList<Party>()
        // Create Evolvable token with two maintainers alice and bob
        val tokenTxResult = denise.startFlow(CreateEvolvableTokens(house withNotary network.defaultNotaryNode.legalIdentity(), observerList))
        // Getting the signed transaction
        val tokenTx = tokenTxResult.getOrThrow()
        // Checks if the maintainers have recorded the transaction
        assertHasTransaction(tokenTx, network, alice, bob, denise)
        // Getting created evolvable token from above transaction
        val createdToken = tokenTx.singleOutput<House>()
        // Linear Id of created evolvable token
        val createdTokenId = createdToken.linearId()
        // Update evolvable token
        val newHouseToken1 = House("24 Leinster Gardens, Bayswater, London", 855_000.GBP, listOf(alice.legalIdentity(), bob.legalIdentity()), linearId = createdTokenId)
        // Checks if bob can update the token successfully
        val updateTxByNodeB = bob.startFlow(UpdateEvolvableToken(createdToken, newHouseToken1))
        // Getting the result of update transaction
        val updateTxByNodeBResult = updateTxByNodeB.getOrThrow()
        // Checks if the maintainers have recorded the transaction
        assertHasTransaction(updateTxByNodeBResult, network, alice, bob)
        // Getting state and reference from above transaction
        val updateTxByNodeBState = updateTxByNodeBResult.singleOutput<House>()
        // Linear Id of updated evolvable token
        val updatedTokenId = updateTxByNodeBState.linearId()
        // Removing maintainer bob from maintainer list and updating the valuation of evolvable token
        val newHouseToken2 = House("24 Leinster Gardens, Bayswater, London", 850_000.GBP, listOf(alice.legalIdentity()), linearId = updatedTokenId)
        // Updating the above change
        val updateTxByNodeA = alice.startFlow(UpdateEvolvableToken(updateTxByNodeBState, newHouseToken2))
        // Getting the signed transaction
        val updateTxByNodeAResult = updateTxByNodeA.getOrThrow()
        // Getting state from the above transaction
        val updateTxByNodeAState = updateTxByNodeAResult.singleOutput<House>()
        println("Maintainers after updation: " + updateTxByNodeAState.state.data.maintainers)
        // Checking whether the above transaction is recorded
        assertHasTransaction(updateTxByNodeAResult, network, bob, alice)
        // Creating another update in Evolvable token
        val newHouseToken3 = House("24 Leinster Gardens, Bayswater, London", 860_000.GBP, listOf(alice.legalIdentity()), linearId = updateTxByNodeAState.linearId())
        // Checking observer bob could update the evolvable token
        assertFailsWith(IllegalArgumentException::class, "Bob is not a maintainer") {
            // Updation by bob
            val updateByNodeB = bob.startFlow(UpdateEvolvableToken(updateTxByNodeAState, newHouseToken3))
            // Getting the signed transaction
            val updateByNodeBTx = updateByNodeB.getOrThrow()
            // Checking whether the above transaction is recorded
            assertHasTransaction(updateByNodeBTx, network, bob, alice)
        }
    }

    /*
        It should be able to change a Maintainer to Observer and after that, the party should not be able to update the Evolvable
        Token anymore
    */

    @Test
    fun `should change the maintainer to observer and the party should not be able to update anymore`() {
        // Create Maintainers list
        val maintainers = listOf(alice.legalIdentity(), bob.legalIdentity())
        // Create an Evolvable token type
        val house: EvolvableTokenType = House("24 Leinster Gardens, Bayswater, London", 900_000.GBP, maintainers, linearId = UniqueIdentifier())
        // Create an empty Observer list
        val observerList = emptyList<Party>()
        // Create Evolvable token
        val createEvolvableTokenSignedTx = denise.startFlow(CreateEvolvableTokens(house withNotary network.defaultNotaryNode.legalIdentity(), observerList))

        val createEvolvableTokenTx = createEvolvableTokenSignedTx.getOrThrow()
        // Checks if the Maintainers have recorded the transaction
        assertHasTransaction(createEvolvableTokenTx, network, alice, bob)
        // Get the state of token from transaction
        val createdTokenStateFromTransaction = createEvolvableTokenTx.singleOutput<House>()
        // Remove bob from maintainers list
        val newMaintainers = listOf(alice.legalIdentity())
        // Create a new Evolvable token type where bob is removed as Maintainer
        val newTokenB = House("24 Leinster Gardens, Bayswater, London", 850_000.GBP, newMaintainers, linearId = house.linearId)
        // Set bob as an Observer
        val newObservers = listOf(bob.legalIdentity())
        // alice updates the old token with new token where bob is an Observer
        val updateTxBResult = alice.startFlow(UpdateEvolvableToken(createdTokenStateFromTransaction, newTokenB, newObservers))

        val updateTxB = updateTxBResult.getOrThrow()
        assertHasTransaction(updateTxB, network)
        // Create a new Evolvable token type
        val newToken = House("24 Leinster Gardens, Bayswater, London", 855_000.GBP, listOf(alice.legalIdentity()), linearId = house.linearId)
        // The transaction to update the token with newly created token type should fail since bob is an Observer and Observer should not be able to update
        assertFailsWith(NotaryException::class, "Bob is not a maintainer") {
            val updateTxIResult = bob.startFlow(UpdateEvolvableToken(createdTokenStateFromTransaction, newToken, listOf(bob.legalIdentity())))
            val updateTxI = updateTxIResult.getOrThrow()
        }
    }

    /*
        It should be possible to change the Observer to Maintainer and after that the party should be able to update the token type
    */

    @Test
    fun `should change the observer to maintainer and the party should be able to update EvolvableTokenType details`() {
        // Create a maintainers list
        val maintainers = listOf(alice.legalIdentity())
        // Create an Evolvable token type
        val house: EvolvableTokenType = House("24 Leinster Gardens, Bayswater, London", 900_000.GBP, maintainers, linearId = UniqueIdentifier())
        // Set bob as Observer
        val observerList = listOf(bob.legalIdentity())
        // Create Evolvable token with Evolvable token type and Observers
        val tokenTxResult = alice.startFlow(CreateEvolvableTokens(house withNotary network.defaultNotaryNode.legalIdentity(), observerList))
        val tokenTx = tokenTxResult.getOrThrow()
        // Checks if the maintainers and observers have recorded the transaction
        assertHasTransaction(tokenTx, network, alice, bob)
        val createdToken = tokenTx.singleOutput<House>()
        // bob should not be able to update since it is an Observer
        assertFailsWith(IllegalArgumentException::class, "Bob is not a maintainer"
        ) {
            val newTokenB = House("24 Leinster Gardens, Bayswater, London", 850_000.GBP, listOf(alice.legalIdentity()), linearId = house.linearId)
            val updateTxBResult = bob.startFlow(UpdateEvolvableToken(createdToken, newTokenB))
            val updateTxB = updateTxBResult.getOrThrow()
        }
        // Set alice and bob as maintainers
        val newMaintainers = listOf(alice.legalIdentity(), bob.legalIdentity())
        // Create a new Evolvable token type with new maintainers
        val newTokenA = House("24 Leinster Gardens, Bayswater, London", 850_000.GBP, newMaintainers, linearId = house.linearId)
        // alice updates the old token with new token type
        val updateTxAResult = alice.startFlow(UpdateEvolvableToken(createdToken, newTokenA))
        val updateTxA = updateTxAResult.getOrThrow()
        val updatedTokenA = updateTxA.singleOutput<House>()
        // Create a new Evolvable token type
        val newTokenB = House("24 Leinster Gardens, Bayswater, London", 750_000.GBP, newMaintainers, linearId = house.linearId)
        // bob should be able to perform updation since it is a Maintainer now
        val updateTxBResult = bob.startFlow(UpdateEvolvableToken(updatedTokenA, newTokenB))
        val updateTxB = updateTxBResult.getOrThrow()
        assertHasTransaction(updateTxB, network, bob)
    }

    @Test
    fun `signing party should be one of the Create command signers`() {
        val token = factory.withTwoMaintainers()

        bob.registerInitiatedFlow(FakeCreateEvolvableTokenInitiatorWithWrongSigners::class.java, CreateEvolvableTokensFlowHandler::class.java)

        try {
            alice.transaction { alice.startFlow(FakeCreateEvolvableTokenInitiatorWithWrongSigners(token, bob.legalIdentity(), notaryIdentity)) }.getOrThrow()
            fail()
        } catch (ex: FlowException) {
            assertEquals("Signing party should be one of the Create command signers.", ex.message)
        }
    }

    @Test
    fun `signing party should be one of the token type maintainers`() {
        val token = factory.withTwoMaintainers()

        bob.registerInitiatedFlow(FakeCreateEvolvableTokenInitiatorWithWrongMaintainers::class.java, CreateEvolvableTokensFlowHandler::class.java)

        try {
            alice.transaction { alice.startFlow(FakeCreateEvolvableTokenInitiatorWithWrongMaintainers(token, bob.legalIdentity(), notaryIdentity)) }.getOrThrow()
            fail()
        } catch (ex: FlowException) {
            assertEquals("Signing party should be one of the token type maintainers.", ex.message)
        }
    }

    @Test
    fun `signing party should be one of the token type participants`() {
        val token = factory.withTwoMaintainers()

        bob.registerInitiatedFlow(FakeCreateEvolvableTokenInitiatorWithWrongParticipant::class.java, CreateEvolvableTokensFlowHandler::class.java)

        try {
            alice.transaction { alice.startFlow(FakeCreateEvolvableTokenInitiatorWithWrongParticipant(token, bob.legalIdentity(), notaryIdentity)) }.getOrThrow()
            fail()
        } catch (ex: FlowException) {
            assertEquals("Signing party should be one of the token type participants.", ex.message)
        }
    }

    @Test
    fun `signing party should call additional transaction check when provided`() {
        val token = factory.withTwoMaintainers()

        bob.registerInitiatedFlow(FakeCreateEvolvableTokenFlowWrapper::class.java, FakeCreateEvolvableTokenFlowHandlerWrapper::class.java)

        assertEquals(0, FakeCreateEvolvableTokenFlowHandlerWrapper.counter.get())

        alice.transaction { alice.startFlow(FakeCreateEvolvableTokenFlowWrapper(token, bob.legalIdentity(), notaryIdentity)) }.getOrThrow()

        assertEquals(1, FakeCreateEvolvableTokenFlowHandlerWrapper.counter.get())
    }
}

abstract class FakeCreateEvolvableTokenInitiator(val token : EvolvableTokenType, val otherParty: Party, val notary: Party) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        val transactionBuilder = TransactionBuilder(notary)

        val participants = getParticipants()
        transactionBuilder
            .addOutputState(getOutputState(participants, getMaintainers(participants)))
        getCommands(participants).forEach { transactionBuilder.addCommand(it.value, it.signers) }

        val ptx: SignedTransaction = serviceHub.signInitialTransaction(transactionBuilder)
        val otherPartySession = initiateFlow(otherParty)

        otherPartySession.send(CreateEvolvableTokensFlow.Notification(signatureRequired = true))
        subFlow(CollectSignaturesFlow(
            partiallySignedTx = ptx,
            sessionsToCollectFrom = listOf(otherPartySession)
            ))
    }

    protected open fun getParticipants() : List<Party> = listOf(ourIdentity, otherParty)
    protected open fun getMaintainers(participants: List<Party>) = participants
    protected open fun getOutputState(participants: List<Party>, maintainers: List<Party>) : EvolvableTokenType
        = FakeEvolvableTokenType(participants = participants, maintainers = maintainers)
    protected open fun getCommands(participants: List<Party>) : List<CommandWithParties<CommandData>>
        = listOf(CommandWithParties(participants.map { it.owningKey }, participants, Create()))
}

@InitiatingFlow
open class FakeCreateEvolvableTokenInitiatorWithWrongSigners(token : EvolvableTokenType, otherParty: Party, notary: Party) :
    FakeCreateEvolvableTokenInitiator(token, otherParty, notary) {

    override fun getCommands(participants: List<Party>) : List<CommandWithParties<CommandData>>
            = listOf(CommandWithParties(listOf(ourIdentity.owningKey), listOf(ourIdentity), Create()),
        CommandWithParties(listOf(otherParty.owningKey), listOf(otherParty), FakeCommand())
    )

    class FakeCommand : CommandData
}

@InitiatingFlow
open class FakeCreateEvolvableTokenInitiatorWithWrongMaintainers(token : EvolvableTokenType, otherParty: Party, notary: Party) :
    FakeCreateEvolvableTokenInitiator(token, otherParty, notary) {

    override fun getMaintainers(participants: List<Party>) = listOf(ourIdentity)
}

@InitiatingFlow
open class FakeCreateEvolvableTokenInitiatorWithWrongParticipant(token : EvolvableTokenType, otherParty: Party, notary: Party) :
    FakeCreateEvolvableTokenInitiator(token, otherParty, notary) {

    override fun getParticipants() = listOf(ourIdentity)
    override fun getCommands(participants: List<Party>) : List<CommandWithParties<CommandData>>
            = listOf(CommandWithParties(listOf(ourIdentity.owningKey, otherParty.owningKey), listOf(ourIdentity, otherParty), Create()))
    override fun getMaintainers(participants: List<Party>) = listOf(ourIdentity, otherParty)
}


@InitiatingFlow
class FakeCreateEvolvableTokenFlowWrapper(token : EvolvableTokenType, otherParty: Party, notary: Party) :
    FakeCreateEvolvableTokenInitiator(token, otherParty, notary)

@InitiatedBy(FakeCreateEvolvableTokenFlowWrapper::class)
class FakeCreateEvolvableTokenFlowHandlerWrapper(val session: FlowSession) : FlowLogic<Unit>() {
    companion object {
        var counter = AtomicInteger(0)
    }

    @Suspendable
    override fun call() {
        subFlow(CreateEvolvableTokensFlowHandler(session) {
            counter.incrementAndGet()
        })
    }
}