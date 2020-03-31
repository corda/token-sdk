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
		val token = factory.withOneMaintainerAndOneParticipant()
		val createTx = alice.createEvolvableToken(token, notaryIdentity).getOrThrow()
		val createdToken = createTx.singleOutput<TestEvolvableTokenType>()

		// Update the token
		val newToken = factory.withOneMaintainerAndOneParticipant(token.linearId)
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
		val token = factory.withOneMaintainerAndTwoParticipants()
		val createTx = alice.createEvolvableToken(token, notaryIdentity).getOrThrow()
		val createdToken = createTx.singleOutput<TestEvolvableTokenType>()

		// Update the token
		val newToken = factory.withOneMaintainerAndTwoParticipants(token.linearId)
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
		val token = factory.withTwoMaintainersAndOneParticipant()
		val createTx = alice.createEvolvableToken(token, notaryIdentity).getOrThrow()
		val createdToken = createTx.singleOutput<TestEvolvableTokenType>()

		// Update the token
		val newToken = factory.withTwoMaintainersAndOneParticipant(token.linearId)
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
		val token = factory.withTwoMaintainersAndTwoParticipants()
		val createTx = alice.createEvolvableToken(token, notaryIdentity).getOrThrow()
		val createdToken = createTx.singleOutput<TestEvolvableTokenType>()

		// Update the token
		val newToken = factory.withTwoMaintainersAndTwoParticipants(token.linearId)
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
	fun `adding 1 participant`() {
		// Create the token
		val token = factory.withOneMaintainer()
		val createTx = alice.createEvolvableToken(token, notaryIdentity).getOrThrow()
		val createdToken = createTx.singleOutput<TestEvolvableTokenType>()

		// Update the token
		val newToken = factory.withOneMaintainerAndOneParticipant(token.linearId)
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
	fun `removing 1 participant`() {
		// Create the token
		val token = factory.withOneMaintainerAndOneParticipant()
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
		val newToken = factory.withOneMaintainerAndOneParticipant(token.linearId, maintainer = denise.legalIdentity())
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

	// Flows with observers
	@Test
	fun `with unrelated observer`() {
		// Create the token
		val token = factory.withOneMaintainerAndOneParticipant()
		val createTx = alice.createEvolvableToken(token, notaryIdentity).getOrThrow()
		val createdToken = createTx.singleOutput<TestEvolvableTokenType>()

		// Update the token
		val newToken = factory.withOneMaintainerAndOneParticipant(token.linearId)
		val updateTx = alice.updateEvolvableToken(createdToken, newToken, listOf(denise.legalIdentity())).getOrThrow()
		val updatedToken = updateTx.singleOutput<TestEvolvableTokenType>()

		// Expect to have one update command with maintainer signature
		val expectedSigningKeys = (token.maintainers + newToken.maintainers + notaryIdentity).map { it.owningKey }.toSet()
		assertEquals(expectedSigningKeys, updateTx.requiredSigningKeys, "Must be signed by all maintainers and the notary")

		// Alice and Charlie should record the transaction
		assertHasTransaction(updateTx, alice, charlie, denise)

		val aliceToken = alice.services.vaultService.queryBy<TestEvolvableTokenType>().states
		assertEquals(updatedToken, aliceToken.single())
		val charlieToken = charlie.services.vaultService.queryBy<TestEvolvableTokenType>().states
		assertEquals(updatedToken, charlieToken.single())
		val deniseQuery = denise.services.vaultService.queryBy<TestEvolvableTokenType>().states
		assertEquals(updatedToken, deniseQuery.single())
	}

	@Test
	fun `with observer that is maintainer`() {
		// Create the token
		val token = factory.withOneMaintainerAndOneParticipant()
		val createTx = alice.createEvolvableToken(token, notaryIdentity).getOrThrow()
		val createdToken = createTx.singleOutput<TestEvolvableTokenType>()

		// Update the token
		val newToken = factory.withOneMaintainerAndOneParticipant(token.linearId)
		val updateTx = alice.updateEvolvableToken(createdToken, newToken, listOf(alice.legalIdentity())).getOrThrow()
		val updatedToken = updateTx.singleOutput<TestEvolvableTokenType>()

		// Expect to have one update command with maintainer signature
		val expectedSigningKeys = (token.maintainers + newToken.maintainers + notaryIdentity).map { it.owningKey }.toSet()
		assertEquals(expectedSigningKeys, updateTx.requiredSigningKeys, "Must be signed by all maintainers and the notary")

		// Alice and Charlie should record the transaction
		assertHasTransaction(updateTx, alice, charlie)
		val aliceToken = alice.services.vaultService.queryBy<TestEvolvableTokenType>().states
		assertEquals(updatedToken, aliceToken.single())
		val charlieToken = charlie.services.vaultService.queryBy<TestEvolvableTokenType>().states
		assertEquals(updatedToken, charlieToken.single())
	}

	@Test
	fun `with observer that is participant`() {
		// Create the token
		val token = factory.withOneMaintainerAndOneParticipant()
		val createTx = alice.createEvolvableToken(token, notaryIdentity).getOrThrow()
		val createdToken = createTx.singleOutput<TestEvolvableTokenType>()

		// Update the token
		val newToken = factory.withOneMaintainerAndOneParticipant(token.linearId)
		val updateTx = alice.updateEvolvableToken(createdToken, newToken, listOf(charlie.legalIdentity())).getOrThrow()
		val updatedToken = updateTx.singleOutput<TestEvolvableTokenType>()

		// Expect to have one update command with maintainer signature
		val expectedSigningKeys = (token.maintainers + newToken.maintainers + notaryIdentity).map { it.owningKey }.toSet()
		assertEquals(expectedSigningKeys, updateTx.requiredSigningKeys, "Must be signed by all maintainers and the notary")

		// Alice and Charlie should record the transaction
		assertHasTransaction(updateTx, alice, charlie)
		val aliceToken = alice.services.vaultService.queryBy<TestEvolvableTokenType>().states
		assertEquals(updatedToken, aliceToken.single())
		val charlieToken = charlie.services.vaultService.queryBy<TestEvolvableTokenType>().states
		assertEquals(updatedToken, charlieToken.single())
	}

	@Test
	fun `non maintainer updating an evolvable token type fails`() {
		// Alice creates an evolvable token type.
		val token = factory.withOneMaintainer(maintainer = alice.info.legalIdentities.first())
		val createTx = alice.createEvolvableToken(token, notaryIdentity).getOrThrow()
		val createdToken = createTx.singleOutput<TestEvolvableTokenType>()

		// Bob tries to update the token.
		val newToken = createdToken.state.data
		assertFails("This flow can only be started by existing maintainers of the EvolvableTokenType.") {
			bob.updateEvolvableToken(createdToken, newToken).getOrThrow()
		}
	}
}