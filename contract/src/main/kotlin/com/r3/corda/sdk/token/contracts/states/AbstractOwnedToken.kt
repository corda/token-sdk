package com.r3.corda.sdk.token.contracts.states

import net.corda.core.contracts.OwnableState
import net.corda.core.crypto.toStringShort
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party

/** Contains common [OwnedToken] functionality. */
abstract class AbstractOwnedToken : OwnableState {

    /** The current [owner] is always the sole participant. */
    override val participants: List<AbstractParty> get() = listOf(owner)

    /** Converts [owner] into a more friendly string, e.g. shortens the public key for [AnonymousParty]s. */
    // TODO: Is AbstractOwnedToken#ownerString needed?
    protected val ownerString
        get() = (owner as? Party)?.name?.organisation
                ?: owner.owningKey.toStringShort().substring(0, 16)
}