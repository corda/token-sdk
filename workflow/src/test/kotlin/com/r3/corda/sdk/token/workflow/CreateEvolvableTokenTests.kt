package com.r3.corda.sdk.token.workflow

import com.r3.corda.sdk.token.contracts.EvolvableTokenContract
import com.r3.corda.sdk.token.contracts.states.EvolvableTokenType
import net.corda.core.contracts.Contract
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.contracts.requireThat
import net.corda.core.identity.Party
import net.corda.core.transactions.LedgerTransaction
import net.corda.core.utilities.getOrThrow
import net.corda.testing.node.StartedMockNode
import org.junit.Test
import java.math.BigDecimal
import kotlin.test.assertEquals
import kotlin.test.assertFails

class CreateEvolvableTokenTests : JITMockNetworkTests() {

    private val alice: StartedMockNode get() = node("Alice")
    private val bob: StartedMockNode get() = node("Bob")
    private val charlie: StartedMockNode get() = node("Charlie")
    private val denise: StartedMockNode get() = node("Denise")

    /**
     * An evolvable token should be creatable with just a maintainer.
     * Maintainers is equal to participants.
     */
    @Test
    fun `with 1 maintainer`() {
        val maintainers = listOf(alice).map { it.legalIdentity() }
        val participants = maintainers
        val token = ThingContract.TokenType(maintainers, participants)

        val createTx = alice.createEvolvableToken(token, notary.legalIdentity()).getOrThrow()
        val createdToken = createTx.singleOutput<ThingContract.TokenType>()

        // Expect proposed and created tokens to match
        assertEquals(token, createdToken.state.data, "Original token did not match the published token.")

        // Expect to have one create command with maintainer signature
        val maintainerKeys = maintainers.map { it.owningKey }.toSet()
        assertEquals(maintainerKeys, createTx.requiredSigningKeys, "Must be signed by all maintainers")

        // Only Alice should record the transaction
        assertRecordsTransaction(createTx, alice)
    }

    /**
     * An evolvable token should be creatable with two maintainers. Both must sign.
     * Maintainers is equal to participants.
     */
    @Test
    fun `with 2 maintainers`() {
        val maintainers = listOf(alice, bob).map { it.legalIdentity() }
        val participants = maintainers
        val token = ThingContract.TokenType(maintainers, participants)

        val createTx = alice.createEvolvableToken(token, notary.legalIdentity()).getOrThrow()
        val createdToken = createTx.singleOutput<ThingContract.TokenType>()

        // Expect proposed and created tokens to match
        assertEquals(token, createdToken.state.data, "Original token did not match the published token.")

        // Expect to have one create command with maintainer signature
        val maintainerKeys = maintainers.map { it.owningKey }.toSet()
        assertEquals(maintainerKeys, createTx.requiredSigningKeys, "Must be signed by all maintainers")

        // Alice and Bob should record the transaction
        assertRecordsTransaction(createTx, alice, bob)
    }

    /**
     * An evolvable token should be creatable with a maintainer and an additional participant. Only maintainers must sign.
     * Participants is a superset of maintainers.
     */
    @Test
    fun `with 1 maintainer and 1 additional participant`() {
        val maintainers = listOf(alice).map { it.legalIdentity() }
        val participants = maintainers + listOf(charlie).map { it.legalIdentity() }
        val token = ThingContract.TokenType(maintainers, participants)

        val createTx = alice.createEvolvableToken(token, notary.legalIdentity()).getOrThrow()
        val createdToken = createTx.singleOutput<ThingContract.TokenType>()

        // Expect proposed and created tokens to match
        assertEquals(token, createdToken.state.data, "Original token did not match the published token.")

        // Expect to have one create command with maintainer signature
        val maintainerKeys = maintainers.map { it.owningKey }.toSet()
        assertEquals(maintainerKeys, createTx.requiredSigningKeys, "Must be signed by all maintainers")

        // Alice and Charlie should record the transaction
        assertRecordsTransaction(createTx, alice, charlie)
    }

    /**
     * An evolvable token should be creatable with a maintainer and two additional participants. Only maintainers must sign.
     * Participants is a superset of maintainers.
     */
    @Test
    fun `with 1 maintainer and 2 additional participants`() {
        val maintainers = listOf(alice).map { it.legalIdentity() }
        val participants = maintainers + listOf(charlie, denise).map { it.legalIdentity() }
        val token = ThingContract.TokenType(maintainers, participants)

        val createTx = alice.createEvolvableToken(token, notary.legalIdentity()).getOrThrow()
        val createdToken = createTx.singleOutput<ThingContract.TokenType>()

        // Expect proposed and created tokens to match
        assertEquals(token, createdToken.state.data, "Original token did not match the published token.")

        // Expect to have one create command with maintainer signature
        val maintainerKeys = maintainers.map { it.owningKey }.toSet()
        assertEquals(maintainerKeys, createTx.requiredSigningKeys, "Must be signed by all maintainers")

        // Alice, Charlie, and Denise should record the transaction
        assertRecordsTransaction(createTx, alice, charlie, denise)
    }

    /**
     * An evolvable token should be creatable with two maintainers and an additional participant. Only maintainers must sign.
     * Participants is a superset of maintainers.
     */
    @Test
    fun `with 2 maintainers and 1 additional participant`() {
        val maintainers = listOf(alice, bob).map { it.legalIdentity() }
        val participants = maintainers + listOf(charlie).map { it.legalIdentity() }
        val token = ThingContract.TokenType(maintainers, participants)

        val createTx = alice.createEvolvableToken(token, notary.legalIdentity()).getOrThrow()
        val createdToken = createTx.singleOutput<ThingContract.TokenType>()

        // Expect proposed and created tokens to match
        assertEquals(token, createdToken.state.data, "Original token did not match the published token.")

        // Expect to have one create command with maintainer signature
        val maintainerKeys = maintainers.map { it.owningKey }.toSet()
        assertEquals(maintainerKeys, createTx.requiredSigningKeys, "Must be signed by all maintainers")

        // Alice, Bob, and Charlie should record the transaction
        assertRecordsTransaction(createTx, alice, bob, charlie)
    }

    /**
     * An evolvable token should be creatable with two maintainers and two additional participants. Only maintainers must sign.
     * Participants is a superset of maintainers.
     */
    @Test
    fun `with 2 maintainers and 2 additional participants`() {
        val maintainers = listOf(alice, bob).map { it.legalIdentity() }
        val participants = maintainers + listOf(charlie, denise).map { it.legalIdentity() }
        val token = ThingContract.TokenType(maintainers, participants)

        val createTx = alice.createEvolvableToken(token, notary.legalIdentity()).getOrThrow()
        val createdToken = createTx.singleOutput<ThingContract.TokenType>()

        // Expect proposed and created tokens to match
        assertEquals(token, createdToken.state.data, "Original token did not match the published token.")

        // Expect to have one create command with maintainer signature
        val maintainerKeys = maintainers.map { it.owningKey }.toSet()
        assertEquals(maintainerKeys, createTx.requiredSigningKeys, "Must be signed by all maintainers")

        // Alice, Bob, Charlie, and Denise should record the transaction
        assertRecordsTransaction(createTx, alice, bob, charlie, denise)
    }

    /**
     * An evolvable token should *not* be creatable if not all maintainers are participants.
     * Participants is *not* a superset of maintainers.
     */
    @Test
    fun `fails if participants is not a superset of maintainers`() {
        val maintainers = listOf(alice).map { it.legalIdentity() }
        val participants = listOf(charlie).map { it.legalIdentity() }
        val token = ThingContract.TokenType(maintainers, participants)

        assertFails("When creating an evolvable token all maintainers must also be participants.") {
            alice.createEvolvableToken(token, notary.legalIdentity()).getOrThrow()
        }
    }

    class ThingContract : EvolvableTokenContract(), Contract {

        override fun additionalCreateChecks(tx: LedgerTransaction) {
            requireThat {
                // No additional checks
            }
        }

        override fun additionalUpdateChecks(tx: LedgerTransaction) {
            requireThat {
                // No additional checks
            }
        }

        data class TokenType(
                override val maintainers: List<Party>,
                override val participants: List<Party>,
                override val linearId: UniqueIdentifier = UniqueIdentifier()
        ) : EvolvableTokenType() {
            override val displayTokenSize: BigDecimal = BigDecimal.ZERO
        }
    }

}