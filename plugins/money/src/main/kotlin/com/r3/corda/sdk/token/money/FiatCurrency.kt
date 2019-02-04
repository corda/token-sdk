package net.corda.sdk.token.money

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import java.math.BigDecimal
import java.util.*

/**
 * This is a temporary class for now while we find a better class for FiatCurrency.
 * A wrapper for money as the Java Currency type doesn't implement our interfaces and adds specificity around the
 * money being fiat. Note that fiat money backed stable coins such as Tether could be classed as either a
 * [FiatCurrency] coupled with the Tether issuer party, or a [DigitalCurrency] ("USDT") coupled with the Tether issuer.
 * TODO: Replace with a "better" implementation.
 * TODO: Potentially merge this with DigitalCurrency in the future. All currencies are tokenised on the ledger.
 */
class FiatCurrency(private val currency: Currency) : Money() {
    override val symbol: String get() = currency.currencyCode
    override val description: String get() = currency.displayName
    override val displayTokenSize: BigDecimal get() = BigDecimal.ONE.scaleByPowerOfTen(-currency.defaultFractionDigits)
    override fun toString(): String = symbol

    @JsonCreator
    constructor(@JsonProperty("currencyCode") currencyCode: String) : this(Currency.getInstance(currencyCode))

    companion object {
        // Uses the java money registry.
        fun getInstance(currencyCode: String) = FiatCurrency(Currency.getInstance(currencyCode))
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is FiatCurrency) return false
        if (currency != other.currency) return false
        return true
    }

    override fun hashCode(): Int {
        return currency.hashCode()
    }

}