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
 * Flow for creating an evolvable token type. Evolvable tokens should not use
 */
@StartableByRPC
class CreateEvolvableToken(
        val token: EvolvableToken,
        val contract: ContractClassName,
        val notary: Party
) : FlowLogic<SignedTransaction>() {
    @Suspendable
    override fun call(): SignedTransaction {
        val utx: TransactionBuilder = TransactionBuilder(notary = notary).apply {
            addCommand(data = TokenCommands.Create(), keys = listOf())
            addOutputState(state = token, contract = contract)
        }
        val stx: SignedTransaction = serviceHub.signInitialTransaction(utx)
        return subFlow(FinalityFlow(transaction = stx, sessions = listOf()))
    }
}