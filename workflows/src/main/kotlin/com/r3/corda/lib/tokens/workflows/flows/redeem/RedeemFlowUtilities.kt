package com.r3.corda.lib.tokens.workflows.flows.redeem

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.lib.tokens.contracts.commands.RedeemTokenCommand
import com.r3.corda.lib.tokens.contracts.states.AbstractToken
import com.r3.corda.lib.tokens.contracts.states.FungibleToken
import com.r3.corda.lib.tokens.contracts.types.TokenType
import com.r3.corda.lib.tokens.contracts.utilities.sumTokenStateAndRefs
import com.r3.corda.lib.tokens.workflows.internal.checkSameIssuer
import com.r3.corda.lib.tokens.workflows.internal.checkSameNotary
import com.r3.corda.lib.tokens.workflows.internal.selection.TokenSelection
import com.r3.corda.lib.tokens.workflows.internal.selection.generateExitNonFungible
import com.r3.corda.lib.tokens.workflows.utilities.addNotaryWithCheck
import com.r3.corda.lib.tokens.workflows.utilities.addTokenTypeJar
import com.r3.corda.lib.tokens.workflows.utilities.heldTokensByTokenIssuer
import com.r3.corda.lib.tokens.workflows.utilities.tokenAmountWithIssuerCriteria
import net.corda.core.contracts.Amount
import net.corda.core.contracts.StateAndRef
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.node.ServiceHub
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.transactions.TransactionBuilder

/**
 * Add redeeming of multiple [inputs] to the [transactionBuilder] with possible [changeOutput].
 */
@Suspendable
@JvmOverloads
fun addTokensToRedeem(
        transactionBuilder: TransactionBuilder,
        inputs: List<StateAndRef<AbstractToken>>,
        changeOutput: AbstractToken? = null
): TransactionBuilder {
    checkSameIssuer(inputs, changeOutput?.issuer)
    checkSameNotary(inputs)
    if (changeOutput != null && changeOutput is FungibleToken) {
        check(inputs.filterIsInstance<StateAndRef<FungibleToken>>().sumTokenStateAndRefs() > changeOutput.amount) {
            "Change output should be less than sum of inputs."
        }
    }
    val firstState = inputs.first().state
    addNotaryWithCheck(transactionBuilder, firstState.notary)
    val moveKey = firstState.data.holder.owningKey
    val issuerKey = firstState.data.issuer.owningKey

    var inputIdx = transactionBuilder.inputStates().size
    val outputIdx = transactionBuilder.outputStates().size
    transactionBuilder.apply {
        val inputIndicies = inputs.map {
            addInputState(it)
            inputIdx++
        }
        val outputs = if (changeOutput != null) {
            addOutputState(changeOutput)
            listOf(outputIdx)
        } else {
            emptyList()
        }
        transactionBuilder.addCommand(RedeemTokenCommand(firstState.data.issuedTokenType, inputIndicies, outputs), issuerKey, moveKey)
    }
    val states = inputs.map { it.state.data } + if (changeOutput == null) emptyList() else listOf(changeOutput)
    addTokenTypeJar(states, transactionBuilder)
    return transactionBuilder
}


/**
 * Redeem non-fungible [heldToken] issued by the [issuer] and add it to the [transactionBuilder].
 */
@Suspendable
fun addNonFungibleTokensToRedeem(
        transactionBuilder: TransactionBuilder,
        serviceHub: ServiceHub,
        heldToken: TokenType,
        issuer: Party
): TransactionBuilder {
    val heldTokenStateAndRef = serviceHub.vaultService.heldTokensByTokenIssuer(heldToken, issuer).states
    check(heldTokenStateAndRef.size == 1) {
        "Exactly one held token of a particular type $heldToken should be in the vault at any one time."
    }
    val nonFungibleState = heldTokenStateAndRef.first()
    addNotaryWithCheck(transactionBuilder, nonFungibleState.state.notary)
    generateExitNonFungible(transactionBuilder, nonFungibleState)
    return transactionBuilder
}

/**
 * Redeem amount of certain type of the token issued by [issuer]. Pay possible change to the [changeOwner] - it can be confidential identity.
 * Additional query criteria can be provided using [additionalQueryCriteria].
 */
@Suspendable
@JvmOverloads
fun addFungibleTokensToRedeem(
        transactionBuilder: TransactionBuilder,
        serviceHub: ServiceHub,
        amount: Amount<TokenType>,
        issuer: Party,
        changeOwner: AbstractParty,
        additionalQueryCriteria: QueryCriteria? = null
): TransactionBuilder {
    val tokenSelection = TokenSelection(serviceHub)
    val baseCriteria = tokenAmountWithIssuerCriteria(amount.token, issuer)
    val queryCriteria = additionalQueryCriteria?.let { baseCriteria.and(it) } ?: baseCriteria
    val fungibleStates = tokenSelection.attemptSpend(amount, transactionBuilder.lockId, queryCriteria) // TODO We shouldn't expose lockId in this function
    checkSameNotary(fungibleStates)
    check(fungibleStates.isNotEmpty()) {
        "Received empty list of states to redeem."
    }
    val notary = fungibleStates.first().state.notary
    addNotaryWithCheck(transactionBuilder, notary)
    // TODO unify it, probably need to move generate exit to redeem utils
    val (exitStates, change) = tokenSelection.generateExit(
            exitStates = fungibleStates,
            amount = amount,
            changeOwner = changeOwner
    )
    addTokensToRedeem(transactionBuilder, exitStates, change)
    return transactionBuilder
}
