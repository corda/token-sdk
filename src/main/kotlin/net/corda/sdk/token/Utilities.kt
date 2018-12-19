package net.corda.sdk.token

import net.corda.core.contracts.Amount
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.internal.uncheckedCast
import net.corda.sdk.token.states.OwnedTokenAmount
import net.corda.sdk.token.types.Issued
import net.corda.sdk.token.types.money.DigitalCurrency
import net.corda.sdk.token.types.money.FiatCurrency
import net.corda.sdk.token.types.token.Token
import java.math.BigDecimal

/** Helpers. */

fun <T : Any> AMOUNT(amount: Int, token: T): Amount<T> = AMOUNT(amount.toLong(), token)
fun <T : Any> AMOUNT(amount: Long, token: T): Amount<T> = Amount.fromDecimal(BigDecimal.valueOf(amount), token)
fun <T : Any> AMOUNT(amount: Double, token: T): Amount<T> = Amount.fromDecimal(BigDecimal.valueOf(amount), token)

infix fun Token.`issued by`(issuer: Party) = issuedBy(issuer)
infix fun <T : Token> Amount<T>.`issued by`(issuer: Party): Amount<Issued<T>> = issuedBy(issuer)
infix fun Token.issuedBy(issuer: Party) = Issued(issuer, this)
infix fun <T : Token> Amount<T>.issuedBy(issuer: Party): Amount<Issued<T>> = Amount(quantity, displayTokenSize, uncheckedCast(token.issuedBy(issuer)))

infix fun <T : Token> Amount<Issued<T>>.`owned by`(owner: AbstractParty) = ownedBy(owner)
infix fun <T : Token> Amount<Issued<T>>.ownedBy(owner: AbstractParty) = OwnedTokenAmount(this, owner)

/** Fiat currencies. */

val GBP = FiatCurrency.getInstance("GBP")

fun GBP(amount: Int): Amount<FiatCurrency> = AMOUNT(amount, GBP)
fun GBP(amount: Long): Amount<FiatCurrency> = AMOUNT(amount, GBP)
fun GBP(amount: Double): Amount<FiatCurrency> = AMOUNT(amount, GBP)
val Int.GBP: Amount<FiatCurrency> get() = GBP(this)
val Long.GBP: Amount<FiatCurrency> get() = GBP(this)
val Double.GBP: Amount<FiatCurrency> get() = GBP(this)

val USD = FiatCurrency.getInstance("USD")
fun USD(amount: Int): Amount<FiatCurrency> = AMOUNT(amount, USD)
fun USD(amount: Long): Amount<FiatCurrency> = AMOUNT(amount, USD)
fun USD(amount: Double): Amount<FiatCurrency> = AMOUNT(amount, USD)
val Int.USD: Amount<FiatCurrency> get() = USD(this)
val Long.USD: Amount<FiatCurrency> get() = USD(this)
val Double.USD: Amount<FiatCurrency> get() = USD(this)

/** Digital currencies. */

val BTC = DigitalCurrency.getInstance("BTC")

fun BTC(amount: Int): Amount<DigitalCurrency> = AMOUNT(amount, BTC)
fun BTC(amount: Long): Amount<DigitalCurrency> = AMOUNT(amount, BTC)
fun BTC(amount: Double): Amount<DigitalCurrency> = AMOUNT(amount, BTC)
val Int.BTC: Amount<DigitalCurrency> get() = BTC(this)
val Long.BTC: Amount<DigitalCurrency> get() = BTC(this)
val Double.BTC: Amount<DigitalCurrency> get() = BTC(this)