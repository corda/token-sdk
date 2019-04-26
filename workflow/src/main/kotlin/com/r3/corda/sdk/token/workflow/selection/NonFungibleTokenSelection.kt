package com.r3.corda.sdk.token.workflow.selection

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.sdk.token.contracts.commands.MoveTokenCommand
import com.r3.corda.sdk.token.contracts.commands.RedeemTokenCommand
import com.r3.corda.sdk.token.contracts.states.NonFungibleToken
import com.r3.corda.sdk.token.contracts.types.TokenType
import com.r3.corda.sdk.token.contracts.utilities.withNotary
import com.r3.corda.sdk.token.workflow.utilities.addNotaryWithCheck
import com.r3.corda.sdk.token.workflow.utilities.ownedTokensByToken
import net.corda.core.contracts.StateAndRef
import net.corda.core.identity.AbstractParty
import net.corda.core.node.services.VaultService
import net.corda.core.transactions.TransactionBuilder
import java.security.PublicKey

// TODO Have utilities for move/redeem for non-fungible tokens.
@Suspendable
fun <T: TokenType> generateMoveNonFungible(vaultService: VaultService, ownedToken: T, owningParty: AbstractParty, transactionBuilder: TransactionBuilder): Pair<TransactionBuilder, List<PublicKey>> {
    // The assumption here is that there is only one owned token of a particular type at any one time.
    // Double clarify this in the docs to ensure that it is used properly. Either way, this code likely
    // needs to be refactored out into a separate flow. For now it's just temporary to get things going.
    val ownedTokenStateAndRef = vaultService.ownedTokensByToken(ownedToken).states.single() // TODO this should have different name...
    val ownedTokenState = ownedTokenStateAndRef.state.data
    val notary = ownedTokenStateAndRef.state.notary
    addNotaryWithCheck(transactionBuilder, notary)
    val signingKey = ownedTokenState.holder.owningKey
    val output = ownedTokenState.withNewHolder(owningParty)
    val command = MoveTokenCommand(output.token)
    val utx: TransactionBuilder = TransactionBuilder(notary = notary).apply {
        addInputState(ownedTokenStateAndRef)
        addCommand(command, signingKey)
        addOutputState(state = output withNotary notary)
    }
    return Pair(utx, listOf(signingKey))
}

// All check should be performed before.
fun <T: TokenType> generateExitNonFungible(txBuilder: TransactionBuilder, moveStateAndRef: StateAndRef<NonFungibleToken<T>>) {
    val nonFungibleToken = moveStateAndRef.state.data // TODO What if redeeming many non-fungible assets.
    val issuerKey = nonFungibleToken.token.issuer.owningKey
    val moveKey = nonFungibleToken.holder.owningKey
    val redeemCommand = RedeemTokenCommand(nonFungibleToken.token)
    txBuilder.apply {
        addInputState(moveStateAndRef)
        addCommand(redeemCommand, issuerKey, moveKey)
    }
}