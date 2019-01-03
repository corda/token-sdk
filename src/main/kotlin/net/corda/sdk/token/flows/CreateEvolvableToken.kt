package net.corda.sdk.token.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.ContractClassName
import net.corda.core.flows.FinalityFlow
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.StartableByRPC
import net.corda.core.identity.Party
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.sdk.token.commands.TokenCommands
import net.corda.sdk.token.types.token.EvolvableToken

/**
 * Flow for creating an evolvable token type. This is just a simple flow for now. Although it can be invoked via the
 * shell, it is more likely to be used for unit testing or called as an inlined-subflow.
 */
@StartableByRPC
class CreateEvolvableToken(
        val evolvableToken: EvolvableToken,
        val contract: ContractClassName,
        val notary: Party
) : FlowLogic<SignedTransaction>() {
    @Suspendable
    override fun call(): SignedTransaction {
        // Create a transaction which updates the ledger with the new evolvable token.
        val utx: TransactionBuilder = TransactionBuilder(notary = notary).apply {
            addCommand(data = TokenCommands.Create(), keys = listOf(evolvableToken.maintainer.owningKey))
            addOutputState(state = evolvableToken, contract = contract)
        }
        // Sign the transaction. Only Concrete Parties should be used here.
        val stx: SignedTransaction = serviceHub.signInitialTransaction(utx)
        // No need to pass in a session as there's no counterparty involved.
        return subFlow(FinalityFlow(transaction = stx, sessions = listOf()))
    }
}