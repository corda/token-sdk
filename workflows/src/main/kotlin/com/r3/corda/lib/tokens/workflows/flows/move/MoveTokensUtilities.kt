package com.r3.corda.lib.tokens.workflows.flows.move

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.lib.tokens.contracts.commands.MoveTokenCommand
import com.r3.corda.lib.tokens.contracts.states.AbstractToken
import com.r3.corda.lib.tokens.contracts.types.IssuedTokenType
import com.r3.corda.lib.tokens.contracts.types.TokenType
import com.r3.corda.lib.tokens.workflows.internal.selection.TokenSelection
import com.r3.corda.lib.tokens.workflows.internal.selection.generateMoveNonFungible
import com.r3.corda.lib.tokens.workflows.types.PartyAndAmount
import com.r3.corda.lib.tokens.workflows.types.PartyAndToken
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

/**
 * Adds multiple token moves to transaction. [partiesAndAmounts] parameter specify which parties should receive amounts of the token.
 * With possible change paid to [changeHolder]. This method will combine multiple token amounts from different issuers if needed.
 * If you would like to choose only tokens from one issuer you can provide optional [queryCriteria] for move generation.
 */
@Suspendable
@JvmOverloads
fun <T : TokenType> addMoveFungibleTokens(
        transactionBuilder: TransactionBuilder,
        serviceHub: ServiceHub,
        partiesAndAmounts: List<PartyAndAmount<T>>,
        changeHolder: AbstractParty,
        queryCriteria: QueryCriteria? = null
): TransactionBuilder {
    val tokenSelection = TokenSelection(serviceHub)
    val (inputs, outputs) = tokenSelection.generateMove(
            lockId = transactionBuilder.lockId,
            partyAndAmounts = partiesAndAmounts,
            queryCriteria = queryCriteria,
            changeHolder = changeHolder
    )
    return addMoveTokens(transactionBuilder = transactionBuilder, inputs = inputs, outputs = outputs)
}

/**
 * Add single move of [amount] of token to the new [holder]. Possible change output will be paid to [changeHolder].
 * This method will combine multiple token amounts from different issuers if needed.
 * If you would like to choose only tokens from one issuer you can provide optional [queryCriteria] for move generation.
 */
@Suspendable
@JvmOverloads
fun <T : TokenType> addMoveFungibleTokens(
        transactionBuilder: TransactionBuilder,
        serviceHub: ServiceHub,
        amount: Amount<T>,
        holder: AbstractParty,
        changeHolder: AbstractParty,
        queryCriteria: QueryCriteria? = null
): TransactionBuilder {
    return addMoveFungibleTokens(
            transactionBuilder = transactionBuilder,
            serviceHub = serviceHub,
            partyAndAmount = PartyAndAmount(holder, amount),
            queryCriteria = queryCriteria,
            changeHolder = changeHolder
    )
}

// TODO don't need it
/**
 * Add single move of amount of token to the new holder specified by [partyAndAmount] parameter. Possible change output will be paid to [changeHolder].
 * This method will combine multiple token amounts from different issuers if needed.
 * If you would like to choose only tokens from one issuer you can provide optional [queryCriteria] for move generation.
 */
@Suspendable
@JvmOverloads
fun <T : TokenType> addMoveFungibleTokens(
        transactionBuilder: TransactionBuilder,
        serviceHub: ServiceHub,
        partyAndAmount: PartyAndAmount<T>,
        changeHolder: AbstractParty,
        queryCriteria: QueryCriteria? = null
): TransactionBuilder {
    return addMoveFungibleTokens(
            transactionBuilder = transactionBuilder,
            serviceHub = serviceHub,
            partiesAndAmounts = listOf(partyAndAmount),
            queryCriteria = queryCriteria,
            changeHolder = changeHolder
    )
}

/* For non-fungible tokens. */

/**
 * Add single move of [token] to the new [holder].
 * Provide optional [queryCriteria] for move generation.
 */
@Suspendable
@JvmOverloads
fun <T : TokenType> addMoveNonFungibleTokens(
        transactionBuilder: TransactionBuilder,
        serviceHub: ServiceHub,
        token: T,
        holder: AbstractParty,
        queryCriteria: QueryCriteria? = null
): TransactionBuilder {
    return generateMoveNonFungible(transactionBuilder, PartyAndToken(holder, token), serviceHub.vaultService, queryCriteria)
}

// TODO don't need it
/**
 * Add single move of token to the new holder specified using [partyAndToken] parameter.
 * Provide optional [queryCriteria] for move generation.
 */
@Suspendable
@JvmOverloads
fun <T : TokenType> addMoveNonFungibleTokens(
        transactionBuilder: TransactionBuilder,
        serviceHub: ServiceHub,
        partyAndToken: PartyAndToken<T>,
        queryCriteria: QueryCriteria? = null
): TransactionBuilder {
    return generateMoveNonFungible(transactionBuilder, partyAndToken, serviceHub.vaultService, queryCriteria)
}

/* FlowLogic extension functions. */

/**
 * Add single move of token to the new holder specified using [partyAndToken] parameter.
 * Provide optional [queryCriteria] for move generation.
 */
@Suspendable
@JvmOverloads
fun <T : TokenType> FlowLogic<*>.addMoveTokens(
        transactionBuilder: TransactionBuilder,
        partyAndToken: PartyAndToken<T>,
        queryCriteria: QueryCriteria? = null
): TransactionBuilder {
    return addMoveNonFungibleTokens(transactionBuilder, serviceHub, partyAndToken, queryCriteria)
}

/**
 * Add single move of [token] to the new [holder].
 * Provide optional [queryCriteria] for move generation.
 */
@Suspendable
@JvmOverloads
fun <T : TokenType> FlowLogic<*>.addMoveTokens(
        transactionBuilder: TransactionBuilder,
        token: T,
        holder: AbstractParty,
        queryCriteria: QueryCriteria? = null
): TransactionBuilder {
    return addMoveNonFungibleTokens(transactionBuilder, serviceHub, token, holder, queryCriteria)
}

/**
 * Add single move of [amount] of token to the new [holder]. Possible change output will be paid to [changeHolder].
 * This method will combine multiple token amounts from different issuers if needed.
 * If you would like to choose only tokens from one issuer you can provide optional [queryCriteria] for move generation.
 */
@Suspendable
@JvmOverloads
fun <T : TokenType> FlowLogic<*>.addMoveTokens(
        transactionBuilder: TransactionBuilder,
        amount: Amount<T>,
        holder: AbstractParty,
        changeHolder: AbstractParty,
        queryCriteria: QueryCriteria? = null
): TransactionBuilder {
    return addMoveFungibleTokens(transactionBuilder, serviceHub, amount, holder, changeHolder, queryCriteria)
}

/**
 * Add single move of amount of token to the new holder specified by [partyAndAmount] parameter. Possible change output will be paid to [changeHolder].
 * This method will combine multiple token amounts from different issuers if needed.
 * If you would like to choose only tokens from one issuer you can provide optional [queryCriteria] for move generation.
 */
@Suspendable
@JvmOverloads
fun <T : TokenType> FlowLogic<*>.addMoveTokens(
        transactionBuilder: TransactionBuilder,
        partyAndAmount: PartyAndAmount<T>,
        changeHolder: AbstractParty,
        queryCriteria: QueryCriteria? = null
): TransactionBuilder {
    return addMoveFungibleTokens(transactionBuilder, serviceHub, partyAndAmount, changeHolder, queryCriteria)
}

/**
 * Adds multiple token moves to transaction. [partiesAndAmounts] parameter specify which parties should receive amounts of the token.
 * With possible change paid to [changeHolder].
 * This method will combine multiple token amounts from different issuers if needed.
 * If you would like to choose only tokens from one issuer you can provide optional [queryCriteria] for move generation.
 */
@Suspendable
@JvmOverloads
fun <T : TokenType> FlowLogic<*>.addMoveTokens(
        transactionBuilder: TransactionBuilder,
        partiesAndAmounts: List<PartyAndAmount<T>>,
        changeHolder: AbstractParty,
        queryCriteria: QueryCriteria? = null
): TransactionBuilder {
    return addMoveFungibleTokens(transactionBuilder, serviceHub, partiesAndAmounts, changeHolder, queryCriteria)
}