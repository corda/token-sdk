package com.r3.corda.sdk.token.workflow.contracts

import com.r3.corda.sdk.token.contracts.EvolvableTokenContract
import com.r3.corda.sdk.token.workflow.states.DiamondGradingReport
import net.corda.core.contracts.Contract
import net.corda.core.contracts.requireThat
import net.corda.core.transactions.LedgerTransaction
import java.math.BigDecimal

/**
 * The [DiamondGradingReportContract] is inspired by the grading reports issued by the Gemological Institute of America
 * (GIA). For more excellent information on diamond grading, please see the (GIA's website)[http://www.gia.edu].
 */
class DiamondGradingReportContract : EvolvableTokenContract(), Contract {

    override fun additionalCreateChecks(tx: LedgerTransaction) {
        val outputDiamond = tx.outputsOfType<DiamondGradingReport>().first()
        requireThat {
            "Diamond's carat weight must be greater than 0 (zero)" using (outputDiamond.caratWeight > BigDecimal.ZERO)
        }
    }

    override fun additionalUpdateChecks(tx: LedgerTransaction) {
        val inDiamond = tx.outputsOfType<DiamondGradingReport>().first()
        val outDiamond = tx.outputsOfType<DiamondGradingReport>().first()
        requireThat {
            "Diamond's carat weight may not be changed" using (inDiamond.caratWeight == outDiamond.caratWeight)
            "Diamond's color may not be changed" using (inDiamond.color == outDiamond.color)
            "Diamond's clarity may not be changed" using (inDiamond.clarity == outDiamond.clarity)
            "Diamond's cut may not be changed" using (inDiamond.cut == outDiamond.cut)
        }
    }


}