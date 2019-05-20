package com.r3.corda.sdk.token.workflow.flows.move

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.sdk.token.contracts.commands.MoveTokenCommand
import com.r3.corda.sdk.token.contracts.states.AbstractToken
import com.r3.corda.sdk.token.contracts.types.IssuedTokenType
import com.r3.corda.sdk.token.contracts.types.TokenType
import com.r3.corda.sdk.token.workflow.flows.internal.selection.TokenSelection
import com.r3.corda.sdk.token.workflow.flows.internal.selection.generateMoveNonFungible
import com.r3.corda.sdk.token.workflow.types.PartyAndAmount
import com.r3.corda.sdk.token.workflow.types.PartyAndToken
import net.corda.core.contracts.Amount
import net.corda.core.contracts.StateAndRef
import net.corda.core.flows.FlowLogic
import net.corda.core.identity.AbstractParty
import net.corda.core.node.ServiceHub
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.transactions.TransactionBuilder

/* For fungible tokens. */

/**
 * Adds a set of token moves to a transaction using specific inputs and outputs.
 */
@Suspendable
fun <T : TokenType> addMoveTokens(
        transactionBuilder: TransactionBuilder,
        inputs: List<StateAndRef<AbstractToken<T>>>,
        outputs: List<AbstractToken<T>>
): TransactionBuilder {
    val outputGroups: Map<IssuedTokenType<T>, List<AbstractToken<T>>> = outputs.groupBy { it.issuedTokenType }
    val inputGroups: Map<IssuedTokenType<T>, List<StateAndRef<AbstractToken<T>>>> = inputs.groupBy {
        it.state.data.issuedTokenType
    }

    check(outputGroups.keys == inputGroups.keys) {
        "Input and output token types must correspond to each other when moving tokensToIssue"
    }

    transactionBuilder.apply {
        // Add a notary to the transaction.
        // TODO: Deal with notary change.
        notary = inputs.map { it.state.notary }.toSet().single()
        outputGroups.forEach { issuedTokenType: IssuedTokenType<T>, outputStates: List<AbstractToken<T>> ->
            val inputGroup = inputGroups[issuedTokenType]
                    ?: throw IllegalArgumentException("No corresponding inputs for the outputs issued token type: $issuedTokenType")
            val keys = inputGroup.map { it.state.data.holder.owningKey }
            addCommand(MoveTokenCommand(issuedTokenType), keys)
            inputGroup.forEach { addInputState(it) }
            outputStates.forEach { addOutputState(it) }
        }
    }

    return transactionBuilder
}

/**
 * Adds a single token move to a transaction.
 */
@Suspendable
fun <T : TokenType> addMoveTokens(
        transactionBuilder: TransactionBuilder,
        input: StateAndRef<AbstractToken<T>>,
        output: AbstractToken<T>
): TransactionBuilder {
    return addMoveTokens(transactionBuilder = transactionBuilder, inputs = listOf(input), outputs = listOf(output))
}

@Suspendable
fun <T : TokenType> addMoveTokens(
        transactionBuilder: TransactionBuilder,
        serviceHub: ServiceHub,
        partiesAndAmounts: List<PartyAndAmount<T>>,
        queryCriteria: QueryCriteria? = null,
        changeOwner: AbstractParty? = null
): TransactionBuilder {
    val tokenSelection = TokenSelection(serviceHub)
    val (inputs, outputs) = tokenSelection.generateMove(
            lockId = transactionBuilder.lockId,
            partyAndAmounts = partiesAndAmounts,
            queryCriteria = queryCriteria,
            changeOwner = changeOwner
    )
    return addMoveTokens(transactionBuilder = transactionBuilder, inputs = inputs, outputs = outputs)
}

@Suspendable
fun <T : TokenType> addMoveTokens(
        transactionBuilder: TransactionBuilder,
        serviceHub: ServiceHub,
        amount: Amount<T>,
        holder: AbstractParty,
        queryCriteria: QueryCriteria?,
        changeOwner: AbstractParty? = null
): TransactionBuilder {
    return addMoveTokens(
            transactionBuilder = transactionBuilder,
            serviceHub = serviceHub,
            partyAndAmount = PartyAndAmount(holder, amount),
            queryCriteria = queryCriteria,
            changeOwner = changeOwner
    )
}

@Suspendable
fun <T : TokenType> addMoveTokens(
        transactionBuilder: TransactionBuilder,
        serviceHub: ServiceHub,
        partyAndAmount: PartyAndAmount<T>,
        queryCriteria: QueryCriteria?,
        changeOwner: AbstractParty? = null
): TransactionBuilder {
    return addMoveTokens(
            transactionBuilder = transactionBuilder,
            serviceHub = serviceHub,
            partiesAndAmounts = listOf(partyAndAmount),
            queryCriteria = queryCriteria,
            changeOwner = changeOwner
    )
}

/* For non-fungible tokens. */

@Suspendable
fun <T : TokenType> addMoveTokens(
        transactionBuilder: TransactionBuilder,
        serviceHub: ServiceHub,
        token: T,
        holder: AbstractParty,
        queryCriteria: QueryCriteria?
): TransactionBuilder {
    return generateMoveNonFungible(transactionBuilder, PartyAndToken(holder, token), serviceHub.vaultService, queryCriteria)
}

@Suspendable
fun <T : TokenType> addMoveTokens(
        transactionBuilder: TransactionBuilder,
        serviceHub: ServiceHub,
        partyAndToken: PartyAndToken<T>,
        queryCriteria: QueryCriteria?
): TransactionBuilder {
    return generateMoveNonFungible(transactionBuilder, partyAndToken, serviceHub.vaultService, queryCriteria)
}

/* FlowLogic extension functions. */

@Suspendable
fun <T : TokenType> FlowLogic<*>.addMoveTokens(
        transactionBuilder: TransactionBuilder,
        partyAndToken: PartyAndToken<T>,
        queryCriteria: QueryCriteria?
): TransactionBuilder {
    return addMoveTokens(transactionBuilder, serviceHub, partyAndToken, queryCriteria)
}

@Suspendable
fun <T : TokenType> FlowLogic<*>.addMoveTokens(
        transactionBuilder: TransactionBuilder,
        token: T,
        holder: AbstractParty,
        queryCriteria: QueryCriteria?
): TransactionBuilder {
    return addMoveTokens(transactionBuilder, serviceHub, token, holder, queryCriteria)
}

@Suspendable
fun <T : TokenType> FlowLogic<*>.addMoveTokens(
        transactionBuilder: TransactionBuilder,
        amount: Amount<T>,
        holder: AbstractParty,
        queryCriteria: QueryCriteria?,
        changeOwner: AbstractParty? = null
): TransactionBuilder {
    return addMoveTokens(transactionBuilder, serviceHub, amount, holder, queryCriteria, changeOwner)
}

@Suspendable
fun <T : TokenType> FlowLogic<*>.addMoveTokens(
        transactionBuilder: TransactionBuilder,
        partyAndAmount: PartyAndAmount<T>,
        queryCriteria: QueryCriteria?,
        changeOwner: AbstractParty? = null
): TransactionBuilder {
    return addMoveTokens(transactionBuilder, serviceHub, partyAndAmount, queryCriteria, changeOwner)
}

@Suspendable
fun <T : TokenType> FlowLogic<*>.addMoveTokens(
        transactionBuilder: TransactionBuilder,
        partiesAndAmounts: List<PartyAndAmount<T>>,
        queryCriteria: QueryCriteria?,
        changeOwner: AbstractParty? = null
): TransactionBuilder {
    return addMoveTokens(transactionBuilder, serviceHub, partiesAndAmounts, queryCriteria, changeOwner)
}