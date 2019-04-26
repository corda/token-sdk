package com.r3.corda.sdk.token.workflow.flows.move

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.sdk.token.contracts.commands.MoveTokenCommand
import com.r3.corda.sdk.token.contracts.states.AbstractToken
import com.r3.corda.sdk.token.contracts.types.IssuedTokenType
import com.r3.corda.sdk.token.contracts.types.TokenType
import com.r3.corda.sdk.token.workflow.selection.TokenSelection
import com.r3.corda.sdk.token.workflow.selection.generateMoveNonFungible
import com.r3.corda.sdk.token.workflow.utilities.getPreferredNotary
import com.r3.corda.sdk.token.workflow.utilities.ownedTokenAmountCriteria
import net.corda.core.contracts.Amount
import net.corda.core.contracts.StateAndRef
import net.corda.core.identity.AbstractParty
import net.corda.core.node.ServiceHub
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.transactions.TransactionBuilder

// TODO clean up serviceHub passing - either as method on flowLogic, or serviceHub
@Suspendable
fun addMoveTokens(inputs: List<StateAndRef<AbstractToken<*>>>, outputs: List<AbstractToken<*>>, transactionBuilder: TransactionBuilder): TransactionBuilder {
    val outputGroups: Map<IssuedTokenType<TokenType>, List<AbstractToken<*>>> = outputs.groupBy { it.issuedTokenType }
    val inputGroups: Map<IssuedTokenType<TokenType>, List<StateAndRef<AbstractToken<*>>>> = inputs.groupBy { it.state.data.issuedTokenType }
    check(outputGroups.keys == inputGroups.keys) {
        "Inputs' and outputs' token types must correspond to each other when moveing tokens"
    }

    transactionBuilder.apply {
        outputGroups.forEach { issuedTokenType: IssuedTokenType<TokenType>, outputStates: List<AbstractToken<*>> ->
            val inputGroup = inputGroups[issuedTokenType]
                    ?: throw IllegalArgumentException("TODO this won't happen because we checked it") //TODO
            val keys = inputGroup.map { it.state.data.holder.owningKey }
            addCommand(MoveTokenCommand(issuedTokenType), keys)
            inputGroup.forEach { addInputState(it) }
            outputStates.forEach { addOutputState(it) }
        }
    }
    return transactionBuilder
}

@Suspendable
fun addMoveTokens(input: StateAndRef<AbstractToken<*>>, output: AbstractToken<*>, transactionBuilder: TransactionBuilder): TransactionBuilder {
    return addMoveTokens(listOf(input), listOf(output), transactionBuilder)
}


// TODO we should have checked before that inputs and outputs holders are well known
// TODO we should have checked that inputs belong to the same node
// Functions that use vault to resolve inputs
// TODO it doesn't make sense to take outputs in this function
//fun addMoveTokens(serviceHub: ServiceHub, inputs: List<AbstractToken<*>>, outputs: List<AbstractToken<*>>, transactionBuilder: TransactionBuilder): TransactionBuilder {
//    // TODO resolve inputs from vault
//    // query by all of them
//    // TODO it may be that it will return output states as well!
//    val inputStateAndRef = resolveAbstractTokens(serviceHub, inputs)
//    return addMoveTokens(inputStateAndRef, outputs, transactionBuilder)
//}
//
//fun resolveAbstractTokens(serviceHub: ServiceHub, tokens: List<AbstractToken<*>>): List<StateAndRef<AbstractToken<*>>> {
//    val tokenGroups = tokens.groupBy { it::class }
//    // For each group sum by token type
//    // Run token selection
//    // then run fungible/non-fungible token selection
////    tokens.map { serviceHub.vaultService} //TODO
//    return emptyList()
//}
//
//fun addMoveTokens(serviceHub: ServiceHub, input: AbstractToken<*>, output: AbstractToken<*>, transactionBuilder: TransactionBuilder): TransactionBuilder {
//    check(input.holder != output.holder) {
//        "Holder of the output should be different than holder of the input token when moving tokens."
//    }
//    // This is a simple move without change.
//    check(input == output.withNewHolder(input.holder)) {
//        "Input and output must be the same token type with the same issuer."
//    }
//    return addMoveTokens(serviceHub, listOf(input), listOf(output), transactionBuilder)
//}

// Based on generateMove.
//TODO have it as extension on FlowLogic? It looks nice from Kotlin, but is quirky in Java
// TODO test
@JvmOverloads
@Suspendable
fun <T : TokenType> addMoveTokens(
        serviceHub: ServiceHub,
        amount: Amount<T>,
        holder: AbstractParty,
        transactionBuilder: TransactionBuilder = TransactionBuilder(notary = getPreferredNotary(serviceHub)),
        queryCriteria: QueryCriteria = ownedTokenAmountCriteria(amount.token),
        changeOwner: AbstractParty? = null
): TransactionBuilder {
    val tokenSelection = TokenSelection(serviceHub)
    return tokenSelection.generateMove(transactionBuilder, amount, holder, queryCriteria, changeOwner).first
}

// TODO test
@JvmOverloads
@Suspendable
fun <T : TokenType> addMoveTokens(
        serviceHub: ServiceHub,
        token: T,
        holder:
        AbstractParty,
        transactionBuilder: TransactionBuilder = TransactionBuilder(notary = getPreferredNotary(serviceHub))
): TransactionBuilder {
    return generateMoveNonFungible(serviceHub.vaultService, token, holder, transactionBuilder).first
}
// TODO What about lists of owned tokens? Amounts?