package com.r3.corda.lib.tokens.money

import com.r3.corda.lib.tokens.contracts.types.TokenType

/**
 * Represents utilities for working with digital currencies.
 */
object DigitalCurrency {

	/**
	 * Creates a [TokenType] from the specified currency code.
	 *
	 * @param currencyCode The currency code that represents the currency from which to create a new [TokenType] instance.
	 * @return Returns a new [TokenType] instance representing the specified currency code, and the currency's default fraction digits.
	 */
	@JvmStatic
	fun getInstance(currencyCode: String): TokenType {
		return registry[currencyCode] ?: throw IllegalArgumentException("$currencyCode doesn't exist.")
	}

	private val registry = mapOf(
		Pair("XRP", TokenType("Ripple", 6)),
		Pair("BTC", TokenType("Bitcoin", 8)),
		Pair("ETH", TokenType("Ethereum", 18)),
		Pair("DOGE", TokenType("Dogecoin", 8))
	)
}
