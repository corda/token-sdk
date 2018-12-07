package net.corda.sdk.token

import net.corda.core.contracts.Amount
import java.math.BigDecimal
import java.util.*

fun <T : Any> AMOUNT(amount: Int, token: T): Amount<T> = AMOUNT(amount.toLong(), token)
fun <T : Any> AMOUNT(amount: Long, token: T): Amount<T> = Amount.fromDecimal(BigDecimal.valueOf(amount), token)
fun <T : Any> AMOUNT(amount: Double, token: T): Amount<T> = Amount.fromDecimal(BigDecimal.valueOf(amount), token)

val GBP = Currency.getInstance("GBP")
fun GBP(amount: Int): Amount<Currency> = AMOUNT(amount, GBP)
fun GBP(amount: Long): Amount<Currency> = AMOUNT(amount, GBP)
fun GBP(amount: Double): Amount<Currency> = AMOUNT(amount, GBP)
val Int.GBP: Amount<Currency> get() = GBP(this)
val Long.GBP: Amount<Currency> get() = GBP(this)
val Double.GBP: Amount<Currency> get() = GBP(this)