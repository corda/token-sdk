package net.corda.sdk.token.money

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import java.math.BigDecimal
import java.util.*

/**
 * A representation of digital money. This implementation somewhat mirrors that of [Currency].
 */
data class DigitalCurrency(
        override val symbol: String,
        override val description: String,
        private val defaultFractionDigits: Int = 0
) : Money() {
    override val displayTokenSize: BigDecimal get() = BigDecimal.ONE.scaleByPowerOfTen(-defaultFractionDigits)

    @JsonCreator
    constructor(@JsonProperty("currencyCode") currencyCode: String) : this(
            getInstance(currencyCode).symbol,
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

    override fun toString() = symbol
}