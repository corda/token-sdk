package com.r3.corda.sdk.token.workflow

import com.r3.corda.sdk.token.contracts.EvolvableTokenContract
import com.r3.corda.sdk.token.contracts.states.EvolvableTokenType
import net.corda.core.contracts.Contract
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.contracts.requireThat
import net.corda.core.identity.Party
import net.corda.core.serialization.CordaSerializable
import net.corda.core.transactions.LedgerTransaction
import net.corda.core.utilities.getOrThrow
import net.corda.testing.node.StartedMockNode
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import java.math.BigDecimal
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertFails

/**
 * This test suite is intended to test and demonstrate common scenarios for working with evolvable token types and
 * non-fungible (discrete) holdable tokens.
 */
class DiamondWithTokenScenarioTests : MockNetworkTest("Gemological Institute of Corda (GIC)", "Denise", "Alice", "Bob", "Charles") {

    lateinit var gic: StartedMockNode
    lateinit var denise: StartedMockNode
    lateinit var alice: StartedMockNode
    lateinit var bob: StartedMockNode
    lateinit var charles: StartedMockNode

    @Before
    override fun initialiseNodes() {
        gic = nodesByName.getValue("Gemological Institute of Corda (GIC)")
        denise = nodesByName.getValue("Denise")
        alice = nodesByName.getValue("Alice")
        bob = nodesByName.getValue("Bob")
        charles = nodesByName.getValue("Charles")
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
        alice.watchForTransaction(issueTokenTx).getOrThrow(Duration.ofSeconds(5))
        assertFails { gic.watchForTransaction(issueTokenTx.id).getOrThrow(Duration.ofSeconds(3)) }

        // STEP 03: Alice transfers ownership to Bob
        // Continuing the chain of sale
        val moveTokenToBobTx = alice.moveTokens(diamondPointer, bob, anonymous = true).getOrThrow(Duration.ofSeconds(5))
        bob.watchForTransaction(moveTokenToBobTx.id).getOrThrow(Duration.ofSeconds(5))
        assertFails { gic.watchForTransaction(moveTokenToBobTx.id).getOrThrow(Duration.ofSeconds(3)) }
        assertFails { denise.watchForTransaction(moveTokenToBobTx.id).getOrThrow(Duration.ofSeconds(3)) }

        // STEP 04: Bob transfers ownership to Charles
        // Continuing the chain of sale
        val moveTokenToCharlesTx = bob.moveTokens(diamondPointer, charles, anonymous = true).getOrThrow(Duration.ofSeconds(5))
        charles.watchForTransaction(moveTokenToCharlesTx.id).getOrThrow(Duration.ofSeconds(5))
        assertFails { gic.watchForTransaction(moveTokenToCharlesTx.id).getOrThrow(Duration.ofSeconds(3)) }
        assertFails { denise.watchForTransaction(moveTokenToCharlesTx.id).getOrThrow(Duration.ofSeconds(3)) }
        assertFails { alice.watchForTransaction(moveTokenToCharlesTx.id).getOrThrow(Duration.ofSeconds(3)) }

        // STEP 05: GIC amends (updates) the grading report
        // This should be reflected to the report participants
        val updatedDiamond = publishedDiamond.state.data.copy(color = DiamondGradingReport.ColorScale.B)
        val updateDiamondTx = gic.updateEvolvableToken(publishedDiamond, updatedDiamond).getOrThrow(Duration.ofSeconds(5))
        denise.watchForTransaction(updateDiamondTx).getOrThrow(Duration.ofSeconds(5))
        charles.watchForTransaction(updateDiamondTx).getOrThrow(Duration.ofSeconds(5))
        assertFails { alice.watchForTransaction(updateDiamondTx).getOrThrow(Duration.ofSeconds(3)) }
        assertFails { bob.watchForTransaction(updateDiamondTx).getOrThrow(Duration.ofSeconds(3)) }

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

    /**
     * The [DiamondGradingReport] is inspired by the grading reports issued by the Gemological Institute of America
     * (GIA). For more excellent information on diamond grading, please see the (GIA's website)[http://www.gia.edu].
     */
    class DiamondGradingReport : EvolvableTokenContract(), Contract {

        override fun additionalCreateChecks(tx: LedgerTransaction) {
            val outputDiamond = tx.outputsOfType<State>().first()
            requireThat {
                "Diamond's carat weight must be greater than 0 (zero)" using (outputDiamond.caratWeight > BigDecimal.ZERO)
            }
        }

        override fun additionalUpdateChecks(tx: LedgerTransaction) {
            val inDiamond = tx.outputsOfType<State>().first()
            val outDiamond = tx.outputsOfType<State>().first()
            requireThat {
                "Diamond's carat weight may not be changed" using (inDiamond.caratWeight == outDiamond.caratWeight)
                "Diamond's color may not be changed" using (inDiamond.color == outDiamond.color)
                "Diamond's clarity may not be changed" using (inDiamond.clarity == outDiamond.clarity)
                "Diamond's cut may not be changed" using (inDiamond.cut == outDiamond.cut)
            }
        }

        @CordaSerializable
        enum class ColorScale { A, B, C, D, E, F }

        @CordaSerializable
        enum class ClarityScale { A, B, C, D, E, F }

        @CordaSerializable
        enum class CutScale { A, B, C, D, E, F }

        data class State(
                val caratWeight: BigDecimal,
                val color: ColorScale,
                val clarity: ClarityScale,
                val cut: CutScale,
                val assessor: Party,
                val requester: Party,
                override val linearId: UniqueIdentifier = UniqueIdentifier()
        ) : EvolvableTokenType() {
            constructor(
                    caratWeight: String,
                    color: ColorScale,
                    clarity: ClarityScale,
                    cut: CutScale,
                    assessor: Party,
                    requester: Party,
                    linearId: UniqueIdentifier = UniqueIdentifier()) : this(BigDecimal(caratWeight), color, clarity, cut, assessor, requester, linearId)

            override val maintainers get() = listOf(assessor)

            override val participants get() = setOf(assessor, requester).toList()

            override val displayTokenSize: BigDecimal = BigDecimal.ZERO
        }
    }

}