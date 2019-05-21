package com.r3.corda.sdk.token.workflow.flows.confidential

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.sdk.token.contracts.states.AbstractToken
import com.r3.corda.sdk.token.contracts.types.TokenType
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.identity.Party


/**
 * This flow extracts the holders from a list of tokens to be issued on ledger, then requests each of the holders to
 * generate a new key pair for holding the new asset. The new key pair effectively anonymises them. The
 * newly generated public keys replace the old, well known, keys.
 *
 * The flow notifies prospective token holders that they must generate a new key pair to confidentially hold some
 * new tokens. As this is an in-line sub-flow, we must pass it a list of sessions, which _may_ contain sessions
 * for observers.
 * As such, we can't assume that all token holders we have sessions for will need to generate a new key
 * pair, so only the session token holders which are also hold passed tokens are sent an [ActionRequest.CREATE_NEW_KEY] and
 * everyone else is sent [ActionRequest.DO_NOTHING].
 *
 * This is an in-line flow and use of it should be paired with [ConfidentialTokensFlowHandler].
 *
 * @property tokens a list of [AbstractToken]s.
 * @property sessions a list of participants' sessions which may contain sessions for observers.
 */
class ConfidentialTokensFlow<T : TokenType>(
        val tokens: List<AbstractToken<T>>,
        val sessions: List<FlowSession>
) : FlowLogic<List<AbstractToken<T>>>() {
    @Suspendable
    override fun call(): List<AbstractToken<T>> {
        // Some holders might be anonymous already. E.g. if some token selection has been performed and a confidential
        // change address was requested.
        val tokensWithWellKnownHolders = tokens.filter { it.holder is Party }
        val tokensWithAnonymousHolders = tokens - tokensWithWellKnownHolders
        val wellKnownTokenHolders = tokensWithWellKnownHolders.map(AbstractToken<T>::holder)
        val anonymousParties = subFlow(AnonymisePartiesFlow(wellKnownTokenHolders, sessions))
        // Replace Party with AnonymousParty.
        return tokensWithWellKnownHolders.map { token ->
            val holder = token.holder
            val anonymousParty = anonymousParties[holder]
                    ?: throw IllegalStateException("Missing anonymous party for $holder.")
            token.withNewHolder(anonymousParty)
        } + tokensWithAnonymousHolders
    }
}
