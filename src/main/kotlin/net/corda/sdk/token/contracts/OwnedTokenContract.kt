package net.corda.sdk.token.contracts

import net.corda.core.contracts.Contract
import net.corda.core.transactions.LedgerTransaction

class OwnedTokenContract : Contract {

    companion object {
        val contractId = this::class.java.enclosingClass.canonicalName
    }

    override fun verify(tx: LedgerTransaction) {

    }

}