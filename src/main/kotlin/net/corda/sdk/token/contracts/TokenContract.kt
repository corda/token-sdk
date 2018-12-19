package net.corda.sdk.token.contracts

import net.corda.core.contracts.Contract
import net.corda.core.transactions.LedgerTransaction

/**
 * You would typically implement your own contract for evolvable token definitions.
 */
abstract class TokenContract : Contract {

    companion object {
        val contractId = this::class.java.enclosingClass.canonicalName
    }

    override fun verify(tx: LedgerTransaction) {

    }

}