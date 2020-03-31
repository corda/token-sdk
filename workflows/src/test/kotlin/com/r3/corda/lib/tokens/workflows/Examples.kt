package com.r3.corda.lib.tokens.workflows

import com.r3.corda.lib.tokens.contracts.states.AbstractToken
import com.r3.corda.lib.tokens.contracts.states.FungibleToken
import com.r3.corda.lib.tokens.contracts.states.NonFungibleToken
import com.r3.corda.lib.tokens.contracts.types.IssuedTokenType
import com.r3.corda.lib.tokens.contracts.types.TokenPointer
import com.r3.corda.lib.tokens.contracts.types.TokenType
import com.r3.corda.lib.tokens.contracts.utilities.heldBy
import com.r3.corda.lib.tokens.contracts.utilities.issuedBy
import com.r3.corda.lib.tokens.contracts.utilities.of
import com.r3.corda.lib.tokens.money.GBP
import com.r3.corda.lib.tokens.testing.states.House
import com.r3.corda.lib.tokens.testing.states.TestEvolvableTokenType
import com.r3.corda.lib.tokens.workflows.utilities.heldBy
import net.corda.core.contracts.Amount
import net.corda.core.contracts.UniqueIdentifier
import org.junit.Test
import kotlin.test.assertEquals

class UsabilityTests : LedgerTestWithPersistence() {

	@Test
	fun `create a fungible token of some token type`() {
		// Some amount of GBP issued by the Bank of England and held by Alice.
		val tenPoundsIssuedByIssuerHeldByAlice: FungibleToken = 10.GBP issuedBy ISSUER.party heldBy ALICE.party
		// Test everything is assigned correctly.
		assertEquals(GBP, tenPoundsIssuedByIssuerHeldByAlice.amount.token.tokenType)
		assertEquals(1000, tenPoundsIssuedByIssuerHeldByAlice.amount.quantity)
		assertEquals(ISSUER.party, tenPoundsIssuedByIssuerHeldByAlice.amount.token.issuer)
		assertEquals(ALICE.party, tenPoundsIssuedByIssuerHeldByAlice.holder)
		println(tenPoundsIssuedByIssuerHeldByAlice)
	}

	@Test
	fun `create a (non) fungible token of some token pointer type`() {
		val house = House("24 Leinster Gardens, Bayswater, London", 900_000.GBP, listOf(BOB.party), linearId = UniqueIdentifier())
		val housePointer: TokenPointer<House> = house.toPointer()
		val houseIssuedByBob: IssuedTokenType = housePointer issuedBy BOB.party
		houseIssuedByBob heldBy ALICE.party

		// Now we want to do fractional ownership in this house...
		// Redeem the NonFungibleToken and reissue it as an FungibleToken
		100 of housePointer issuedBy BOB.party heldBy ALICE.party
	}

	object PTK : TokenType("PTK", 9) {
		val description: String get() = "Pitch Token Classic"
	}

	@Test
	fun `usability test`() {
		// Create issued TokenType.
		val gbp: TokenType = GBP
		val tenGbp: Amount<TokenType> = 10 of gbp
		val tenIssuedGbp: Amount<IssuedTokenType> = tenGbp issuedBy ISSUER.party
		val tendHeldGbp: FungibleToken = tenIssuedGbp heldBy ALICE.party
		// The class should be TokenType and identifier "GBP".
		assertEquals("TokenType", tenIssuedGbp.token.tokenType.tokenClass.simpleName)
		assertEquals("GBP", tenIssuedGbp.token.tokenType.tokenIdentifier)
		// Create a custom TokenType -> PTK is a sub-class of TokenType. It has extra properties.
		val fivePtk = 5 of PTK
		assertEquals("PTK", fivePtk.token.tokenClass.simpleName)
		// We can cast the TokenType to PTK because we just created the above types - we know what the TokenType is.
		val ptk = fivePtk.token as PTK
		assertEquals("Pitch Token Classic", ptk.description)
		val issuedPtk = fivePtk issuedBy ISSUER.party
		val heldPtk = issuedPtk heldBy ALICE.party
		assertEquals("PTK(tokenIdentifier='PTK', fractionDigits=9)", heldPtk.amount.token.tokenType.toString())
		// Create a new Evolvable token type and convert it to a pointer.
		val testType = TestEvolvableTokenType(listOf(ISSUER.party))
		val testTypePointer: TokenPointer<TestEvolvableTokenType> = testType.toPointer()
		// Create an issued token type from the token pointer.
		val somePointerType: IssuedTokenType = testTypePointer issuedBy ISSUER.party
		assertEquals("TestEvolvableTokenType", somePointerType.tokenType.tokenClass.simpleName)
		assertEquals(testType.linearId.id.toString(), somePointerType.tokenType.tokenIdentifier)
		val heldPointer: NonFungibleToken = somePointerType heldBy BOB.party
		// Create a list of tokens of different types with different token types and treat them differently depending
		// on the type of the TokenType; pointer, regular or custom.
		val tokens: List<AbstractToken> = listOf(heldPointer, heldPtk, tendHeldGbp, heldPtk, heldPointer)
		assertEquals(2, tokens.count { it.tokenType.isPointer() })
		assertEquals(2, tokens.count { it.tokenType.isCustomTokenType() })
		assertEquals(1, tokens.count { it.tokenType.isRegularTokenType() })
	}

}
