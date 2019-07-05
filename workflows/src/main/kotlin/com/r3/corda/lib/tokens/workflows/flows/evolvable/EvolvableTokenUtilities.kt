package com.r3.corda.lib.tokens.workflows.flows.evolvable

import com.r3.corda.lib.tokens.contracts.commands.Create
import com.r3.corda.lib.tokens.contracts.commands.Update
import com.r3.corda.lib.tokens.contracts.states.EvolvableTokenType
import com.r3.corda.lib.tokens.contracts.utilities.withNotary
import com.r3.corda.lib.tokens.workflows.internal.flows.distribution.getDistributionList
import net.corda.core.contracts.ContractClassName
import net.corda.core.contracts.StateAndRef
import net.corda.core.contracts.TransactionState
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.node.ServiceHub
import net.corda.core.transactions.TransactionBuilder

/**
 * Helper method for assembling a [Create] transaction for [EvolvableToken]s. This accepts a [TransactionBuilder] as well
 * as token details, and adds commands and outputs to the transaction. Returns the [TransactionBuilder]
 * to allow for continued assembly of the transaction, as needed.
 */
fun <T : EvolvableTokenType> addCreateEvolvableToken(
        transactionBuilder: TransactionBuilder,
        state: TransactionState<T>
): TransactionBuilder {
    val maintainers = state.data.maintainers.toSet()
    val signingKeys = maintainers.map { it.owningKey }
    return transactionBuilder
            .addCommand(data = Create(), keys = signingKeys)
            .addOutputState(state = state)
}

fun <T : EvolvableTokenType> addCreateEvolvableToken(
        transactionBuilder: TransactionBuilder,
        evolvableToken: T,
        contract: ContractClassName,
        notary: Party
): TransactionBuilder {
    return addCreateEvolvableToken(transactionBuilder, TransactionState(evolvableToken, contract, notary))
}

fun <T : EvolvableTokenType> addCreateEvolvableToken(
        transactionBuilder: TransactionBuilder,
        evolvableToken: T,
        notary: Party
): TransactionBuilder {
    return addCreateEvolvableToken(transactionBuilder, evolvableToken withNotary notary)
}

/**
 * Helper method for assembling an [Update] transaction for [EvolvableToken]s. This accepts a [TransactionBuilder] as well
 * as old and new states, and adds commands, inputs, and outputs to the transaction. Returns the [TransactionBuilder]
 * to allow for continued assembly of the transaction, as needed.
 */
fun addUpdateEvolvableToken(
        transactionBuilder: TransactionBuilder,
        oldStateAndRef: StateAndRef<EvolvableTokenType>,
        newState: EvolvableTokenType
): TransactionBuilder {
    val oldState = oldStateAndRef.state.data
    val maintainers = (oldState.maintainers + newState.maintainers).toSet()
    val signingKeys = maintainers.map { it.owningKey }
    return transactionBuilder
            .addCommand(data = Update(), keys = signingKeys)
            .addInputState(oldStateAndRef)
            .addOutputState(state = newState, contract = oldStateAndRef.state.contract)
}

internal fun Iterable<EvolvableTokenType>.maintainers(): Set<Party> = fold(emptySet(), { acc, txState -> acc.plus(txState.maintainers) })

internal fun Iterable<EvolvableTokenType>.participants(): Set<AbstractParty> = fold(emptySet(), { acc, txState -> acc.plus(txState.participants) })

internal fun Iterable<EvolvableTokenType>.otherMaintainers(ourIdentity: Party) = maintainers().minus(ourIdentity)

internal fun subscribersForState(state: EvolvableTokenType, serviceHub: ServiceHub): Set<Party> {
    return getDistributionList(serviceHub, state.linearId).map { it.party }.toSet()
}