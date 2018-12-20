package net.corda.sdk.token.types.token

import net.corda.core.contracts.LinearPointer
import net.corda.core.contracts.LinearState
import net.corda.core.contracts.TokenizableAssetInfo
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.serialization.CordaSerializable
import net.corda.sdk.token.types.token.Token.*
import java.math.BigDecimal

/**
 * [Token]s can be defined as:
 *
 * 1. An [FixedDefinition], which means the token type is inlined into the [Token] class. This is for token types
 * which you don't expect to evolve. It is not a state! It doesn't need to be because it never changes or hardly ever
 * changes. You are likely to use this when testing. CurrencyType would probably use this. If you do need to make
 * changes then they can be made by upgrading the contract/state or just redeeming and re-issuing the state with a
 * different [Token].
 * 2. A [EvolvableDefinition], which _is_ a state object. The expectation is that it will evolve over time. Of
 * course in-lining a [LinearState] directly into the [Token] state doesn't make much sense, as you would have to
 * perform a state update just to change the token type. It makes more sense to include a pointer to the token type
 * instead. That's what [Pointer] is. This way, the token can evolve independently to which party currently owns
 * some amount of the token. Because the [EvolvableDefinition] is not inlined into the [Token] state it does not
 * sub-class [Token].
 */
@CordaSerializable
sealed class Token : TokenizableAssetInfo {

    abstract class FixedDefinition : Token()

    data class Pointer<T : EvolvableDefinition>(
            val pointer: LinearPointer<T>,
            override val displayTokenSize: BigDecimal
    ) : Token() {
        override fun toString(): String = "Pointer(${pointer.pointer.id}, ${pointer.type.canonicalName})"
    }

    abstract class EvolvableDefinition(
            open val maintainer: Party,
            final override val linearId: UniqueIdentifier = UniqueIdentifier()
    ) : TokenizableAssetInfo, LinearState {
        final override val participants: List<AbstractParty> get() = listOf(maintainer)
        abstract fun toPointer(): Pointer<*>
    }

}






