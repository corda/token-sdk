package net.corda.sdk.token.types.money

import net.corda.core.contracts.Amount
import net.corda.sdk.token.utilities.AMOUNT

/** Helpers for creating amounts of fixed token definitions. */

// Sterling.
val GBP = FiatCurrency.getInstance("GBP")

fun GBP(amount: Int): Amount<FiatCurrency> = AMOUNT(amount, GBP)
fun GBP(amount: Long): Amount<FiatCurrency> = AMOUNT(amount, GBP)
fun GBP(amount: Double): Amount<FiatCurrency> = AMOUNT(amount, GBP)
val Int.GBP: Amount<FiatCurrency> get() = GBP(this)
val Long.GBP: Amount<FiatCurrency> get() = GBP(this)
val Double.GBP: Amount<FiatCurrency> get() = GBP(this)

// US Dollar.
val USD = FiatCurrency.getInstance("USD")

fun USD(amount: Int): Amount<FiatCurrency> = AMOUNT(amount, USD)
fun USD(amount: Long): Amount<FiatCurrency> = AMOUNT(amount, USD)
fun USD(amount: Double): Amount<FiatCurrency> = AMOUNT(amount, USD)
val Int.USD: Amount<FiatCurrency> get() = USD(this)
val Long.USD: Amount<FiatCurrency> get() = USD(this)
val Double.USD: Amount<FiatCurrency> get() = USD(this)

// Bitcoin.
val BTC = DigitalCurrency.getInstance("BTC")

fun BTC(amount: Int): Amount<DigitalCurrency> = AMOUNT(amount, BTC)
fun BTC(amount: Long): Amount<DigitalCurrency> = AMOUNT(amount, BTC)
fun BTC(amount: Double): Amount<DigitalCurrency> = AMOUNT(amount, BTC)
val Int.BTC: Amount<DigitalCurrency> get() = BTC(this)
val Long.BTC: Amount<DigitalCurrency> get() = BTC(this)
val Double.BTC: Amount<DigitalCurrency> get() = BTC(this)