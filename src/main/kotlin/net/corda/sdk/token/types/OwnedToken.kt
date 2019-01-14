package net.corda.sdk.token.types

import net.corda.core.contracts.OwnableState
import net.corda.core.identity.AbstractParty

abstract class AbstractOwnedToken<T : EmbeddableToken> : OwnableState {

    /** The current [owner] is always the sole participant. */
    override val participants: List<AbstractParty> get() = listOf(owner)

    // TODO: Add more bits here.

}