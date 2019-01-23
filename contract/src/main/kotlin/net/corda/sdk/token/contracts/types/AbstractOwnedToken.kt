package net.corda.sdk.token.contracts.types

import net.corda.core.contracts.OwnableState
import net.corda.core.identity.AbstractParty

abstract class AbstractOwnedToken : OwnableState {

    companion object {
        fun tokenIdentifier(token: EmbeddableToken): String = when (token) {
            is TokenPointer<*> -> token.pointer.pointer.id.toString()
            is FixedToken -> token.symbol
        }

        fun tokenClass(token: EmbeddableToken): String = when (token) {
            is TokenPointer<*> -> token.pointer.type.name
            is FixedToken -> token.javaClass.name
        }
    }

    /** The current [owner] is always the sole participant. */
    override val participants: List<AbstractParty> get() = listOf(owner)

}