package com.r3.corda.lib.tokens.workflows.flows.rpc

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.lib.tokens.contracts.states.AbstractToken
import com.r3.corda.lib.tokens.contracts.states.FungibleToken
import com.r3.corda.lib.tokens.contracts.states.NonFungibleToken
import com.r3.corda.lib.tokens.contracts.types.IssuedTokenType
import com.r3.corda.lib.tokens.contracts.types.TokenType
import com.r3.corda.lib.tokens.contracts.utilities.heldBy
import com.r3.corda.lib.tokens.contracts.utilities.issuedBy
import com.r3.corda.lib.tokens.workflows.flows.issue.ConfidentialIssueTokensFlow
import com.r3.corda.lib.tokens.workflows.flows.issue.ConfidentialIssueTokensFlowHandler
import com.r3.corda.lib.tokens.workflows.flows.issue.IssueTokensFlow
import com.r3.corda.lib.tokens.workflows.flows.issue.IssueTokensFlowHandler
import com.r3.corda.lib.tokens.workflows.utilities.heldBy
import com.r3.corda.lib.tokens.workflows.utilities.sessionsForParticipants
import com.r3.corda.lib.tokens.workflows.utilities.sessionsForParties
import net.corda.core.contracts.Amount
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
 * A flow for issuing fungible or non-fungible tokens which initiates its own participantSessions. This is the case when
 * called from the node rpc or in a unit test. However, in the case where you already have a session with another [Party]
 * and you wish to issue tokens as part of a wider workflow, then use [IssueTokensFlow].
 *
 * @property tokensToIssue a list of [AbstractToken]s to issue
 * @property observers aset of observing [Party]s
 */
@StartableByService
@StartableByRPC
@InitiatingFlow
class IssueTokens<T : TokenType>
@JvmOverloads
constructor(
        val tokensToIssue: List<AbstractToken<T>>,
        val observers: List<Party> = emptyList()
) : FlowLogic<SignedTransaction>() {

    /* Fungible token constructors. */
    @JvmOverloads
    constructor(token: FungibleToken<T>, observers: List<Party> = emptyList())
            : this(listOf(token), observers)

    @JvmOverloads
    constructor(amount: Amount<T>, issuer: Party, holder: Party, observers: List<Party> = emptyList())
            : this(listOf(amount issuedBy issuer heldBy holder), observers)

    @JvmOverloads
    constructor(amount: Amount<IssuedTokenType<T>>, holder: AbstractParty, observers: List<Party> = emptyList())
            : this(listOf(amount heldBy holder), observers)

    @JvmOverloads
    constructor(amount: Amount<IssuedTokenType<T>>, observers: List<Party> = emptyList())
            : this(listOf(amount heldBy amount.token.issuer), observers)

    @JvmOverloads
    constructor(amount: Amount<T>, issuer: Party, observers: List<Party> = emptyList())
            : this(listOf(amount issuedBy issuer heldBy issuer), observers)

    /* Non-fungible token constructors. */
    @JvmOverloads
    constructor(token: NonFungibleToken<T>, observers: List<Party> = emptyList())
            : this(listOf(token), observers)

    @JvmOverloads
    constructor(tokenType: T, issuer: Party, holder: AbstractParty, observers: List<Party> = emptyList())
            : this(listOf(tokenType issuedBy issuer heldBy holder), observers)

    @JvmOverloads
    constructor(issuedTokenType: IssuedTokenType<T>, holder: AbstractParty, observers: List<Party> = emptyList())
            : this(listOf(issuedTokenType heldBy holder), observers)

    @JvmOverloads
    constructor(issuedTokenType: IssuedTokenType<T>, observers: List<Party> = emptyList())
            : this(listOf(issuedTokenType heldBy issuedTokenType.issuer), observers)

    @JvmOverloads
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
 * from the node rpc or in a unit test. However, in the case where you already have a session with another [Party] and
 * you wish to issue tokens as part of a wider workflow, then use [IssueTokensFlow].
 *
 * @property tokensToIssue a list of [AbstractToken]s to issue
 * @property observers aset of observing [Party]s
 */
@StartableByService
@StartableByRPC
@InitiatingFlow
class ConfidentialIssueTokens<T : TokenType>
@JvmOverloads
constructor(
        val tokensToIssue: List<AbstractToken<T>>,
        val observers: List<Party> = emptyList()
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