package net.corda.sdk.token.contracts.types

import net.corda.core.contracts.TokenizableAssetInfo

/**
 * Overarching interface for all things token. All tokens implement this interface. Just a quick level-set on terminology
 * here. [Token] refers to a "type of thing" as opposed to the vehicle which is used to assign units of a token to a
 * particular owner. For that we use the [OwnedToken] state for assigning non-fungible tokens to an owner and the
 * [OwnedTokenAmount] state for assigning amounts of some fungible token to an owner.
 */
interface Token : TokenizableAssetInfo