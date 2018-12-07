package net.corda.sdk.token.types

import net.corda.core.contracts.LinearPointer
import net.corda.core.contracts.LinearState
import net.corda.core.contracts.TokenizableAssetInfo
import net.corda.sdk.token.states.Token
import net.corda.sdk.token.types.TokenType.*
import java.math.BigDecimal

/**
 * [TokenType]s can be defined as:
 *
 * 1. A [StaticDefinition], which means the token type is inlined into the [Token] class. This is for token types
 *    which you don't expect to evolve. It is not a state! It doesn't need to be because it never changes. You are
 *    likely to use this when testing.
 * 2. A [EvolvableDefinition], which _is_ a state object. The expectation is that it will evolve over time. Of
 *    course in-lining a [LinearState] directly into the [Token] state doesn't make much sense, as you would have to
 *    perform a state update just to change the token type. It makes more sense to include a pointer to the token type
 *    instead.
 * 3. A [Pointer] which points to an [EvolvableDefinition] via its linear ID.
 */
sealed class TokenType : TokenizableAssetInfo {

    abstract class StaticDefinition : TokenType()

    data class Pointer<T : EvolvableDefinition>(val pointer: LinearPointer<T>, override val displayTokenSize: BigDecimal) : TokenType()

    abstract class EvolvableDefinition : LinearState, TokenType() {
        abstract fun toPointer(): Pointer<*>
    }

}

// TODO

// Think further about the differences in token types across issuer. The [Issuer] type is nice because it allows you to strip
// the issuer and then group same the same currencies as fungible, if you'd like to view them that way. A step up from this
// would be the ability to specify fungibility predicates. E.g. "I'll treat all these issuers on my whitelist the same, the
// other ones I won't accept"

// Reason being is that dealing with the the same token type issued by multiple issuers is probably going to be a common
// theme going forward. The fact that there exists multiple USD stable coins on Ethereum today proves this. We need a
// mechanism for users to handle this. How does it map to the current token types?

// Not all tokens have issuers. Bitcoin doesn't... But maybe it does...? A null bytes key as we don't care about the issuer.
// If something doesn't have an issuer then we are saying that it is not a contract but pretty much all things on Corda
// have an issuer, so this probably doesn't hold.

// Other areas which need more thought:
//
// 1. The toke type interfaces. Ledger native vs depository receipt etc. Issued, redeemable, etc.
// 2. Need to develop a nice API for using Token pointers. Currently the API is a bit clunky.
// 3. Where does reference data sit? On ledger? In library? In attachments? Where exactly?
// 4. What token types will be used as pointers, what tokens will be inlined? Equities probably pointers, money will
//    be inlined.
// 5. Depth of modelling, do we want to model commercial bank money as a liability of a commerical bank to deliver a
//    liability of a central bank?? Would be nice if we could but is this overly complex?
// 6. Are non fungible things just token types?
// 7. Do we need to inline token symbol and description for fungible token pointers? How does this affect coin selection?
// 8. How does all this interact with obligations?