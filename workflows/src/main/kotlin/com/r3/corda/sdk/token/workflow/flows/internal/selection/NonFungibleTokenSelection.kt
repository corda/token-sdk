package com.r3.corda.sdk.token.workflow.flows.internal.selection

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.sdk.token.contracts.commands.MoveTokenCommand
import com.r3.corda.sdk.token.contracts.commands.RedeemTokenCommand
import com.r3.corda.sdk.token.contracts.states.NonFungibleToken
import com.r3.corda.sdk.token.contracts.types.TokenType
import com.r3.corda.sdk.token.contracts.utilities.withNotary
import com.r3.corda.sdk.token.workflow.types.PartyAndToken
import com.r3.corda.sdk.token.workflow.utilities.addNotaryWithCheck
import com.r3.corda.sdk.token.workflow.utilities.ownedTokenCriteria
import net.corda.core.contracts.StateAndRef
import net.corda.core.node.services.VaultService
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.transactions.TransactionBuilder

@Suspendable
fun <T : TokenType> generateMoveNonFungible(
        partyAndToken: PartyAndToken<T>,
        vaultService: VaultService,
        queryCriteria: QueryCriteria?
): Pair<StateAndRef<NonFungibleToken<T>>, NonFungibleToken<T>> {
    val query = queryCriteria ?: ownedTokenCriteria(partyAndToken.token)
    val criteria = ownedTokenCriteria(partyAndToken.token).and(query)
    val nonFungibleTokens = vaultService.queryBy<NonFungibleToken<T>>(criteria).states
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
fun <T : TokenType> generateMoveNonFungible(
        transactionBuilder: TransactionBuilder,
        partyAndToken: PartyAndToken<T>,
        vaultService: VaultService,
        queryCriteria: QueryCriteria?
): TransactionBuilder {
    val (input, output) = generateMoveNonFungible(partyAndToken, vaultService, queryCriteria)
    val notary = input.state.notary
    addNotaryWithCheck(transactionBuilder, notary)
    val signingKey = input.state.data.holder.owningKey
    val command = MoveTokenCommand(output.token)
    return transactionBuilder.apply {
        addInputState(input)
        addCommand(command, signingKey)
        addOutputState(state = output withNotary notary)
    }
}

// All check should be performed before.
@Suspendable
fun <T : TokenType> generateExitNonFungible(txBuilder: TransactionBuilder, moveStateAndRef: StateAndRef<NonFungibleToken<T>>) {
    val nonFungibleToken = moveStateAndRef.state.data // TODO What if redeeming many non-fungible assets.
    val issuerKey = nonFungibleToken.token.issuer.owningKey
    val moveKey = nonFungibleToken.holder.owningKey
    val redeemCommand = RedeemTokenCommand(nonFungibleToken.token)
    txBuilder.apply {
        addInputState(moveStateAndRef)
        addCommand(redeemCommand, issuerKey, moveKey)
    }
}