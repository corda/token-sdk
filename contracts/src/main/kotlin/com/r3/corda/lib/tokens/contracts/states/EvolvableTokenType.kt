package com.r3.corda.lib.tokens.contracts.states

import com.r3.corda.lib.tokens.contracts.types.TokenPointer
import com.r3.corda.lib.tokens.contracts.types.TokenType
import net.corda.core.contracts.LinearPointer
import net.corda.core.contracts.LinearState
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party

/**
 * [EvolvableTokenType]s are for storing token reference data that we expect to change over time.
 *
 * [EvolvableTokenType]s _are_ state objects because the expectation is that they will evolve over time. Of course
 * in-lining a [LinearState] directly into the [NonFungibleToken] or [FungibleToken] state doesn't make much sense, as
 * you would have to perform a state update to change the token type. It makes more sense to include a pointer to the
 * token type instead. That's what [TokenPointer] is for. This way, the token can evolve independently to which party
 * currently owns (some amount) of the token. Because the [EvolvableTokenType] is not inlined into the
 * [NonFungibleToken] or [FungibleToken] state it does not sub-class [TokenType].
 */
abstract class EvolvableTokenType : LinearState {
    /**
     * The [Party]s which create and maintain this token [EvolvableTokenType]. It probably _is_ the issuer of the token
     * but may not necessarily be. For example, a reference data maintainer may create an [EvolvableTokenType] for
     * some stock, keep all the details up-to-date, and distribute the updates. This [EvolvableTokenType], can
     * then be used by many issuers to create [FungibleToken]s (depository receipts) for the stock in question. Also
     * the actual stock issuer (if they had a Corda node on the network) could use the same stock token to issue ledger
     * native stock.
     */
    abstract val maintainers: List<Party>

    /** Defaults to the maintainer but can be overridden if necessary. */
    override val participants: List<AbstractParty> get() = maintainers

    /**
     * The number of fractional digits allowable for this token type. Specifying "0" will only allow integer amounts of
     * the token type. Specifying "2", allows two decimal places, like most fiat currencies, and so on...
     */
    abstract val fractionDigits: Int

    /** For obtaining a pointer to this [EvolveableTokenType]. */
    inline fun <reified T : EvolvableTokenType> toPointer(): TokenPointer<T> {
        val linearPointer = LinearPointer(linearId, T::class.java)
        return TokenPointer(linearPointer, fractionDigits)
    }
}
