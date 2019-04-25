package com.r3.corda.sdk.token.workflow

import com.r3.corda.sdk.token.contracts.states.NonFungibleToken
import com.r3.corda.sdk.token.contracts.types.TokenPointer
import com.r3.corda.sdk.token.workflow.states.DiamondGradingReport
import net.corda.core.contracts.LinearState
import net.corda.core.contracts.StateAndRef
import net.corda.core.node.services.Vault
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.utilities.getOrThrow
import net.corda.testing.node.StartedMockNode
import org.junit.Ignore
import org.junit.Test
import java.time.Duration
import kotlin.test.assertEquals

/**
 * This test suite is intended to test and demonstrate common scenarios for working with evolvable token types and
 * non-fungible (discrete) holdable tokens.
 */
class DiamondWithTokenScenarioTests : JITMockNetworkTests() {

    private val gic: StartedMockNode get() = node("Gemological Institute of Corda (GIC)")
    private val denise: StartedMockNode get() = node("Denise")
    private val alice: StartedMockNode get() = node("Alice")
    private val bob: StartedMockNode get() = node("Bob")
    private val charlie: StartedMockNode get() = node("Charlie")

    /**
     * This scenario creates a new evolvable token type and issues holdable tokens. It is intended to demonstrate a
     * fairly typical use case for creating evolvable token types and for issuing discrete (non-fungible) holdable tokens.
     *
     * 1. GIC creates (publishes) the diamond grading report
     * 2. Denise (the diamond dealer) issues a holdable, discrete (non-fungible) token to Alice
     * 3. Alice transfers the discrete token to Bob
     * 4. Bob transfers the discrete token to Charlie
     * 5. GIC amends (updates) the grading report
     * 6. Charlie redeems the holdable token with Denise (perhaps Denise buys back the diamond and plans to issue a new
     *    holdable token as replacement)
     */
    @Test
    fun `lifecycle example`() {
        // STEP 01: GIC publishes the diamond certificate
        // GIC publishes and shares with Denise
        val diamond = DiamondGradingReport("1.0", DiamondGradingReport.ColorScale.A, DiamondGradingReport.ClarityScale.A, DiamondGradingReport.CutScale.A, gic.legalIdentity(), denise.legalIdentity())
        val publishDiamondTx = gic.createEvolvableToken(diamond, notary.legalIdentity()).getOrThrow()
        val publishedDiamond = publishDiamondTx.singleOutput<DiamondGradingReport>()
        assertEquals(diamond, publishedDiamond.state.data, "Original diamond did not match the published diamond.")
        denise.watchForTransaction(publishDiamondTx).getOrThrow(Duration.ofSeconds(5))

        // STEP 02: Denise creates ownership token
        // Denise issues the token to Alice
        val diamondPointer = publishedDiamond.state.data.toPointer<DiamondGradingReport>()
        val issueTokenTx = denise.issueTokens(
                token = diamondPointer,
                issueTo = alice,
                notary = notary,
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

        // STEP 04: Bob transfers ownership to Charlie
        // Continuing the chain of sale
        val moveTokenToCharlieTx = bob.moveTokens(diamondPointer, charlie, anonymous = true).getOrThrow(Duration.ofSeconds(5))
        assertRecordsTransaction(moveTokenToCharlieTx, bob, charlie)
        assertNotRecordsTransaction(moveTokenToCharlieTx, gic, denise, alice)

        // STEP 05: GIC amends (updates) the grading report
        // This should be reflected to the report participants
        val updatedDiamond = publishedDiamond.state.data.copy(color = DiamondGradingReport.ColorScale.B)
        val updateDiamondTx = gic.updateEvolvableToken(publishedDiamond, updatedDiamond).getOrThrow(Duration.ofSeconds(5))
        assertRecordsTransaction(updateDiamondTx, gic, denise, bob, charlie)
        assertNotRecordsTransaction(updateDiamondTx, alice)

        // STEP 06: Charlie redeems the token with Denise
        // This should exit the holdable token
        val charlieDiamond = moveTokenToCharlieTx.tx.outputsOfType<NonFungibleToken<TokenPointer<DiamondGradingReport>>>().first()
        val redeemDiamondTx = charlie.redeemTokens(charlieDiamond.token.tokenType, denise).getOrThrow(Duration.ofSeconds(5))
        assertRecordsTransaction(redeemDiamondTx, charlie, denise)
        assertNotRecordsTransaction(redeemDiamondTx, gic, alice, bob)

        // FINAL POSITIONS

        // GIC, Denise, Bob and Charlie have the latest evolvable token; Alice does not
        val newDiamond = updateDiamondTx.singleOutput<DiamondGradingReport>()
        assertHasStateAndRef(newDiamond, gic, denise, bob, charlie)
        assertNotHasStateAndRef(newDiamond, alice)

        // Alice has an outdated (and unconsumed) evolvable token; GIC, Denise, Bob and Charlie do not
        val oldDiamond = publishDiamondTx.singleOutput<DiamondGradingReport>()
        assertHasStateAndRef(oldDiamond, alice)
        assertNotHasStateAndRef(oldDiamond, gic, denise, bob, charlie)

        // No one has nonfungible (discrete) tokens
        assertNotHasStateAndRef(issueTokenTx.singleOutput<NonFungibleToken<TokenPointer<DiamondGradingReport>>>(), gic, denise, alice, bob, charlie)
        assertNotHasStateAndRef(moveTokenToBobTx.singleOutput<NonFungibleToken<TokenPointer<DiamondGradingReport>>>(), gic, denise, alice, bob, charlie)
        assertNotHasStateAndRef(moveTokenToCharlieTx.singleOutput<NonFungibleToken<TokenPointer<DiamondGradingReport>>>(), gic, denise, alice, bob, charlie)
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