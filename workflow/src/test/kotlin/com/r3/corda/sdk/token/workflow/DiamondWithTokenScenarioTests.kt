package com.r3.corda.sdk.token.workflow

import com.r3.corda.sdk.token.workflow.statesAndContracts.DiamondGradingReport
import net.corda.core.utilities.getOrThrow
import net.corda.testing.node.StartedMockNode
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import java.time.Duration
import kotlin.test.assertEquals

/**
 * This test suite is intended to test and demonstrate common scenarios for working with evolvable token types and
 * non-fungible (discrete) holdable tokens.
 */
class DiamondWithTokenScenarioTests : MockNetworkTest("Gemological Institute of Corda (GIC)", "Denise", "Alice", "Bob", "Charlie") {

    lateinit var gic: StartedMockNode
    lateinit var denise: StartedMockNode
    lateinit var alice: StartedMockNode
    lateinit var bob: StartedMockNode
    lateinit var charlie: StartedMockNode

    @Before
    override fun initialiseNodes() {
        gic = nodesByName.getValue("Gemological Institute of Corda (GIC)")
        denise = nodesByName.getValue("Denise")
        alice = nodesByName.getValue("Alice")
        bob = nodesByName.getValue("Bob")
        charlie = nodesByName.getValue("Charlie")
    }

    /**
     * This scenario creates a new evolvable token type and issues holdable tokens. It is intended to demonstrate a
     * fairly typical use case for creating evolvable token types and for issuing discrete (non-fungible) holdable tokens.
     *
     * 1. GIC creates (publishes) the diamond grading report
     * 2. Denise (the diamond dealer) issues a holdable, discrete (non-fungible) token to Alice
     * 3. Alice transfers the discrete token to Bob
     * 4. Bob transfers the discrete token to Charles
     * 5. GIC amends (updates) the grading report
     * 6. Charles redeems the holdable token with Denise (perhaps Denise buys back the diamond and plans to issue a new
     *    holdable token as replacement)
     *
     * TODO Implement step 6
     */
    @Test
    fun `lifecycle example`() {
        // STEP 01: GIC publishes the diamond certificate
        // GIC publishes and shares with Denise
        val diamond = DiamondGradingReport.State("1.0", DiamondGradingReport.ColorScale.A, DiamondGradingReport.ClarityScale.A, DiamondGradingReport.CutScale.A, gic.legalIdentity(), denise.legalIdentity())
        val publishDiamondTx = gic.createEvolvableToken(diamond, NOTARY.legalIdentity()).getOrThrow()
        val publishedDiamond = publishDiamondTx.singleOutput<DiamondGradingReport.State>()
        assertEquals(diamond, publishedDiamond.state.data, "Original diamond did not match the published diamond.")
        denise.watchForTransaction(publishDiamondTx).getOrThrow(Duration.ofSeconds(5))

        // STEP 02: Denise creates ownership token
        // Denise issues the token to Alice
        val diamondPointer = publishedDiamond.state.data.toPointer<DiamondGradingReport.State>()
        val issueTokenTx = denise.issueTokens(
                token = diamondPointer,
                owner = alice,
                notary = NOTARY,
                anonymous = true
        ).getOrThrow()
        // GIC should *not* receive a copy of this issuance
        assertRecordsTransaction(issueTokenTx, alice)
        assertNotRecordsTransaction(issueTokenTx, gic)

        // STEP 03: Alice transfers ownership to Bob
        // Continuing the chain of sale
        val moveTokenToBobTx = alice.moveTokens(diamondPointer, bob, anonymous = true).getOrThrow(Duration.ofSeconds(5))
        assertRecordsTransaction(moveTokenToBobTx, alice, bob)
        assertNotRecordsTransaction(moveTokenToBobTx, gic, denise)

        // STEP 04: Bob transfers ownership to Charles
        // Continuing the chain of sale
        val moveTokenToCharlieTx = bob.moveTokens(diamondPointer, charlie, anonymous = true).getOrThrow(Duration.ofSeconds(5))
        assertRecordsTransaction(moveTokenToCharlieTx, bob, charlie)
        assertNotRecordsTransaction(moveTokenToCharlieTx, gic, denise, alice)

        // STEP 05: GIC amends (updates) the grading report
        // This should be reflected to the report participants
        val updatedDiamond = publishedDiamond.state.data.copy(color = DiamondGradingReport.ColorScale.B)
        val updateDiamondTx = gic.updateEvolvableToken(publishedDiamond, updatedDiamond).getOrThrow(Duration.ofSeconds(5))
        // TODO Use a distribution group / subscription to inform Charlie of a change
        assertRecordsTransaction(updateDiamondTx, gic, denise) // Should include Charlie
        assertNotRecordsTransaction(updateDiamondTx, alice, bob)

        // STEP 06: Charles redeems the token with Denise
        // This should exit the holdable token
    }

    /**
     * This scenario creates multiple evolvable token types in a single transaction.
     *
     * 1. GIC creates (publishes) 3 diamond grading reports
     */
    @Test
    @Ignore
    fun `create multiple grading reports`() {

    }

    /**
     * This scenario creates a multiple evolvable token types in a single transaction, and then issues multiple holding
     * tokens.
     *
     * 1. GIC creates (publishes) 3 diamond grading reports
     * 2. Denise (the diamond dealer) issues 2 holdable tokens to self (perhaps as inventory)
     */
    @Test
    @Ignore
    fun `issue multiple grading report tokens`() {

    }

    /**
     * This scenario creates a new evolvable token type and issues holdable tokens to self.
     *
     * 1. GIC creates (publishes) the diamond grading report
     * 2. Denise (the diamond dealer) issues a holdable, discrete (non-fungible) token to herself (perhaps as inventory)
     */
    @Test
    @Ignore
    fun `issue a grading report token to self`() {

    }

    /**
     * This scenario creates a new evolvable token type, moves it around, and then issues an update. In this case, only
     * the current holder (not past holders) should receive an update.
     *
     * 1. GIC creates (publishes) the diamond grading report
     * 2. Denise (the diamond dealer) issues a holdable, discrete (non-fungible) token to Alice
     * 3. Alice transfers the discrete token to Bob
     * 4. GIC updates (amends) the grading report
     */
    @Test
    @Ignore
    fun `update a grading report and inform token holders`() {

    }

    /**
     * This scenario tests that the token issuer cannot issue two holdable tokens. In practice, this may be challenging
     * to enforce.
     *
     * 1. GIC creates (publishes) the diamond grading report
     * 2. Denise (the diamond dealer) issues a holdable, discrete (non-fungible) token to Alice
     * 3. Denise then issues a new holdable, discrete (non-fungible) token to Bob
     */
    @Test
    @Ignore
    fun `denise cannot issue multiple ownership tokens`() {
        // STEP 01: GIC publishes the certificate

        // STEP 02: Denise issues an ownership token

        // STEP 03: Denise issues another ownership token
    }

    /**
     * This scenario tests that a token holder should not (really) issue a new holdable token. However, in practice this
     * may be challenging to enforce; rather, participants should consider if they trust the token issuer.
     *
     * 1. GIC creates (publishes) the diamond grading report
     * 2. Denise (the diamond dealer) issues a holdable, discrete (non-fungible) token to Alice
     * 3. Alice then issues a new holdable, discrete (non-fungible) token to Bob
     */
    @Test
    @Ignore
    fun `alice cannot issue a new ownership token`() {
        // STEP 01: GIC publishes the certificate

        // STEP 02: Denise issues an ownership token

        // STEP 03: Denise transfers ownership to Alice

        // STEP 04: Alice issues another ownership token
    }

}