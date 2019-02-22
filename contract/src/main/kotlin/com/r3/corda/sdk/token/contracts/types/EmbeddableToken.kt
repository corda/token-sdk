package com.r3.corda.sdk.token.contracts.types

import com.r3.corda.sdk.token.contracts.states.EvolvableToken
import net.corda.core.contracts.LinearPointer
import java.math.BigDecimal

/**
 * [EmbeddableToken]s are [Token]s which can be composed into an [OwnedToken] or an [OwnedTokenAmount]. They are almost
 * always wrapped with an [IssuedToken] class.
 */
sealed class EmbeddableToken : Token

/**
 * A [FixedToken] is where the definition of a token is inlined into the [OwnedToken] or [OwnedTokenAmount] class.
 * This is for tokens which you don't expect to evolve. Note that [FixedToken]s are not states! They don't need to be
 * because the definition of the token never changes or hardly ever changes. You are certainly likely to use this when
 * testing or creating tokens for currencies.
 *
 * If you do need to make changes to a [FixedToken] then they need to be made through upgrading your CorDapp. Then by
 * redeeming and re-issuing the [OwnedToken] or [OwnedTokenAmount] with the new token. However, if your token is likely
 * to need updating often, then use the [EvolvableToken] type.
 */
abstract class FixedToken : EmbeddableToken() {
    /**
     * All [FixedToken]s must have a symbol, which is typically a 3-4 character, upper case alphabetic string.
     * TODO: Add some validation for the symbol.
     */
    abstract val symbol: String
    override val tokenIdentifier: String get() = symbol
    override val tokenClass: String get() = javaClass.canonicalName
    override fun toString(): String = "$tokenClass($tokenIdentifier)"
}

/**
 * To harness the power of [EvolvableToken]s, they cannot be directly embedded in [OwnedToken] or [OwnedTokenAmount]s.
 * Instead, a [TokenPointer] is embedded. The pointer can be resolved inside the verify function to obtain the data
 * within the token definition. This way, the [Token] can evolve independently from who owns it, as the data is held in
 * a separate state object.
 */
data class TokenPointer<T : EvolvableToken>(
        val pointer: LinearPointer<T>,
        override val displayTokenSize: BigDecimal
) : EmbeddableToken() {
    override val tokenIdentifier: String get() = pointer.pointer.id.toString()
    override val tokenClass: String get() = pointer.type.canonicalName
    override fun toString(): String = "Pointer($tokenIdentifier, $tokenClass)"
}










