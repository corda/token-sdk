package com.r3.corda.lib.tokens.workflows

import com.r3.corda.lib.tokens.contracts.states.EvolvableTokenType
import com.r3.corda.lib.tokens.testing.states.TestEvolvableTokenType
import com.r3.corda.lib.tokens.workflows.factories.TestEvolvableTokenTypeFactory
import net.corda.core.node.services.queryBy
import net.corda.core.utilities.getOrThrow
import net.corda.testing.node.StartedMockNode
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails

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
}