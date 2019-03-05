package com.r3.corda.sdk.token.contracts.types

import com.r3.corda.sdk.token.contracts.states.EvolvableTokenType
import com.r3.corda.sdk.token.contracts.states.FungibleToken
import com.r3.corda.sdk.token.contracts.states.NonFungibleToken

/**
 * An [FixedTokenType] is used when a [TokenType] can be inlined into the [NonFungibleToken] or [FungibleToken] class.
 * This is for tokens which you don't expect to evolve. Note that [FixedTokenType]s are not states! They don't need to
 * be because the [TokenType] never changes or hardly ever changes. You are certainly likely to use this when testing
 * or creating tokens for currencies, for example.
 *
 * If you do need to make changes to an [FixedTokenType] then they need to be made through upgrading your CorDapp.
 * Then by redeeming and re-issuing the [NonFungibleToken] or [FungibleToken] with the new [TokenType]. However, if your
 * token is likely to need updating often, then use the [EvolvableTokenType] type.
 */
abstract class FixedTokenType : TokenType {
    override fun toString(): String = "$tokenClass($tokenIdentifier)"
}