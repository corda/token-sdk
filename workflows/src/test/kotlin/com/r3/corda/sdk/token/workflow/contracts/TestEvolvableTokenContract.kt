package com.r3.corda.sdk.token.workflow.contracts

import com.r3.corda.sdk.token.contracts.EvolvableTokenContract
import net.corda.core.contracts.Contract
import net.corda.core.contracts.requireThat
import net.corda.core.transactions.LedgerTransaction

class TestEvolvableTokenContract : EvolvableTokenContract(), Contract {

    companion object {
        val ID: String = this::class.java.enclosingClass.canonicalName
    }

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

}