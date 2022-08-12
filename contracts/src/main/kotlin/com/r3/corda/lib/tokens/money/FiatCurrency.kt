package com.r3.corda.lib.tokens.money

import com.r3.corda.lib.tokens.contracts.types.TokenType
import java.util.*

/**
 * Represents utilities for working with Fiat currencies.
 */
object FiatCurrency {

	/**
	 * Creates a [TokenType] from the specified currency code.
	 *
	 * @param currencyCode The currency code that represents the currency from which to create a new [TokenType] instance.
	 * @return Returns a new [TokenType] instance representing the specified currency code, and the currency's default fraction digits.
	 */
	@JvmStatic
	fun getInstance(currencyCode: String): TokenType {
		val currency = Currency.getInstance(currencyCode)
		return TokenType(currency.currencyCode, currency.defaultFractionDigits)
	}
}
