package com.r3.corda.sdk.token.workflow.states

import com.r3.corda.sdk.token.contracts.states.EvolvableTokenType
import com.r3.corda.sdk.token.workflow.contracts.DiamondGradingReportContract
import net.corda.core.contracts.BelongsToContract
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.Party
import net.corda.core.serialization.CordaSerializable
import java.math.BigDecimal

/**
 * The [DiamondGradingReport] is inspired by the grading reports issued by the Gemological Institute of America
 * (GIA). For more excellent information on diamond grading, please see the (GIA's website)[http://www.gia.edu].
 */
@BelongsToContract(DiamondGradingReportContract::class)
data class DiamondGradingReport(
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

    @CordaSerializable
    enum class ColorScale { A, B, C, D, E, F }

    @CordaSerializable
    enum class ClarityScale { A, B, C, D, E, F }

    @CordaSerializable
    enum class CutScale { A, B, C, D, E, F }

    override val maintainers get() = listOf(assessor)

    override val participants get() = setOf(assessor, requester).toList()

    override val displayTokenSize: BigDecimal = BigDecimal.ZERO
}