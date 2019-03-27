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

    @Test
    fun `happy path`() {
        // STEP 01: GIC publishes the diamond certificate
        // GIC publishes and shares with Denise
        val diamond = DiamondCertificate.State("1.0", DiamondCertificate.ColorScale.D, DiamondCertificate.ClarityScale.FL, DiamondCertificate.CutScale.EXCELLENT, gic.legalIdentity(), denise.legalIdentity())
        val publishDiamondTx = gic.createEvolvableToken(diamond, NOTARY.legalIdentity()).getOrThrow()
        val publishedDiamond = publishDiamondTx.singleOutput<DiamondCertificate.State>()
        assertEquals(diamond, publishedDiamond.state.data, "Original diamond did not match the published diamond.")
        denise.watchForTransaction(publishDiamondTx).getOrThrow(Duration.ofSeconds(5))

        // STEP 02: Denise creates ownership token
        // Denise issues the token to Alice
        val diamondPointer = publishedDiamond.state.data.toPointer<DiamondCertificate.State>()
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
    }

    @Test @Ignore
    fun `denise cannot issue multiple ownership tokens`() {
        // STEP 01: GIC publishes the certificate

        // STEP 02: Denise issues an ownership token

        // STEP 03: Denise issues another ownership token
        // Expect to fail here!
    }

    @Test @Ignore
    fun `alice cannot issue a new ownership token`() {
        // STEP 01: GIC publishes the certificate

        // STEP 02: Denise issues an ownership token

        // STEP 03: Denise transfers ownership to Alice

        // STEP 04: Alice issues another ownership token
        // Expect to fail here!
    }

    class DiamondCertificate : EvolvableTokenContract(), Contract {

        override fun additionalCreateChecks(tx: LedgerTransaction) {
            requireThat {
                val outputDiamond = tx.outputsOfType<State>().first()
                "Diamond's carat weight must be greater than 0 (zero)" using (outputDiamond.caratWeight > BigDecimal.ZERO)
            }
        }

        override fun additionalUpdateChecks(tx: LedgerTransaction) {
            requireThat {
                val inDiamond = tx.outputsOfType<State>().first()
                val outDiamond = tx.outputsOfType<State>().first()
                "Diamond's carat weight may not be changed" using (inDiamond.caratWeight == outDiamond.caratWeight)
                "Diamond's color may not be changed" using (inDiamond.color == outDiamond.color)
                "Diamond's clarity may not be changed" using (inDiamond.clarity == outDiamond.clarity)
                "Diamond's cut may not be changed" using (inDiamond.cut == outDiamond.cut)
            }
        }

        @CordaSerializable
        enum class ColorScale { D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T, U, V, W, X, Y, Z }

        @CordaSerializable
        enum class ClarityScale { FL, IF, VVS1, VVS2, VS1, VS2, SI1, SI2, I1, I2, I3 }

        @CordaSerializable
        enum class CutScale { EXCELLENT, VERY_GOOD, GOOD, FAIR, POOR }

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