package com.r3.corda.lib.tokens.contracts

import com.r3.corda.lib.tokens.contracts.types.TokenType
import net.corda.core.contracts.Amount

class CommonTokens {

	companion object {
		val USD = TokenType("USD", 2)
		val GBP = TokenType("GBP", 2)
	}

}

fun Number.ofType(tt: TokenType): Amount<TokenType> {
	return Amount(this.toLong(), tt)
}