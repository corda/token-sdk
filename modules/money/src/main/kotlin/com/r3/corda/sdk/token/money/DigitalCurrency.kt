package com.r3.corda.sdk.token.money

import com.r3.corda.sdk.token.contracts.types.TokenType
import java.math.BigDecimal
import java.util.*

/** A representation of digital money. This implementation somewhat mirrors that of [Currency]. */
data class DigitalCurrency(
    override val tokenIdentifier: String,
    override val description: String,
    private val defaultFractionDigits: Int = 0
) : Money() {
    override val tokenClass: Class<in TokenType> get() = javaClass
    override val displayTokenSize: BigDecimal get() = BigDecimal.ONE.scaleByPowerOfTen(-defaultFractionDigits)
    override fun toString(): String = tokenIdentifier

    constructor(currencyCode: String) : this(
        getInstance(currencyCode).tokenIdentifier,
        getInstance(currencyCode).description,
        getInstance(currencyCode).defaultFractionDigits
    )

    companion object {
        private val registry = mapOf(
            Pair("XRP", DigitalCurrency("XRP", "Ripple", 6)),
            Pair("BTC", DigitalCurrency("BTC", "Bitcoin", 8)),
            Pair("ETH", DigitalCurrency("ETH", "Ethereum", 18)),
            Pair("DOGE", DigitalCurrency("DOGE", "Dogecoin", 8))
        )

        fun getInstance(currencyCode: String): DigitalCurrency {
            return registry[currencyCode] ?: throw IllegalArgumentException("$currencyCode doesn't exist.")
        }
    }
}