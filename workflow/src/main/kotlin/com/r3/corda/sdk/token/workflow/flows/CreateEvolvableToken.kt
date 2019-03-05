package com.r3.corda.sdk.token.workflow.flows

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.sdk.token.contracts.commands.Create
import com.r3.corda.sdk.token.contracts.states.EvolvableTokenType
import com.r3.corda.sdk.token.contracts.utilities.withNotary
import net.corda.core.contracts.ContractClassName
import net.corda.core.contracts.TransactionState
import net.corda.core.flows.FinalityFlow
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.StartableByRPC
import net.corda.core.identity.Party
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker

/**
 * Flow for creating an evolvable token type. This is just a simple flow for now. Although it can be invoked via the
 * shell, it is more likely to be used for unit testing or called as an inlined-subflow.
 */
@StartableByRPC
class CreateEvolvableToken<T : EvolvableTokenType>(
        val transactionState: TransactionState<T>
) : FlowLogic<SignedTransaction>() {

    constructor(evolvableToken: T, contract: ContractClassName, notary: Party)
            : this(TransactionState(evolvableToken, contract, notary))

    constructor(evolvableToken: T, notary: Party) : this(evolvableToken withNotary notary)

    companion object {
        object CREATING : ProgressTracker.Step("Creating transaction proposal.")
        object SIGNING : ProgressTracker.Step("Signing transaction proposal.")
        object RECORDING : ProgressTracker.Step("Recording signed transaction.") {
            override fun childProgressTracker() = FinalityFlow.tracker()
        }

        fun tracker() = ProgressTracker(CREATING, SIGNING, RECORDING)
    }

    override val progressTracker: ProgressTracker = tracker()

    @Suspendable
    override fun call(): SignedTransaction {
        // Create a transaction which updates the ledger with the new evolvable token.
        // Note that initially it is not shared with anyone.
        progressTracker.currentStep = CREATING
        val evolvableToken = transactionState.data
        val signingKeys = evolvableToken.maintainers.map { it.owningKey }
        val utx: TransactionBuilder = TransactionBuilder().apply {
            addCommand(data = Create(), keys = signingKeys)
            addOutputState(state = transactionState)
        }

        // Sign the transaction. Only Concrete Parties should be used here.
        progressTracker.currentStep = SIGNING
        val stx: SignedTransaction = serviceHub.signInitialTransaction(utx)

        // No need to pass in a session as there's no counterparty involved.
        progressTracker.currentStep = RECORDING
        return subFlow(FinalityFlow(
                transaction = stx,
                progressTracker = RECORDING.childProgressTracker(),
                sessions = emptyList()
        ))
    }
}