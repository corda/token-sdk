@file:JvmName("AmountUtilities")
package com.r3.corda.lib.tokens.contracts.utilities

import com.r3.corda.lib.tokens.contracts.types.IssuedTokenType
import com.r3.corda.lib.tokens.contracts.types.TokenType
import net.corda.core.contracts.Amount
import net.corda.core.contracts.Issued
import net.corda.core.identity.Party
import net.corda.core.internal.uncheckedCast
import java.math.BigDecimal

// -------------------------------------------------------------------------------
// Helpers for creating an amount of a token using some quantity and a token type.
// -------------------------------------------------------------------------------

/** For creating [Int] quantities of [TokenType]s. */
fun amount(amount: Int, token: TokenType): Amount<TokenType> = amount(amount.toLong(), token)

/** For creating [Long] quantities of [TokenType]s. */
fun amount(amount: Long, token: TokenType): Amount<TokenType> = Amount.fromDecimal(BigDecimal.valueOf(amount), token)

/** For creating [Double] quantities of [TokenType]s.  */
fun amount(amount: Double, token: TokenType): Amount<TokenType> = Amount.fromDecimal(BigDecimal.valueOf(amount), token)

/** For creating [BigDecimal] quantities of [TokenType]s. */
fun amount(amount: BigDecimal, token: TokenType): Amount<TokenType> = Amount.fromDecimal(amount, token)

// ---------------------------------------------------------------------------------------
// Helpers for creating an amount of a token using some quantity and an issued token type.
// ---------------------------------------------------------------------------------------

/** For parsing [Int] quantities of [IssuedTokenType]s. */
fun amount(amount: Int, token: IssuedTokenType): Amount<IssuedTokenType> {
    return amount(amount.toLong(), token)
}

/** For parsing [Long] quantities of [IssuedTokenType]s. */
fun amount(amount: Long, token: IssuedTokenType): Amount<IssuedTokenType> {
    return Amount.fromDecimal(BigDecimal.valueOf(amount), token)
}

/** For parsing [Double] quantities of [IssuedTokenType]s. */
fun amount(amount: Double, token: IssuedTokenType): Amount<IssuedTokenType> {
    return Amount.fromDecimal(BigDecimal.valueOf(amount), token)
}

/** For parsing [BigDecimal] quantities of [IssuedTokenType]s. */
fun amount(amount: BigDecimal, token: IssuedTokenType): Amount<IssuedTokenType> {
    return Amount.fromDecimal(amount, token)
}

// ---------------------------------------------------------------------------------------------
// For creating amounts of token types using a DSL-like infix notation. E.g. "1000 of tokenType"
// ---------------------------------------------------------------------------------------------

/** For creating [Int] quantities of [TokenType]s. */
infix fun Int.of(token: TokenType): Amount<TokenType> = amount(this, token)

/** For creating [Long] quantities of [TokenType]s. */
infix fun Long.of(token: TokenType): Amount<TokenType> = amount(this, token)

/** For creating [Double] quantities of [TokenType]s. */
infix fun Double.of(token: TokenType): Amount<TokenType> = amount(this, token)

/** For creating [BigDecimal] quantities of [TokenType]s. */
infix fun BigDecimal.of(token: TokenType): Amount<TokenType> = amount(this, token)

// ----------------------------------------------------------------------------------------------
// For creating amounts of token types using a DSL-like infix notation. "1000 of issuedTokenType"
// ----------------------------------------------------------------------------------------------

/** For creating [Int] quantities of [IssuedTokenType]s. */
infix fun Int.of(token: IssuedTokenType): Amount<IssuedTokenType> = amount(this, token)

/** For creating [Long] quantities of [IssuedTokenType]s. */
infix fun Long.of(token: IssuedTokenType): Amount<IssuedTokenType> = amount(this, token)

/** For creating [Double] quantities of [IssuedTokenType]s. */
infix fun Double.of(token: IssuedTokenType): Amount<IssuedTokenType> = amount(this, token)

/** For creating [BigDecimal] quantities of [IssuedTokenType]s. */
infix fun BigDecimal.of(token: IssuedTokenType): Amount<IssuedTokenType> {
    return amount(this, token)
}

// -------------------------------------------------------------------------------------------------------
// For wrapping amounts of a token with an issuer: Amount<TokenType> -> Amount<IssuedTokenType<TokenType>>.
// -------------------------------------------------------------------------------------------------------

/** Wraps a [TokenType] with an [IssuedTokenType]. E.g. Amount<TokenType> -> Amount<IssuedTokenType<TokenType>>. */
infix fun Amount<TokenType>.issuedBy(issuer: Party): Amount<IssuedTokenType> = _issuedBy(issuer)

internal infix fun Amount<TokenType>._issuedBy(issuer: Party): Amount<IssuedTokenType> {
    return Amount(quantity, displayTokenSize, uncheckedCast(token.issuedBy(issuer)))
}

/** Wraps a [TokenType] with an [IssuedTokenType]. E.g. TokenType -> IssuedTokenType<TokenType>. */
infix fun TokenType.issuedBy(issuer: Party): IssuedTokenType = _issuedBy(issuer)

internal infix fun TokenType._issuedBy(issuer: Party): IssuedTokenType = IssuedTokenType(issuer, this)

// ------------------------------------------------------------------
// Helpers for summing amounts of token types and issued token types.
// ------------------------------------------------------------------

/** Sums [Amount]s of [IssuedTokenType]s or returns null if list is empty or there is an [IssuedTokenType] mis-match. */
fun <T : TokenType> Iterable<Amount<T>>.sumIssuedTokensOrNull(): Amount<T>? {
    return if (!iterator().hasNext()) null else sumIssuedTokensOrThrow()
}

/**
 * Sums [Amount]s of [IssuedTokenType]s or throws [IllegalArgumentException] if there is an [IssuedTokenType] mis-match.
 */
fun <T : TokenType> Iterable<Amount<T>>.sumIssuedTokensOrThrow(): Amount<T> {
    return reduce { left, right -> left + right }
}

/** Sums [Amount]s of [IssuedTokenType]s or returns zero if there is an [IssuedTokenType] mis-match or an empty list. */
fun <T : TokenType> Iterable<Amount<T>>.sumIssuedTokensOrZero(token: T): Amount<T> {
    return if (iterator().hasNext()) sumIssuedTokensOrThrow() else Amount.zero(token)
}

/** Sums [Amount]s of [TokenType]s or returns null if list is empty or there is an [TokenType] mis-match. */
fun <T : TokenType> Iterable<Amount<T>>.sumTokensOrNull(): Amount<T>? {
    return if (!iterator().hasNext()) null else sumTokensOrThrow()
}

/** Sums [Amount]s of [TokenType]s or throws [IllegalArgumentException] if there is an [TokenType] mis-match. */
fun <T : TokenType> Iterable<Amount<T>>.sumTokensOrThrow(): Amount<T> {
    return reduce { left, right -> left + right }
}

/** Sums [Amount]s of [TokenType]s or returns zero if there is an [TokenType] mis-match or an empty list. */
fun <T : TokenType> Iterable<Amount<T>>.sumTokensOrZero(token: T): Amount<T> {
    return if (iterator().hasNext()) sumTokensOrThrow() else Amount.zero(token)
}

/**
 * Strips the wrapping [IssuedTokenType] from an [Amount] of [TokenType] and returns only the [Amount] of [TokenType].
 * This is useful when you are mixing code that cares about specific issuers with code that will accept any, or which is
 * imposing issuer constraints via some other mechanism and the additional type safety is not wanted.
 */

fun Amount<out TokenType>.withoutIssuer(): Amount<TokenType> {
    return if (token is IssuedTokenType){
        Amount(quantity, displayTokenSize, (token as IssuedTokenType).tokenType)
    }else{
        Amount(quantity, displayTokenSize, token)
    }
}