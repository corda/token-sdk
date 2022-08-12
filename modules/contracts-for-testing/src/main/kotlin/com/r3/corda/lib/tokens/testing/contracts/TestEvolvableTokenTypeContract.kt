package com.r3.corda.lib.tokens.testing.contracts

import com.r3.corda.lib.tokens.contracts.EvolvableTokenContract
import net.corda.core.contracts.Contract
import net.corda.core.transactions.LedgerTransaction

class TestEvolvableTokenTypeContract : EvolvableTokenContract(), Contract {

	companion object {
		@JvmStatic
		val ID: String = this::class.java.enclosingClass.canonicalName
	}

	override fun additionalCreateChecks(tx: LedgerTransaction) {}
	override fun additionalUpdateChecks(tx: LedgerTransaction) {}
}
