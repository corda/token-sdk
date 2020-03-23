package com.r3.corda.lib.tokens.money

import com.r3.corda.lib.tokens.contracts.types.TokenType
import java.util.*

/**
 * This class is used to return a [TokenType] with the required currency code and fraction digits for fiat currencies.
 *
 * Note that the primary constructor only exists for simple and convenient access from Java code.
 *
 * @param currencyCode The currency code that represents the TokenType which the developer wishes to instantiate.
 */
class FiatCurrency(currencyCode: String): TokenType(
        getInstance(currencyCode).tokenIdentifier,
        getInstance(currencyCode).fractionDigits
) {
    companion object {
        // Uses the java money registry.
        @JvmStatic
        fun getInstance(currencyCode: String): TokenType {
            val currency = Currency.getInstance(currencyCode)
            return TokenType(currency.currencyCode, currency.defaultFractionDigits)
        }
    }
}
