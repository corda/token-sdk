package net.corda.sdk.token.contracts

import net.corda.core.contracts.Contract
import net.corda.core.transactions.LedgerTransaction

class TokenContract : Contract {

    companion object {
        val contractId = this::class.java.enclosingClass.canonicalName
    }

    override fun verify(tx: LedgerTransaction) {

    }

}