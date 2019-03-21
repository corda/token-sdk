package com.r3.corda.sdk.token.contracts.types

import com.r3.corda.sdk.token.contracts.states.EvolvableTokenType
import com.r3.corda.sdk.token.contracts.states.FungibleToken
import com.r3.corda.sdk.token.contracts.states.NonFungibleToken
import net.corda.core.contracts.TokenizableAssetInfo
import net.corda.core.serialization.CordaSerializable

/**
 * Represents a token which can be embedded within an [NonFungibleToken] or [FungibleToken] state. This interface is
 * implemented by [FixedTokenType] and [TokenPointer]. Note that [EvolvableTokenType] does NOT implement
 * this interface, as it represented by proxy through the [TokenPointer].
 *
 * Just a quick level-set on terminology here. [TokenType] refers to a "type of thing" as opposed to the vehicle
 * which is used to represent the agreement between an issuer and the holder of tokens. For that we use the
 * [NonFungibleToken] state for representing the agreement between an issuer and a holder of a token which cannot be
 * split and merged, and the [FungibleToken] state for representing the agreement between an issuer and a holder of some
 * amount of a token which can be split and merged
 */
@CordaSerializable
interface TokenType : TokenizableAssetInfo {
    /**
     * All [FixedTokenType]s must have a [tokenIdentifier], which is typically a 3-4 character, upper case
     * alphabetic string. The [tokenIdentifier] is used in conjunction with the [tokenClass] to create an instance of a
     * [FixedTokenType], for example: (FiatCurrency, GBP), (DigitalCurrency, BTC), or (Stock, GOOG). For [TokenPointer]s
     * this property will contain the linearId of the [EvolvableTokenType] which is pointed to. The linearId can be used
     * to obtain the underlying [EvolvableTokenType] from the vault.
     */
    val tokenIdentifier: String

    /**
     * This property is used for when querying the vault for tokens. It allows us to construct an instance of a
     * [FixedTokenType] with a specified [tokenIdentifier], or for [EvolvableTokenType]s, as the
     * [tokenIdentifier] is a linearId, which is opaque, the [tokenClass] provides a bit more context on what is being
     * pointed to.
     */
    val tokenClass: Class<*>
}
