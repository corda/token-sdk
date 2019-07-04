package com.r3.corda.lib.tokens.money

import com.r3.corda.lib.tokens.contracts.types.TokenType
import java.util.*

/** A representation of digital money. This implementation somewhat mirrors that of [Currency]. */
class DigitalCurrency(tokenIdentifier: String, fractionDigits: Int) : TokenType(tokenIdentifier, fractionDigits) {
    override fun toString(): String = tokenIdentifier

    companion object {
        private val registry = mapOf(
                Pair("XRP", DigitalCurrency("Ripple", 6)),
                Pair("BTC", DigitalCurrency("Bitcoin", 8)),
                Pair("ETH", DigitalCurrency("Ethereum", 18)),
                Pair("DOGE", DigitalCurrency("Dogecoin", 8))
        )

        fun getInstance(currencyCode: String): DigitalCurrency {
            return registry[currencyCode] ?: throw IllegalArgumentException("$currencyCode doesn't exist.")
        }
    }
}