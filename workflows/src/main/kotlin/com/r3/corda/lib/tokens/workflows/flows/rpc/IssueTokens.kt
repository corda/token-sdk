package com.r3.corda.lib.tokens.workflows.flows.rpc

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.lib.tokens.contracts.states.AbstractToken
import com.r3.corda.lib.tokens.workflows.flows.issue.ConfidentialIssueTokensFlow
import com.r3.corda.lib.tokens.workflows.flows.issue.ConfidentialIssueTokensFlowHandler
import com.r3.corda.lib.tokens.workflows.flows.issue.IssueTokensFlow
import com.r3.corda.lib.tokens.workflows.flows.issue.IssueTokensFlowHandler
import com.r3.corda.lib.tokens.workflows.utilities.sessionsForParticipants
import com.r3.corda.lib.tokens.workflows.utilities.sessionsForParties
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.transactions.SignedTransaction

/**
 * A flow for issuing fungible or non-fungible tokens which initiates its own participantSessions. This is the case when
 * called from the node rpc or in a unit test. However, in the case where you already have a session with another [Party]
 * and you wish to issue tokens as part of a wider workflow, then use [IssueTokensFlow].
 *
 * @property tokensToIssue a list of [AbstractToken]s to issue
 * @property observers a set of observing [Party]s
 */
@StartableByService
@StartableByRPC
@InitiatingFlow
class IssueTokens
@JvmOverloads
constructor(
        val tokensToIssue: List<AbstractToken>,
        val observers: List<Party> = emptyList()
) : FlowLogic<SignedTransaction>() {

    @Suspendable
    override fun call(): SignedTransaction {
        val observerSessions = sessionsForParties(observers)
        val participantSessions = sessionsForParticipants(tokensToIssue)
        return subFlow(IssueTokensFlow(tokensToIssue, participantSessions, observerSessions))
    }
}

/**
 * Responder flow for [IssueTokens].
 */
@InitiatedBy(IssueTokens::class)
class IssueTokensHandler(val otherSession: FlowSession) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() = subFlow(IssueTokensFlowHandler(otherSession))
}

/**
 * A flow for issuing fungible or non-fungible tokens which initiates its own participantSessions. This is the case when called
 * from the node rpc or in a unit test. However, in the case where you already have a session with another [Party] and
 * you wish to issue tokens as part of a wider workflow, then use [IssueTokensFlow].
 *
 * @property tokensToIssue a list of [AbstractToken]s to issue
 * @property observers aset of observing [Party]s
 */
@StartableByService
@StartableByRPC
@InitiatingFlow
class ConfidentialIssueTokens
@JvmOverloads
constructor(
        val tokensToIssue: List<AbstractToken>,
        val observers: List<Party> = emptyList()
) : FlowLogic<SignedTransaction>() {
    @Suspendable
    override fun call(): SignedTransaction {
        val observerSessions = sessionsForParties(observers)
        val participantSessions = sessionsForParticipants(tokensToIssue)
        return subFlow(ConfidentialIssueTokensFlow(tokensToIssue, participantSessions, observerSessions))
    }
}

/**
 * Responder flow for [ConfidentialIssueTokens].
 */
@InitiatedBy(ConfidentialIssueTokens::class)
class ConfidentialIssueTokensHandler(val otherSession: FlowSession) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() = subFlow(ConfidentialIssueTokensFlowHandler(otherSession))
}