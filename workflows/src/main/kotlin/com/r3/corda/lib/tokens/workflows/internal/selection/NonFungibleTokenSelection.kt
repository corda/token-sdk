package com.r3.corda.lib.tokens.workflows.internal.selection

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.lib.tokens.contracts.commands.MoveTokenCommand
import com.r3.corda.lib.tokens.contracts.commands.RedeemTokenCommand
import com.r3.corda.lib.tokens.contracts.states.NonFungibleToken
import com.r3.corda.lib.tokens.contracts.utilities.withNotary
import com.r3.corda.lib.tokens.workflows.types.PartyAndToken
import com.r3.corda.lib.tokens.workflows.utilities.addNotaryWithCheck
import com.r3.corda.lib.tokens.workflows.utilities.addTokenTypeJar
import com.r3.corda.lib.tokens.workflows.utilities.heldTokenCriteria
import net.corda.core.contracts.StateAndRef
import net.corda.core.node.services.VaultService
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.transactions.TransactionBuilder

@Suspendable
fun generateMoveNonFungible(
        partyAndToken: PartyAndToken,
        vaultService: VaultService,
        queryCriteria: QueryCriteria?
): Pair<StateAndRef<NonFungibleToken>, NonFungibleToken> {
    val query = queryCriteria ?: heldTokenCriteria(partyAndToken.token)
    val criteria = heldTokenCriteria(partyAndToken.token).and(query)
    val nonFungibleTokens = vaultService.queryBy<NonFungibleToken>(criteria).states
    // There can be multiple non-fungible tokens of the same TokenType. E.g. There can be multiple House tokens, each
    // with a different address. Whilst they have the same TokenType, they are still non-fungible. Therefore care must
    // be taken to ensure that only one token is returned for each query. As non-fungible tokens are also LinearStates,
    // the linearID can be used to ensure you only get one result.
    require(nonFungibleTokens.size == 1) { "Your query wasn't specific enough and returned multiple non-fungible tokens." }
    val input = nonFungibleTokens.single()
    val nonFungibleState = input.state.data
    val output = nonFungibleState.withNewHolder(partyAndToken.party)
    return Pair(input, output)
}

@Suspendable
fun generateMoveNonFungible(
        transactionBuilder: TransactionBuilder,
        partyAndToken: PartyAndToken,
        vaultService: VaultService,
        queryCriteria: QueryCriteria?
): TransactionBuilder {
    val (input, output) = generateMoveNonFungible(partyAndToken, vaultService, queryCriteria)
    val notary = input.state.notary
    addTokenTypeJar(listOf(input.state.data, output), transactionBuilder)
    addNotaryWithCheck(transactionBuilder, notary)
    val signingKey = input.state.data.holder.owningKey

    return transactionBuilder.apply {
        val currentInputSize = inputStates().size
        val currentOutputSize = outputStates().size
        addInputState(input)
        addOutputState(state = output withNotary notary)
        addCommand(MoveTokenCommand(output.token, inputIndexes = listOf(currentInputSize), outputIndexes = listOf(currentOutputSize)), signingKey)
    }
}


// All check should be performed before.
@Suspendable
fun generateExitNonFungible(txBuilder: TransactionBuilder, moveStateAndRef: StateAndRef<NonFungibleToken>) {
    val nonFungibleToken = moveStateAndRef.state.data // TODO What if redeeming many non-fungible assets.
    addTokenTypeJar(nonFungibleToken, txBuilder)
    val issuerKey = nonFungibleToken.token.issuer.owningKey
    val moveKey = nonFungibleToken.holder.owningKey
    txBuilder.apply {
        val currentInputSize = inputStates().size
        addInputState(moveStateAndRef)
        addCommand(RedeemTokenCommand(nonFungibleToken.token, listOf(currentInputSize)), issuerKey, moveKey)
    }
}