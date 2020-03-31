package com.r3.corda.lib.tokens.workflows

import com.r3.corda.lib.tokens.contracts.utilities.*
import com.r3.corda.lib.tokens.money.GBP
import net.corda.core.contracts.StateAndRef
import net.corda.core.contracts.StateRef
import net.corda.core.contracts.TransactionState
import net.corda.core.crypto.SecureHash
import net.corda.testing.core.TestIdentity
import org.junit.Test

class TransactionUtilityTests {
	val ALICE: TestIdentity = TestIdentity.fresh("ALICE")
	val ISSUER_ONE: TestIdentity = TestIdentity.fresh("ISSUER ONE")
	val ISSUER_TWO: TestIdentity = TestIdentity.fresh("ISSUER TWO")
	val NOTARY: TestIdentity = TestIdentity.fresh("NOTARY")

	@Test
	fun `sum issued tokens with different issuers`() {
		val a = 10.GBP issuedBy ISSUER_ONE.party heldBy ALICE.party
		val b = 25.GBP issuedBy ISSUER_ONE.party heldBy ALICE.party
		val c = 40.GBP issuedBy ISSUER_TWO.party heldBy ALICE.party
		val d = 55.GBP issuedBy ISSUER_TWO.party heldBy ALICE.party
		val tokens = listOf(a, b, c, d)
		val result = tokens.filterTokensByIssuer(ISSUER_ONE.party).sumTokenStatesOrThrow()
		println(result)
		val tokenStateAndRefs = tokens.map {
			StateAndRef(TransactionState(it, notary = NOTARY.party), StateRef(SecureHash.zeroHash, 0))
		}
		val resultTwo = tokenStateAndRefs.filterTokenStateAndRefsByIssuer(ISSUER_TWO.party).sumTokenStateAndRefs()
		println(resultTwo)
	}
}