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
import com.r3.corda.sdk.token.workflow.utilities.requireKnownConfidentialIdentity
import net.corda.core.contracts.Amount
import net.corda.core.contracts.StateAndRef
import net.corda.core.flows.FlowLogic
import net.corda.core.identity.AbstractParty
import net.corda.core.node.ServiceHub
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.transactions.TransactionBuilder

@Suspendable
fun addMoveTokens(inputs: List<StateAndRef<AbstractToken<*>>>, outputs: List<AbstractToken<*>>, transactionBuilder: TransactionBuilder): TransactionBuilder {
    val outputGroups: Map<IssuedTokenType<TokenType>, List<AbstractToken<*>>> = outputs.groupBy { it.issuedTokenType }
    val inputGroups: Map<IssuedTokenType<TokenType>, List<StateAndRef<AbstractToken<*>>>> = inputs.groupBy { it.state.data.issuedTokenType }
    check(outputGroups.keys == inputGroups.keys) {
        "Inputs' and outputs' token types must correspond to each other when moving tokens"
    }

    transactionBuilder.apply {
        outputGroups.forEach { issuedTokenType: IssuedTokenType<TokenType>, outputStates: List<AbstractToken<*>> ->
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

@Suspendable
fun addMoveTokens(input: StateAndRef<AbstractToken<*>>, output: AbstractToken<*>, transactionBuilder: TransactionBuilder): TransactionBuilder {
    return addMoveTokens(listOf(input), listOf(output), transactionBuilder)
}


// TODO we should have checked before that inputs and outputs holders are well known
// TODO we should have checked that inputs belong to the same node
// Functions that use vault to resolve inputs

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

@JvmOverloads
@Suspendable
fun <T : TokenType> addMoveTokens(
        serviceHub: ServiceHub,
        token: T,
        holder: AbstractParty,
        transactionBuilder: TransactionBuilder = TransactionBuilder(notary = getPreferredNotary(serviceHub))
): TransactionBuilder {
    return generateMoveNonFungible(serviceHub.vaultService, token, holder, transactionBuilder).first
}


// TODO Extensions on FlowLogic are nice to use from Kotlin, but are weird to use from Java
@JvmOverloads
@Suspendable
fun <T : TokenType> FlowLogic<*>.addMoveTokens(
        amount: Amount<T>,
        holder: AbstractParty,
        transactionBuilder: TransactionBuilder = TransactionBuilder(notary = getPreferredNotary(serviceHub)),
        queryCriteria: QueryCriteria = ownedTokenAmountCriteria(amount.token),
        changeOwner: AbstractParty? = null
): TransactionBuilder {
    return addMoveTokens(serviceHub, amount, holder, transactionBuilder, queryCriteria, changeOwner)
}

@JvmOverloads
@Suspendable
fun <T : TokenType> FlowLogic<*>.addMoveTokens(
        token: T,
        holder: AbstractParty,
        transactionBuilder: TransactionBuilder = TransactionBuilder(notary = getPreferredNotary(serviceHub))
): TransactionBuilder {
    return addMoveTokens(serviceHub, token, holder, transactionBuilder)
}


@Suspendable
internal fun <T : TokenType> FlowLogic<*>.generateMove(partiesAndAmounts: Map<out AbstractParty, List<Amount<T>>>,
                                                       partiesAndTokens: Map<out AbstractParty, List<T>>,
                                                       transactionBuilder: TransactionBuilder = TransactionBuilder(getPreferredNotary(serviceHub))): TransactionBuilder {
    for ((holder, amounts) in partiesAndAmounts) {
        for (amount in amounts) {
            addMoveTokens(amount, holder, transactionBuilder)
        }
    }
    for ((holder, tokens) in partiesAndTokens) {
        for (token in tokens) addMoveTokens(token, holder, transactionBuilder)
    }
    (partiesAndAmounts.keys + partiesAndTokens.keys).forEach {
        // Check that we have all confidential identities.
        serviceHub.identityService.requireKnownConfidentialIdentity(it)
    }
    return transactionBuilder
}