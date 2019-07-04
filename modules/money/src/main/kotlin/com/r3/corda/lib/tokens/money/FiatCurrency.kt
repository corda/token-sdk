package com.r3.corda.lib.tokens.money

import com.r3.corda.lib.tokens.contracts.types.TokenType
import java.util.*

/**
 * This is a temporary class while we find a better implementation. It's a wrapper for the Java Currency which doesn't
 * implement the Token SDK interfaces. It also adds specificity around the money, in question, being fiat. Note that
 * fiat money backed stable coins such as Tether would be classed as a [FiatCurrency].
 *
 * @property currency the java.util.Currency which this token type should wrap.
 */
class FiatCurrency(private val currency: Currency) : TokenType(currency.currencyCode, currency.defaultFractionDigits) {
    override fun toString(): String = tokenIdentifier

    companion object {
        // Uses the java money registry.
        fun getInstance(currencyCode: String) = FiatCurrency(Currency.getInstance(currencyCode))
    }
}