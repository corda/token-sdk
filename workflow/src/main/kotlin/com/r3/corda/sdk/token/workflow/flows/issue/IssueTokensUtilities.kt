package com.r3.corda.sdk.token.workflow.flows.issue

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.sdk.token.contracts.commands.IssueTokenCommand
import com.r3.corda.sdk.token.contracts.states.AbstractToken
import com.r3.corda.sdk.token.contracts.types.IssuedTokenType
import com.r3.corda.sdk.token.contracts.types.TokenType
import net.corda.core.identity.Party
import net.corda.core.transactions.TransactionBuilder

/**
 * A function that adds a list of output [AbstractToken] states to a [TransactionBuilder]. It automatically adds
 * [IssueTokenCommand] commands for each [IssuedTokenType]. A notary [Party] must be added to the [TransactionBuilder]
 * before this function can be called.
 */
@Suspendable
fun addIssueTokens(outputs: List<AbstractToken<*>>, txb: TransactionBuilder): TransactionBuilder {
    val outputGroups: Map<IssuedTokenType<TokenType>, List<AbstractToken<*>>> = outputs.groupBy { it.issuedTokenType }
    return txb.apply {
        outputGroups.forEach { issuedTokenType: IssuedTokenType<TokenType>, states: List<AbstractToken<*>> ->
            // All tokens must have the same issuer.
            val issuer = states.map { it.issuer }.toSet().single()
            addCommand(IssueTokenCommand(issuedTokenType), issuer.owningKey)
            states.forEach { state -> addOutputState(state) }
        }
    }
}

@Suspendable
fun addIssueTokens(vararg outputs: AbstractToken<*>, transactionBuilder: TransactionBuilder): TransactionBuilder {
    return addIssueTokens(outputs.toList(), transactionBuilder)
}

@Suspendable
fun addIssueTokens(output: AbstractToken<*>, transactionBuilder: TransactionBuilder): TransactionBuilder {
    return addIssueTokens(listOf(output), transactionBuilder)
}