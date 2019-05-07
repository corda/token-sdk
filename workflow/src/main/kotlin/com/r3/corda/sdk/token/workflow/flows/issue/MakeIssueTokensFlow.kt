package com.r3.corda.sdk.token.workflow.flows.issue

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.sdk.token.contracts.states.AbstractToken
import com.r3.corda.sdk.token.contracts.states.FungibleToken
import com.r3.corda.sdk.token.contracts.states.NonFungibleToken
import com.r3.corda.sdk.token.contracts.types.IssuedTokenType
import com.r3.corda.sdk.token.contracts.types.TokenType
import com.r3.corda.sdk.token.contracts.utilities.heldBy
import com.r3.corda.sdk.token.contracts.utilities.issuedBy
import com.r3.corda.sdk.token.contracts.utilities.of
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.flows.InitiatedBy
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.ReceiveFinalityFlow
import net.corda.core.flows.StartableByRPC
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.transactions.SignedTransaction

// Flow startable from shell
@InitiatingFlow
@StartableByRPC
class MakeIssueTokensFlow<T : TokenType> private constructor(val tokens: List<AbstractToken<T>>) : FlowLogic<SignedTransaction>() {

    // NonFungible
    constructor(token: NonFungibleToken<T>) : this(listOf(token))

    constructor(tokenType: T, issuer: Party, holder: AbstractParty) : this(listOf(tokenType issuedBy issuer heldBy holder))

    constructor(issuedTokenType: IssuedTokenType<T>, holder: AbstractParty) : this(listOf(issuedTokenType heldBy holder))

    constructor(issuedTokenType: IssuedTokenType<T>) : this(listOf(issuedTokenType heldBy issuedTokenType.issuer))

    constructor(tokenType: T, issuer: Party) : this(listOf(tokenType issuedBy issuer heldBy issuer))

    // Fungible
    constructor(token: FungibleToken<T>) : this(listOf(token))

    constructor(tokenType: T, amount: Long, issuer: Party, holder: AbstractParty) : this(listOf(amount of tokenType issuedBy issuer heldBy holder))

    constructor(issuedTokenType: IssuedTokenType<T>, amount: Long, holder: AbstractParty) : this(listOf(amount of issuedTokenType heldBy holder))

    constructor(issuedTokenType: IssuedTokenType<T>, amount: Long) : this(listOf(amount of issuedTokenType heldBy issuedTokenType.issuer))

    constructor(tokenType: T, amount: Long, issuer: Party) : this(listOf(amount of tokenType issuedBy issuer heldBy issuer))

    @Suspendable
    override fun call(): SignedTransaction {
        return subFlow(IssueTokensFlow(tokens, emptyList()))
    }
}

@InitiatedBy(MakeIssueTokensFlow::class)
class MakeIssueTokensHandler(val otherSession: FlowSession) : FlowLogic<SignedTransaction>() {
    @Suspendable
    override fun call(): SignedTransaction {
        return subFlow(ReceiveFinalityFlow(otherSideSession = otherSession))
    }
}