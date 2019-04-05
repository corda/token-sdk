package com.r3.corda.sdk.token.workflow.statesAndContracts

import com.r3.corda.sdk.token.contracts.EvolvableTokenContract
import com.r3.corda.sdk.token.contracts.states.EvolvableTokenType
import net.corda.core.contracts.Contract
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.contracts.requireThat
import net.corda.core.identity.Party
import net.corda.core.serialization.CordaSerializable
import net.corda.core.transactions.LedgerTransaction
import java.math.BigDecimal

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