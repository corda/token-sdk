package com.r3.corda.sdk.token.workflow.flows.shell

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.sdk.token.contracts.states.AbstractToken
import com.r3.corda.sdk.token.contracts.states.FungibleToken
import com.r3.corda.sdk.token.contracts.states.NonFungibleToken
import com.r3.corda.sdk.token.contracts.types.IssuedTokenType
import com.r3.corda.sdk.token.contracts.types.TokenType
import com.r3.corda.sdk.token.contracts.utilities.heldBy
import com.r3.corda.sdk.token.contracts.utilities.issuedBy
import com.r3.corda.sdk.token.contracts.utilities.of
import com.r3.corda.sdk.token.workflow.flows.issue.ConfidentialIssueTokensFlow
import com.r3.corda.sdk.token.workflow.flows.issue.ConfidentialIssueTokensFlowHandler
import com.r3.corda.sdk.token.workflow.flows.issue.IssueTokensFlow
import com.r3.corda.sdk.token.workflow.flows.issue.IssueTokensFlowHandler
import com.r3.corda.sdk.token.workflow.utilities.sessionsForParticipants
import com.r3.corda.sdk.token.workflow.utilities.sessionsForParties
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.flows.InitiatedBy
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.StartableByRPC
import net.corda.core.flows.StartableByService
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.transactions.SignedTransaction

/**
 * A flow for issuing fungible or non-fungible tokens which initiates its own participantSessions. This is the case when called
 * from the node shell or in a unit test. However, in the case where you already have a session with another [Party] and
 * you wish to issue tokens as part of a wider workflow, then use [IssueTokensFlow].
 *
 * @property tokensToIssue a list of [AbstractToken]s to issue
 * @property observers aset of observing [Party]s
 */
@StartableByService
@StartableByRPC
@InitiatingFlow
class IssueTokens<T : TokenType>(
        val tokensToIssue: List<AbstractToken<T>>,
        val observers: List<Party>
) : FlowLogic<SignedTransaction>() {

    /* Fungible token constructors. */

    constructor(token: FungibleToken<T>, observers: List<Party> = emptyList())
            : this(listOf(token), observers)

    constructor(tokenType: T, amount: Long, issuer: Party, holder: Party, observers: List<Party> = emptyList())
            : this(listOf(amount of tokenType issuedBy issuer heldBy holder), observers)

    constructor(issuedTokenType: IssuedTokenType<T>, amount: Long, holder: AbstractParty, observers: List<Party> = emptyList())
            : this(listOf(amount of issuedTokenType heldBy holder), observers)

    constructor(issuedTokenType: IssuedTokenType<T>, amount: Long, observers: List<Party> = emptyList())
            : this(listOf(amount of issuedTokenType heldBy issuedTokenType.issuer), observers)

    constructor(tokenType: T, amount: Long, issuer: Party, observers: List<Party> = emptyList())
            : this(listOf(amount of tokenType issuedBy issuer heldBy issuer), observers)

    /* Non-fungible token constructors. */

    constructor(token: NonFungibleToken<T>, observers: List<Party> = emptyList())
            : this(listOf(token), observers)

    constructor(tokenType: T, issuer: Party, holder: AbstractParty, observers: List<Party> = emptyList())
            : this(listOf(tokenType issuedBy issuer heldBy holder), observers)

    constructor(issuedTokenType: IssuedTokenType<T>, holder: AbstractParty, observers: List<Party> = emptyList())
            : this(listOf(issuedTokenType heldBy holder), observers)

    constructor(issuedTokenType: IssuedTokenType<T>, observers: List<Party> = emptyList())
            : this(listOf(issuedTokenType heldBy issuedTokenType.issuer), observers)

    constructor(tokenType: T, issuer: Party, observers: List<Party> = emptyList())
            : this(listOf(tokenType issuedBy issuer heldBy issuer), observers)

    @Suspendable
    override fun call(): SignedTransaction {
        val observerSessions = sessionsForParties(observers)
        val participantSessions = sessionsForParticipants(tokensToIssue)
        return subFlow(IssueTokensFlow(tokensToIssue, participantSessions, observerSessions))
    }
}

@InitiatedBy(IssueTokens::class)
class IssueTokensHandler(val otherSession: FlowSession) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() = subFlow(IssueTokensFlowHandler(otherSession))
}

/**
 * A flow for issuing fungible or non-fungible tokens which initiates its own participantSessions. This is the case when called
 * from the node shell or in a unit test. However, in the case where you already have a session with another [Party] and
 * you wish to issue tokens as part of a wider workflow, then use [IssueTokensFlow].
 *
 * @property tokensToIssue a list of [AbstractToken]s to issue
 * @property observers aset of observing [Party]s
 */
@StartableByService
@StartableByRPC
@InitiatingFlow
class ConfidentialIssueTokens<T : TokenType>(
        val tokensToIssue: List<AbstractToken<T>>,
        val observers: List<Party>
) : FlowLogic<SignedTransaction>() {
    @Suspendable
    override fun call(): SignedTransaction {
        val observerSessions = sessionsForParties(observers)
        val participantSessions = sessionsForParticipants(tokensToIssue)
        return subFlow(ConfidentialIssueTokensFlow(tokensToIssue, participantSessions, observerSessions))
    }
}

@InitiatedBy(ConfidentialIssueTokens::class)
class ConfidentialIssueTokensHandler(val otherSession: FlowSession) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() = subFlow(ConfidentialIssueTokensFlowHandler(otherSession))
}