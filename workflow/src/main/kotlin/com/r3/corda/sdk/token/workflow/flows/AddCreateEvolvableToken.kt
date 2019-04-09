package com.r3.corda.sdk.token.workflow.flows

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.sdk.token.contracts.commands.Create
import com.r3.corda.sdk.token.contracts.states.EvolvableTokenType
import com.r3.corda.sdk.token.contracts.utilities.withNotary
import net.corda.core.contracts.ContractClassName
import net.corda.core.contracts.TransactionState
import net.corda.core.flows.FlowLogic
import net.corda.core.identity.Party
import net.corda.core.transactions.TransactionBuilder

class AddCreateEvolvableToken<T : EvolvableTokenType>(
        val transactionBuilder: TransactionBuilder,
        val state: TransactionState<T>
) : FlowLogic<TransactionBuilder>() {

    constructor(transactionBuilder: TransactionBuilder, evolvableToken: T, contract: ContractClassName, notary: Party)
            : this(transactionBuilder, TransactionState(evolvableToken, contract, notary))

    constructor(transactionBuilder: TransactionBuilder, evolvableToken: T, notary: Party) : this(transactionBuilder, evolvableToken withNotary notary)

    @Suspendable
    override fun call(): TransactionBuilder {
        return transactionBuilder.apply {
            addCommand(data = Create(), keys = signingKeys)
            addOutputState(state = state)
        }
    }

    private val maintainers get(): Set<Party> = state.data.maintainers.toSet()

    private val signingKeys get() = maintainers.map { it.owningKey }

}