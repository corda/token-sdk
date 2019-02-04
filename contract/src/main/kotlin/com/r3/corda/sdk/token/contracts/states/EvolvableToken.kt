package com.r3.corda.sdk.token.contracts.states

import com.r3.corda.sdk.token.contracts.types.Token
import com.r3.corda.sdk.token.contracts.types.TokenPointer
import net.corda.core.contracts.LinearPointer
import net.corda.core.contracts.LinearState
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party

/**
 * [EvolvableToken]s _are_ state objects because the expectation is that they will evolve over time. Of course in-lining
 * a [LinearState] directly into the [OwnedToken] or [OwnedTokenAmount] state doesn't make much sense, as you would have
 * to perform a state update to change the token type. It makes more sense to include a pointer to the token type
 * instead. That's what [TokenPointer] is for. This way, the token can evolve independently to which party currently
 * owns (some amount) of the token. Because the [EvolvableToken] is not inlined into the [OwnedToken] or
 * [OwnedTokenAmount] state it does not sub-class [EmbeddableToken].
 */
abstract class EvolvableToken : Token, LinearState {
    /**
     * The [Party]s which create and maintain this token definition. It probably _is_ the issuer of the token but
     * may not necessarily be. For example, a reference data maintainer may create an [EvolvableToken] for some stock,
     * keep all the details up-to-date, and distribute the updates... This [EvolvableToken], can then be used by many
     * issuers to create [OwnedTokenAmount]s (depository receipts) for the stock in question. Also the actual stock
     * issuer (if they had a Corda node on the network) could use the same stock token to issue ledger native stock.
     */
    abstract val maintainers: List<Party>

    /** Defaults to the maintainer but can be overridden if necessary. */
    override val participants: List<AbstractParty> get() = maintainers

    /** For obtaining a pointer to this [EvolveableToken]. */
    inline fun <reified T : EvolvableToken> toPointer(): TokenPointer<T> {
        val linearPointer = LinearPointer(linearId, T::class.java)
        return TokenPointer(linearPointer, displayTokenSize)
    }
}
