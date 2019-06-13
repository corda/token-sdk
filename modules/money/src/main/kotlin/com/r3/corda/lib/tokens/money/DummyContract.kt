package com.r3.corda.lib.tokens.money

import net.corda.core.contracts.Contract
import net.corda.core.transactions.LedgerTransaction

/**
 * This is included so that the CorDapp scanner auto-magically includes this JAR in the attachment store. It will remain
 * until CorDapp dependencies are properly handled in Corda 5.0.
 */
class DummyContract : Contract {
    override fun verify(tx: LedgerTransaction) = Unit
}