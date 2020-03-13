@file:JvmName("MoneyUtilities")
package com.r3.corda.lib.tokens.money

import com.r3.corda.lib.tokens.contracts.types.TokenType
import com.r3.corda.lib.tokens.contracts.utilities.amount
import net.corda.core.contracts.Amount

/** Helpers for creating amounts of fixed token definitions. */

// Sterling.
val GBP = FiatCurrency.getInstance("GBP")

fun GBP(amount: Int): Amount<TokenType> = amount(amount, GBP)
fun GBP(amount: Long): Amount<TokenType> = amount(amount, GBP)
fun GBP(amount: Double): Amount<TokenType> = amount(amount, GBP)
val Int.GBP: Amount<TokenType> get() = GBP(this)
val Long.GBP: Amount<TokenType> get() = GBP(this)
val Double.GBP: Amount<TokenType> get() = GBP(this)

// US Dollar.
val USD = FiatCurrency.getInstance("USD")

fun USD(amount: Int): Amount<TokenType> = amount(amount, USD)
fun USD(amount: Long): Amount<TokenType> = amount(amount, USD)
fun USD(amount: Double): Amount<TokenType> = amount(amount, USD)
val Int.USD: Amount<TokenType> get() = USD(this)
val Long.USD: Amount<TokenType> get() = USD(this)
val Double.USD: Amount<TokenType> get() = USD(this)

// Euro.
val EUR = FiatCurrency.getInstance("EUR")

fun EUR(amount: Int): Amount<TokenType> = amount(amount, EUR)
fun EUR(amount: Long): Amount<TokenType> = amount(amount, EUR)
fun EUR(amount: Double): Amount<TokenType> = amount(amount, EUR)
val Int.EUR: Amount<TokenType> get() = EUR(this)
val Long.EUR: Amount<TokenType> get() = EUR(this)
val Double.EUR: Amount<TokenType> get() = EUR(this)

// Swissie.
val CHF = FiatCurrency.getInstance("CHF")

fun CHF(amount: Int): Amount<TokenType> = amount(amount, CHF)
fun CHF(amount: Long): Amount<TokenType> = amount(amount, CHF)
fun CHF(amount: Double): Amount<TokenType> = amount(amount, CHF)
val Int.CHF: Amount<TokenType> get() = CHF(this)
val Long.CHF: Amount<TokenType> get() = CHF(this)
val Double.CHF: Amount<TokenType> get() = CHF(this)

// Japanese Yen.
val JPY = FiatCurrency.getInstance("JPY")

fun JPY(amount: Int): Amount<TokenType> = amount(amount, JPY)
fun JPY(amount: Long): Amount<TokenType> = amount(amount, JPY)
fun JPY(amount: Double): Amount<TokenType> = amount(amount, JPY)
val Int.JPY: Amount<TokenType> get() = JPY(this)
val Long.JPY: Amount<TokenType> get() = JPY(this)
val Double.JPY: Amount<TokenType> get() = JPY(this)

// Canadian Dollar.
val CAD = FiatCurrency.getInstance("CAD")

fun CAD(amount: Int): Amount<TokenType> = amount(amount, CAD)
fun CAD(amount: Long): Amount<TokenType> = amount(amount, CAD)
fun CAD(amount: Double): Amount<TokenType> = amount(amount, CAD)
val Int.CAD: Amount<TokenType> get() = CAD(this)
val Long.CAD: Amount<TokenType> get() = CAD(this)
val Double.CAD: Amount<TokenType> get() = CAD(this)

// Australian Dollar.
val AUD = FiatCurrency.getInstance("AUD")

fun AUD(amount: Int): Amount<TokenType> = amount(amount, AUD)
fun AUD(amount: Long): Amount<TokenType> = amount(amount, AUD)
fun AUD(amount: Double): Amount<TokenType> = amount(amount, AUD)
val Int.AUD: Amount<TokenType> get() = AUD(this)
val Long.AUD: Amount<TokenType> get() = AUD(this)
val Double.AUD: Amount<TokenType> get() = AUD(this)

// New Zealand Dollar.
val NZD = FiatCurrency.getInstance("NZD")

fun NZD(amount: Int): Amount<TokenType> = amount(amount, NZD)
fun NZD(amount: Long): Amount<TokenType> = amount(amount, NZD)
fun NZD(amount: Double): Amount<TokenType> = amount(amount, NZD)
val Int.NZD: Amount<TokenType> get() = NZD(this)
val Long.NZD: Amount<TokenType> get() = NZD(this)
val Double.NZD: Amount<TokenType> get() = NZD(this)

// Bitcoin.
val BTC = DigitalCurrency.getInstance("BTC")

fun BTC(amount: Int): Amount<TokenType> = amount(amount, BTC)
fun BTC(amount: Long): Amount<TokenType> = amount(amount, BTC)
fun BTC(amount: Double): Amount<TokenType> = amount(amount, BTC)
val Int.BTC: Amount<TokenType> get() = BTC(this)
val Long.BTC: Amount<TokenType> get() = BTC(this)
val Double.BTC: Amount<TokenType> get() = BTC(this)

val XRP = DigitalCurrency.getInstance("XRP")

fun XRP(amount: Int): Amount<TokenType> = amount(amount, XRP)
fun XRP(amount: Long): Amount<TokenType> = amount(amount, XRP)
fun XRP(amount: Double): Amount<TokenType> = amount(amount, XRP)
val Int.XRP: Amount<TokenType> get() = XRP(this)
val Long.XRP: Amount<TokenType> get() = XRP(this)
val Double.XRP: Amount<TokenType> get() = XRP(this)
