package com.r3.corda.sdk.token.contracts.states

import com.r3.corda.sdk.token.contracts.types.EmbeddableToken
import com.r3.corda.sdk.token.contracts.types.FixedToken
import com.r3.corda.sdk.token.contracts.types.TokenPointer
import net.corda.core.contracts.OwnableState
import net.corda.core.crypto.toStringShort
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party

/** Contains common [OwnedToken] functionality. */
abstract class AbstractOwnedToken : OwnableState {
    companion object {
        /** Used when persisting sub-types of [AbstractOwnedToken] to the database and querying from the database. */
        fun tokenIdentifier(token: EmbeddableToken): String = when (token) {
            is TokenPointer<*> -> token.pointer.pointer.id.toString()
            is FixedToken -> token.symbol
        }

        /** Used when persisting sub-types of [AbstractOwnedToken] to the database and querying from the database. */
        fun tokenClass(token: EmbeddableToken): String = when (token) {
            is TokenPointer<*> -> token.pointer.type.name
            is FixedToken -> token.javaClass.name
        }
    }

    /** The current [owner] is always the sole participant. */
    override val participants: List<AbstractParty> get() = listOf(owner)

    /** Converts [owner] into a more friendly string, e.g. shortens the public key for [AnonymousParty]s. */
    protected val ownerString
        get() = (owner as? Party)?.name?.organisation
                ?: owner.owningKey.toStringShort().substring(0, 16)
}