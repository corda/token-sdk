package com.r3.corda.sdk.token.workflow

import com.r3.corda.sdk.token.contracts.states.EvolvableTokenType
import com.r3.corda.sdk.token.workflow.factories.TestEvolvableTokenTypeFactory
import com.r3.corda.sdk.token.workflow.states.TestEvolvableTokenType
import net.corda.core.utilities.getOrThrow
import net.corda.testing.node.StartedMockNode
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails

class UpdateEvolvableTokenTests : JITMockNetworkTests() {

    private val alice: StartedMockNode get() = node("Alice")
    private val bob: StartedMockNode get() = node("Bob")
    private val charlie: StartedMockNode get() = node("Charlie")
    private val denise: StartedMockNode get() = node("Denise")

    /**
     * Helper factory for assembling [EvolvableTokenType]s reliably.
     */
    private val factory = TestEvolvableTokenTypeFactory(this, "Alice", "Bob", "Charlie", "Denise")

    /**
     * An evolvable token should be updatable with just a maintainer.
     * Maintainers is equal to participants.
     */
    @Test
    fun `with 1 maintainer`() {
        // Create the token
        val token = factory.withOneMaintainer()
        val createTx = alice.createEvolvableToken(token, notaryIdentity).getOrThrow()
        val createdToken = createTx.singleOutput<TestEvolvableTokenType>()

        // Update the token
        val newToken = factory.withOneMaintainer(token.linearId)
        val updateTx = alice.updateEvolvableToken(createdToken, newToken).getOrThrow()
        val updatedToken = updateTx.singleOutput<TestEvolvableTokenType>()

        // Expect proposed and updated tokensToIssue to match
        assertEquals(newToken, updatedToken.state.data, "Original token did not match the published token.")

        // Expect to have one update command with maintainer signature
        val expectedSigningKeys = (token.maintainers + newToken.maintainers + notaryIdentity).map { it.owningKey }.toSet()
        assertEquals(expectedSigningKeys, updateTx.tx.requiredSigningKeys, "Must be signed by all maintainers and the notary")

        // Only Alice should record the transaction
        assertHasTransaction(updateTx, alice)
    }

    /**
     * An evolvable token should be updatable with two maintainers. Both must sign.
     * Maintainers is equal to participants.
     */
    @Test
    fun `with 2 maintainers`() {
        // Create the token
        val token = factory.withTwoMaintainers()
        val createTx = alice.createEvolvableToken(token, notaryIdentity).getOrThrow()
        val createdToken = createTx.singleOutput<TestEvolvableTokenType>()

        // Update the token
        val newToken = factory.withTwoMaintainers(token.linearId)
        val updateTx = alice.updateEvolvableToken(createdToken, newToken).getOrThrow()
        val updatedToken = updateTx.singleOutput<TestEvolvableTokenType>()

        // Expect proposed and updated tokensToIssue to match
        assertEquals(newToken, updatedToken.state.data, "Original token did not match the published token.")

        // Expect to have one update command with maintainer signature
        val expectedSigningKeys = (token.maintainers + newToken.maintainers + notaryIdentity).map { it.owningKey }.toSet()
        assertEquals(expectedSigningKeys, updateTx.requiredSigningKeys, "Must be signed by all maintainers and the notary")

        // Alice and Bob should record the transaction
        assertHasTransaction(updateTx, alice, bob)
    }

    /**
     * An evolvable token should be creatable with a maintainer and an additional participant. Only maintainers must sign.
     * Participants is a superset of maintainers.
     */
    @Test
    fun `with 1 maintainer and 1 additional participant`() {
        // Create the token
        val token = factory.withOneMaintainerAndOneObserver()
        val createTx = alice.createEvolvableToken(token, notaryIdentity).getOrThrow()
        val createdToken = createTx.singleOutput<TestEvolvableTokenType>()

        // Update the token
        val newToken = factory.withOneMaintainerAndOneObserver(token.linearId)
        val updateTx = alice.updateEvolvableToken(createdToken, newToken).getOrThrow()
        val updatedToken = updateTx.singleOutput<TestEvolvableTokenType>()

        // Expect proposed and updated tokensToIssue to match
        assertEquals(newToken, updatedToken.state.data, "Original token did not match the published token.")

        // Expect to have one update command with maintainer signature
        val expectedSigningKeys = (token.maintainers + newToken.maintainers + notaryIdentity).map { it.owningKey }.toSet()
        assertEquals(expectedSigningKeys, updateTx.requiredSigningKeys, "Must be signed by all maintainers and the notary")

        // Alice and Charlie should record the transaction
        assertHasTransaction(updateTx, alice, charlie)
    }

    /**
     * An evolvable token should be creatable with a maintainer and two additional participants. Only maintainers must sign.
     * Participants is a superset of maintainers.
     */
    @Test
    fun `with 1 maintainer and 2 additional participants`() {
        // Create the token
        val token = factory.withOneMaintainerAndTwoObservers()
        val createTx = alice.createEvolvableToken(token, notaryIdentity).getOrThrow()
        val createdToken = createTx.singleOutput<TestEvolvableTokenType>()

        // Update the token
        val newToken = factory.withOneMaintainerAndTwoObservers(token.linearId)
        val updateTx = alice.updateEvolvableToken(createdToken, newToken).getOrThrow()
        val updatedToken = updateTx.singleOutput<TestEvolvableTokenType>()

        // Expect proposed and updated tokensToIssue to match
        assertEquals(newToken, updatedToken.state.data, "Original token did not match the published token.")

        // Expect to have one update command with maintainer signature
        val expectedSigningKeys = (token.maintainers + newToken.maintainers + notaryIdentity).map { it.owningKey }.toSet()
        assertEquals(expectedSigningKeys, updateTx.requiredSigningKeys, "Must be signed by all maintainers and the notary")

        // Alice, Charlie, and Denise should record the transaction
        assertHasTransaction(updateTx, alice, charlie, denise)
    }

    /**
     * An evolvable token should be updatable with two maintainers and an additional participant. Only maintainers must sign.
     * Participants is a superset of maintainers.
     */
    @Test
    fun `with 2 maintainers and 1 additional participant`() {
        // Create the token
        val token = factory.withTwoMaintainersAndOneObserver()
        val createTx = alice.createEvolvableToken(token, notaryIdentity).getOrThrow()
        val createdToken = createTx.singleOutput<TestEvolvableTokenType>()

        // Update the token
        val newToken = factory.withTwoMaintainersAndOneObserver(token.linearId)
        val updateTx = alice.updateEvolvableToken(createdToken, newToken).getOrThrow()
        val updatedToken = updateTx.singleOutput<TestEvolvableTokenType>()

        // Expect proposed and updated tokensToIssue to match
        assertEquals(newToken, updatedToken.state.data, "Original token did not match the published token.")

        // Expect to have one update command with maintainer signature
        val expectedSigningKeys = (token.maintainers + newToken.maintainers + notaryIdentity).map { it.owningKey }.toSet()
        assertEquals(expectedSigningKeys, updateTx.requiredSigningKeys, "Must be signed by all maintainers and the notary")

        // Alice, Bob, and Charlie should record the transaction
        assertHasTransaction(updateTx, alice, bob, charlie)
    }

    /**
     * An evolvable token should be creatable with two maintainers and two additional participants. Only maintainers must sign.
     * Participants is a superset of maintainers.
     */
    @Test
    fun `with 2 maintainers and 2 additional participants`() {
        // Create the token
        val token = factory.withTwoMaintainersAndTwoObservers()
        val createTx = alice.createEvolvableToken(token, notaryIdentity).getOrThrow()
        val createdToken = createTx.singleOutput<TestEvolvableTokenType>()

        // Update the token
        val newToken = factory.withTwoMaintainersAndTwoObservers(token.linearId)
        val updateTx = alice.updateEvolvableToken(createdToken, newToken).getOrThrow()
        val updatedToken = updateTx.singleOutput<TestEvolvableTokenType>()

        // Expect proposed and updated tokensToIssue to match
        assertEquals(newToken, updatedToken.state.data, "Original token did not match the published token.")

        // Expect to have one update command with maintainer signature
        val expectedSigningKeys = (token.maintainers + newToken.maintainers + notaryIdentity).map { it.owningKey }.toSet()
        assertEquals(expectedSigningKeys, updateTx.requiredSigningKeys, "Must be signed by all maintainers and the notary")

        // Alice, Bob, Charlie, and Denise should record the transaction
        assertHasTransaction(updateTx, alice, bob, charlie, denise)
    }

    /**
     * An evolvable token should *not* be updatable if not all maintainers are participants.
     * Participants is *not* a superset of maintainers.
     */
    @Test
    fun `fails if participants is not a superset of maintainers`() {
        // Create the token
        val token = factory.withOneMaintainer()
        val createTx = alice.createEvolvableToken(token, notaryIdentity).getOrThrow()
        val createdToken = createTx.singleOutput<TestEvolvableTokenType>()

        // Update the token
        val newToken = factory.withDifferingMaintainersAndParticipants(token.linearId)

        assertFails("When creating an evolvable token all maintainers must also be participants.") {
            alice.updateEvolvableToken(createdToken, newToken).getOrThrow()
        }
    }

    /**
     * An evolvable token should allow adding a maintainer.
     * Participants is a superset of maintainers.
     */
    @Test
    fun `from 1 maintainers to 2 maintainers`() {
        // Create the token
        val token = factory.withOneMaintainer()
        val createTx = alice.createEvolvableToken(token, notaryIdentity).getOrThrow()
        val createdToken = createTx.singleOutput<TestEvolvableTokenType>()

        // Update the token
        val newToken = factory.withTwoMaintainers(token.linearId)
        val updateTx = alice.updateEvolvableToken(createdToken, newToken).getOrThrow()
        val updatedToken = updateTx.singleOutput<TestEvolvableTokenType>()

        // Expect proposed and updated tokensToIssue to match
        assertEquals(newToken, updatedToken.state.data, "Original token did not match the published token.")

        // Expect to have one update command with maintainer signature
        val expectedSigningKeys = (token.maintainers + newToken.maintainers + notaryIdentity).map { it.owningKey }.toSet()
        assertEquals(expectedSigningKeys, updateTx.requiredSigningKeys, "Must be signed by all maintainers and the notary")

        // Alice, Bob, Charlie, and Denise should record the transaction
        assertHasTransaction(updateTx, alice, bob)
    }

    /**
     * An evolvable token should allow removing a maintainer.
     * Participants is a superset of maintainers.
     */
    @Test
    fun `from 2 maintainers to 1 maintainer`() {
        // Create the token
        val token = factory.withTwoMaintainers()
        val createTx = alice.createEvolvableToken(token, notaryIdentity).getOrThrow()
        val createdToken = createTx.singleOutput<TestEvolvableTokenType>()

        // Update the token
        val newToken = factory.withOneMaintainer(token.linearId)
        val updateTx = alice.updateEvolvableToken(createdToken, newToken).getOrThrow()
        val updatedToken = updateTx.singleOutput<TestEvolvableTokenType>()

        // Expect proposed and updated tokensToIssue to match
        assertEquals(newToken, updatedToken.state.data, "Original token did not match the published token.")

        // Expect to have one update command with maintainer signature
        val expectedSigningKeys = (token.maintainers + newToken.maintainers + notaryIdentity).map { it.owningKey }.toSet()
        assertEquals(expectedSigningKeys, updateTx.requiredSigningKeys, "Must be signed by all maintainers and the notary")

        // Alice, Bob, Charlie, and Denise should record the transaction
        assertHasTransaction(updateTx, alice, bob)
    }

    /**
     * An evolvable token should allow adding an observer.
     * Participants is a superset of maintainers.
     */
    @Test
    fun `adding 1 observer`() {
        // Create the token
        val token = factory.withOneMaintainer()
        val createTx = alice.createEvolvableToken(token, notaryIdentity).getOrThrow()
        val createdToken = createTx.singleOutput<TestEvolvableTokenType>()

        // Update the token
        val newToken = factory.withOneMaintainerAndOneObserver(token.linearId)
        val updateTx = alice.updateEvolvableToken(createdToken, newToken).getOrThrow()
        val updatedToken = updateTx.singleOutput<TestEvolvableTokenType>()

        // Expect proposed and updated tokensToIssue to match
        assertEquals(newToken, updatedToken.state.data, "Original token did not match the published token.")

        // Expect to have one update command with maintainer signature
        val expectedSigningKeys = (token.maintainers + newToken.maintainers + notaryIdentity).map { it.owningKey }.toSet()
        assertEquals(expectedSigningKeys, updateTx.requiredSigningKeys, "Must be signed by all maintainers and the notary")

        // Alice and Charlie should record the transaction
        assertHasTransaction(updateTx, alice, charlie)
    }

    /**
     * An evolvable token should allow adding an observer.
     * Participants is a superset of maintainers.
     */
    @Test
    fun `removing 1 observer`() {
        // Create the token
        val token = factory.withOneMaintainerAndOneObserver()
        val createTx = alice.createEvolvableToken(token, notaryIdentity).getOrThrow()
        val createdToken = createTx.singleOutput<TestEvolvableTokenType>()

        // Update the token
        val newToken = factory.withOneMaintainer(token.linearId)
        val updateTx = alice.updateEvolvableToken(createdToken, newToken).getOrThrow()
        val updatedToken = updateTx.singleOutput<TestEvolvableTokenType>()

        // Expect proposed and updated tokensToIssue to match
        assertEquals(newToken, updatedToken.state.data, "Original token did not match the published token.")

        // Expect to have one update command with maintainer signature
        val expectedSigningKeys = (token.maintainers + newToken.maintainers + notaryIdentity).map { it.owningKey }.toSet()
        assertEquals(expectedSigningKeys, updateTx.requiredSigningKeys, "Must be signed by all maintainers and the notary")

        // Alice and Charlie should record the transaction
        assertHasTransaction(updateTx, alice, charlie)
    }

    /**
     * An evolvable token should allow changing a maintainer.
     * Participants is a superset of maintainers.
     */
    @Test
    fun `changing maintainer`() {
        // Create the token
        val token = factory.withOneMaintainer()
        val createTx = alice.createEvolvableToken(token, notaryIdentity).getOrThrow()
        val createdToken = createTx.singleOutput<TestEvolvableTokenType>()

        // Update the token
        val newToken = factory.withOneMaintainerAndOneObserver(token.linearId, maintainer = denise.legalIdentity())
        val updateTx = alice.updateEvolvableToken(createdToken, newToken).getOrThrow()
        val updatedToken = updateTx.singleOutput<TestEvolvableTokenType>()

        // Expect proposed and updated tokensToIssue to match
        assertEquals(newToken, updatedToken.state.data, "Original token did not match the published token.")

        // Expect to have one update command with maintainer signature
        val expectedSigningKeys = (token.maintainers + newToken.maintainers + notaryIdentity).map { it.owningKey }.toSet()
        assertEquals(expectedSigningKeys, updateTx.requiredSigningKeys, "Must be signed by all maintainers and the notary")

        // Alice, Bob, Charlie, and Denise should record the transaction
        assertHasTransaction(updateTx, alice, denise)
    }


}