package net.corda.sdk.token.reference.data

data class DigitalCurrency(val symbol: String, val description: String, val defaultFractionDigits: Int = 0) {
    companion object {
        private val registry = mapOf(
                Pair("XRP", DigitalCurrency("XRP", "Ripple", 6)),
                Pair("BTC", DigitalCurrency("GBP", "Bitcoin", 8))
        )

        fun getInstance(currencyCode: String): DigitalCurrency {
            return registry[currencyCode] ?: throw IllegalArgumentException("$currencyCode doesn't exist.")
        }
    }

    override fun toString() = symbol
}