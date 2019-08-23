package com.r3.corda.lib.tokens.workflows.flows.confidential

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.lib.ci.RequestKeyFlow
import com.r3.corda.lib.tokens.contracts.states.AbstractToken
import com.r3.corda.lib.tokens.workflows.internal.flows.confidential.AnonymisePartiesFlow
import com.r3.corda.lib.tokens.workflows.utilities.toWellKnownParties
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.identity.AnonymousParty
import net.corda.core.identity.Party


/**
 * This flow extracts the holders from a list of tokens to be issued on ledger, then requests only the well known
 * holders to generate a new key pair for holding the new asset. The new key pair effectively anonymises them. The
 * newly generated public keys replace the old, well known, keys. The flow doesn't request new keys for
 * [AnonymousParty]s.
 *
 * This is an in-line flow and use of it should be paired with [ConfidentialTokensFlowHandler].
 *
 * Note that this flow should only be called if you are dealing with [Party]s as individual nodes, i.e. not accounts.
 * When issuing tokens to accounts, the public keys + tokens need to be generated up-front as passed into the
 * [IssueTokensFlow].
 *
 * @property tokens a list of [AbstractToken]s.
 * @property sessions a list of participants' sessions which may contain sessions for observers.
 */
class ConfidentialTokensFlow(
        val tokens: List<AbstractToken>,
        val sessions: List<FlowSession>
) : FlowLogic<List<AbstractToken>>() {
    @Suspendable
    override fun call(): List<AbstractToken> {
        // Some holders might be anonymous already. E.g. if some token selection has been performed and a confidential
        // change address was requested.
        val tokensWithWellKnownHolders = tokens.filter { it.holder is Party }
        val tokensWithAnonymousHolders = tokens - tokensWithWellKnownHolders
        val wellKnownTokenHolders = tokensWithWellKnownHolders
                .map(AbstractToken::holder)
                .toWellKnownParties(serviceHub)
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
