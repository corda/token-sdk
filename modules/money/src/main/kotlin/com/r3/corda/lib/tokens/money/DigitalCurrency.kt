package com.r3.corda.lib.tokens.money

import com.r3.corda.lib.tokens.contracts.types.TokenType
import java.util.*

/** A representation of digital money. This implementation somewhat mirrors that of [Currency]. */
class DigitalCurrency {
    companion object {
        private val registry = mapOf(
                Pair("XRP", TokenType("Ripple", 6)),
                Pair("BTC", TokenType("Bitcoin", 8)),
                Pair("ETH", TokenType("Ethereum", 18)),
                Pair("DOGE", TokenType("Dogecoin", 8))
        )

        fun getInstance(currencyCode: String): TokenType {
            return registry[currencyCode] ?: throw IllegalArgumentException("$currencyCode doesn't exist.")
        }
    }
}