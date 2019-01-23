package net.corda.sdk.token.contracts.types

import net.corda.core.contracts.LinearPointer
import net.corda.core.contracts.LinearState
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party

/**
 * A [EvolvableToken]s _are_ state objects because the expectation is that they will evolve over time. Of course
 * in-lining a [LinearState] directly into the [Token] state doesn't make much sense, as you would have to
 * perform a state update just to change the token type. It makes more sense to include a pointer to the token type
 * instead. That's what [TokenPointer] is for. This way, the token can evolve independently to which party currently
 * owns some amount of the token. Because the [EvolvableToken] is not inlined into the [Token] state it does not
 * sub-class [EmbeddableToken].
 */
abstract class EvolvableToken : Token, LinearState {

    /**
     * The [Party]s which create and maintain this token definition. It probably _is_ the issuer of the token but
     * may not necessarily be the issuer. For example. A reference data maintainer may create a token for some stock,
     * keep all the details up-to-date, and distribute the updates... However, it can be used by many issuers to create
     * depository receipts for the stock in question. Also the actual stock issuer (if they had a Corda node on the
     * network) could use the same stock token to issue ledger native stock.
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
