package com.r3.corda.lib.tokens.testing.contracts

import net.corda.core.contracts.Contract
import net.corda.core.transactions.LedgerTransaction

open class DodgeTokenContract : Contract {

	companion object {
		@JvmStatic
		val ID: String = this::class.java.enclosingClass.canonicalName
	}

	override fun verify(tx: LedgerTransaction) {}
}