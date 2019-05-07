package com.r3.corda.sdk.token.workflow.flows

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.sdk.token.contracts.commands.IssueTokenCommand
import com.r3.corda.sdk.token.contracts.states.AbstractToken
import com.r3.corda.sdk.token.contracts.states.FungibleToken
import com.r3.corda.sdk.token.contracts.states.NonFungibleToken
import com.r3.corda.sdk.token.contracts.types.IssuedTokenType
import com.r3.corda.sdk.token.contracts.types.TokenType
import com.r3.corda.sdk.token.contracts.utilities.heldBy
import com.r3.corda.sdk.token.contracts.utilities.issuedBy
import com.r3.corda.sdk.token.contracts.utilities.of
import com.r3.corda.sdk.token.workflow.flows.issue.addIssueTokens
import net.corda.core.flows.FinalityFlow
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.flows.InitiatedBy
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.ReceiveFinalityFlow
import net.corda.core.flows.StartableByRPC
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.node.ServiceHub
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder

//TODO
open class IssueTokens<T : TokenType>(
        val tokens: Set<AbstractToken<T>>,
        val sessions: Set<FlowSession>
) : FlowLogic<SignedTransaction>() {

    /**
     * Creates a [TransactionBuilder] with the preferred notary, the requested set of tokens as outputs and adds
     * [IssueTokenCommand]s for each group of states (grouped by [IssuedTokenType].
     */
    @Suspendable
    fun createIssueTokensTransaction(services: ServiceHub, tokens: Set<AbstractToken<T>>): TransactionBuilder {
        // TODO fix notary choice
        val notary = services.networkMapCache.notaryIdentities.first()
        val transactionBuilder = TransactionBuilder(notary)
//        val transactionBuilder = TransactionBuilder(getPreferredNotary())
        return addIssueTokens(tokens.toList(), transactionBuilder)
    }

    // TODO fix sessions
    @Suspendable
    override fun call(): SignedTransaction {
        val transactionBuilder = createIssueTokensTransaction(serviceHub, tokens)
        val signedTransaction = serviceHub.signInitialTransaction(transactionBuilder)
        // TODO hack
        val holder = tokens.first().holder as Party
        // todo end hack
        return subFlow(FinalityFlow(signedTransaction, initiateFlow(holder)))
    }
}


/*

Add notary initially if no inputs present already.

Add outputs: all must have the same notary.
Add inputs

Add inputs and outputs

How are the inputs and outputs linked together?

Check if all inputs have the same notary. if they don't then
Set notary. Which notary? Specify or gets the first one. or uses the currnet one.
Don't need to set contracts as they are linked via the annotation.
 */