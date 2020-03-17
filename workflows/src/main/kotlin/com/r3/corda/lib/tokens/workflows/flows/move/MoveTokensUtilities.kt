@file:JvmName("MoveTokensUtilities")
package com.r3.corda.lib.tokens.workflows.flows.move

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.lib.tokens.contracts.commands.MoveTokenCommand
import com.r3.corda.lib.tokens.contracts.states.AbstractToken
import com.r3.corda.lib.tokens.contracts.types.IssuedTokenType
import com.r3.corda.lib.tokens.contracts.types.TokenType
import com.r3.corda.lib.tokens.selection.TokenQueryBy
import com.r3.corda.lib.tokens.selection.database.selector.DatabaseTokenSelection
import com.r3.corda.lib.tokens.workflows.internal.selection.generateMoveNonFungible
import com.r3.corda.lib.tokens.workflows.types.PartyAndAmount
import com.r3.corda.lib.tokens.workflows.types.PartyAndToken
import com.r3.corda.lib.tokens.workflows.types.toPairs
import com.r3.corda.lib.tokens.workflows.utilities.addTokenTypeJar
import net.corda.core.contracts.Amount
import net.corda.core.contracts.StateAndRef
import net.corda.core.identity.AbstractParty
import net.corda.core.node.ServiceHub
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.transactions.TransactionBuilder

/* For fungible tokens. */

/**
 * Adds a set of token moves to a transaction using specific inputs and outputs.
 */
@Suspendable
fun addMoveTokens(
        transactionBuilder: TransactionBuilder,
        inputs: List<StateAndRef<AbstractToken>>,
        outputs: List<AbstractToken>
): TransactionBuilder {
    val outputGroups: Map<IssuedTokenType, List<AbstractToken>> = outputs.groupBy { it.issuedTokenType }
    val inputGroups: Map<IssuedTokenType, List<StateAndRef<AbstractToken>>> = inputs.groupBy {
        it.state.data.issuedTokenType
    }

    check(outputGroups.keys == inputGroups.keys) {
        "Input and output token types must correspond to each other when moving tokensToIssue"
    }

    transactionBuilder.apply {
        // Add a notary to the transaction.
        // TODO: Deal with notary change.
        notary = inputs.map { it.state.notary }.toSet().single()
        outputGroups.forEach { issuedTokenType: IssuedTokenType, outputStates: List<AbstractToken> ->
            val inputGroup = inputGroups[issuedTokenType]
                    ?: throw IllegalArgumentException("No corresponding inputs for the outputs issued token type: $issuedTokenType")
            val keys = inputGroup.map { it.state.data.holder.owningKey }

            var inputStartingIdx = inputStates().size
            var outputStartingIdx = outputStates().size

            val inputIdx = inputGroup.map {
                addInputState(it)
                inputStartingIdx++
            }

            val outputIdx = outputStates.map {
                addOutputState(it)
                outputStartingIdx++
            }

            addCommand(MoveTokenCommand(issuedTokenType, inputs = inputIdx, outputs = outputIdx), keys)
        }
    }

    addTokenTypeJar(inputs.map { it.state.data } + outputs, transactionBuilder)

    return transactionBuilder
}

/**
 * Adds a single token move to a transaction.
 */
@Suspendable
fun addMoveTokens(
        transactionBuilder: TransactionBuilder,
        input: StateAndRef<AbstractToken>,
        output: AbstractToken
): TransactionBuilder {
    return addMoveTokens(transactionBuilder = transactionBuilder, inputs = listOf(input), outputs = listOf(output))
}

/**
 * Adds multiple token moves to transaction. [partiesAndAmounts] parameter specify which parties should receive amounts of the token.
 * With possible change paid to [changeHolder]. This method will combine multiple token amounts from different issuers if needed.
 * If you would like to choose only tokens from one issuer you can provide optional [queryCriteria] for move generation.
 * Note: For now this method always uses database token selection, to use in memory one, use [addMoveTokens] with already selected
 * input and output states.
 */
@Suspendable
@JvmOverloads
fun addMoveFungibleTokens(
        transactionBuilder: TransactionBuilder,
        serviceHub: ServiceHub,
        partiesAndAmounts: List<PartyAndAmount<TokenType>>,
        changeHolder: AbstractParty,
        queryCriteria: QueryCriteria? = null
): TransactionBuilder {
    // TODO For now default to database query, but switch this line on after we can change API in 2.0
//    val selector: Selector = ConfigSelection.getPreferredSelection(serviceHub)
    val selector = DatabaseTokenSelection(serviceHub)
    val (inputs, outputs) = selector.generateMove(partiesAndAmounts.toPairs(), changeHolder, TokenQueryBy(queryCriteria = queryCriteria), transactionBuilder.lockId)
    return addMoveTokens(transactionBuilder = transactionBuilder, inputs = inputs, outputs = outputs)
}

/**
 * Add single move of [amount] of token to the new [holder]. Possible change output will be paid to [changeHolder].
 * This method will combine multiple token amounts from different issuers if needed.
 * If you would like to choose only tokens from one issuer you can provide optional [queryCriteria] for move generation.
 * Note: For now this method always uses database token selection, to use in memory one, use [addMoveTokens] with already selected
 * input and output states.
 */
@Suspendable
@JvmOverloads
fun addMoveFungibleTokens(
        transactionBuilder: TransactionBuilder,
        serviceHub: ServiceHub,
        amount: Amount<TokenType>,
        holder: AbstractParty,
        changeHolder: AbstractParty,
        queryCriteria: QueryCriteria? = null
): TransactionBuilder {
    return addMoveFungibleTokens(
            transactionBuilder = transactionBuilder,
            serviceHub = serviceHub,
            partiesAndAmounts = listOf(PartyAndAmount(holder, amount)),
            changeHolder = changeHolder,
            queryCriteria = queryCriteria
    )
}

/* For non-fungible tokens. */

/**
 * Add single move of [token] to the new [holder].
 * Provide optional [queryCriteria] for move generation.
 */
@Suspendable
@JvmOverloads
fun addMoveNonFungibleTokens(
        transactionBuilder: TransactionBuilder,
        serviceHub: ServiceHub,
        token: TokenType,
        holder: AbstractParty,
        queryCriteria: QueryCriteria? = null
): TransactionBuilder {
    return generateMoveNonFungible(transactionBuilder, PartyAndToken(holder, token), serviceHub.vaultService, queryCriteria)
}

/**
 * Add single move of token to the new holder specified using [partyAndToken] parameter.
 * Provide optional [queryCriteria] for move generation.
 */
@Suspendable
@JvmOverloads
fun addMoveNonFungibleTokens(
        transactionBuilder: TransactionBuilder,
        serviceHub: ServiceHub,
        partyAndToken: PartyAndToken,
        queryCriteria: QueryCriteria? = null
): TransactionBuilder {
    return generateMoveNonFungible(transactionBuilder, partyAndToken, serviceHub.vaultService, queryCriteria)
}
