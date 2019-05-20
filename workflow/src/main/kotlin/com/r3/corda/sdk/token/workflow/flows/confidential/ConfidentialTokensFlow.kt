package com.r3.corda.sdk.token.workflow.flows.confidential

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.sdk.token.contracts.states.AbstractToken
import com.r3.corda.sdk.token.contracts.types.TokenType
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party


/**
 * This flow extracts the holders from a list of tokensToIssue to be issued on ledger, then requests each of the holders
 * generate a new key pair for holding the newly issued asset. The new key pair effectively anonymises them. The
 * newly generated public keys replace the old, well known, keys.
 *
 * The flow notifies prospective token holders that they must generate a new key pair to confidentially hold some
 * newly issued tokensToIssue. As this is an in-line sub-flow, we must pass it a list of participantSessions, which _may_ contain participantSessions
 * for observers. As such, we can't assume that all tokenHolders we have participantSessions for will need to generate a new key
 * pair, so only the session tokenHolders which are also token holders are sent an [ActionRequest.CREATE_NEW_KEY] and
 * everyone else is sent [ActionRequest.DO_NOTHING].
 *
 * This is an in-line flow and use of it should be paired with [ConfidentialTokensFlowHandler].
 *
 * @property tokenHolders a list of [AbstractParty]s which will hold tokensToIssue.
 * @property sessions a list of participantSessions which may contain participantSessions for observers.
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


