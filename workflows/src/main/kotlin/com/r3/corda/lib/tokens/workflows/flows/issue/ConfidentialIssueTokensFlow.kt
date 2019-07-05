package com.r3.corda.lib.tokens.workflows.flows.issue

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.lib.tokens.contracts.states.AbstractToken
import com.r3.corda.lib.tokens.contracts.states.FungibleToken
import com.r3.corda.lib.tokens.contracts.states.NonFungibleToken
import com.r3.corda.lib.tokens.workflows.flows.confidential.ConfidentialTokensFlow
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.transactions.SignedTransaction

/**
 * A flow for issuing tokens to confidential keys. To be used in conjunction with the
 * [ConfidentialIssueTokensFlowHandler].
 *
 * @property tokens a list of tokens to issue.
 * @property participantSessions a list of sessions for the parties being issued tokens.
 * @property observerSessions a list of sessions for any observers.
 */
class ConfidentialIssueTokensFlow
@JvmOverloads
constructor(
        val tokens: List<AbstractToken>,
        val participantSessions: List<FlowSession>,
        val observerSessions: List<FlowSession> = emptyList()
) : FlowLogic<SignedTransaction>() {

    /** Issue a single [FungibleToken]. */
    @JvmOverloads
    constructor(
            token: FungibleToken,
            participantSessions: List<FlowSession>,
            observerSessions: List<FlowSession> = emptyList()
    ) : this(listOf(token), participantSessions, observerSessions)

    /** Issue a single [FungibleToken] to self with no observers. */
    constructor(token: FungibleToken) : this(listOf(token), emptyList(), emptyList())

    /** Issue a single [NonFungibleToken]. */
    @JvmOverloads
    constructor(
            token: NonFungibleToken,
            participantSessions: List<FlowSession>,
            observerSessions: List<FlowSession> = emptyList()
    ) : this(listOf(token), participantSessions, observerSessions)

    /** Issue a single [NonFungibleToken] to self with no observers. */
    constructor(token: NonFungibleToken) : this(listOf(token), emptyList(), emptyList())

    @Suspendable
    override fun call(): SignedTransaction {
        // Request new keys pairs from all proposed token holders.
        val confidentialTokens = subFlow(ConfidentialTokensFlow(tokens, participantSessions))
        // Issue tokens using the existing participantSessions.
        return subFlow(IssueTokensFlow(confidentialTokens, participantSessions, observerSessions))
    }
}
