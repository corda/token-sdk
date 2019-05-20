package com.r3.corda.sdk.token.contracts

import com.r3.corda.sdk.token.contracts.commands.TokenCommand
import com.r3.corda.sdk.token.contracts.states.NonFungibleToken
import com.r3.corda.sdk.token.contracts.types.IssuedTokenType
import com.r3.corda.sdk.token.contracts.types.TokenType
import net.corda.core.contracts.CommandWithParties
import net.corda.core.transactions.LedgerTransaction
import net.corda.core.transactions.LedgerTransaction.InOutGroup

/**
 * See kdoc for [FungibleTokenContract].
 */
class NonFungibleTokenContract<T : TokenType> : AbstractTokenContract<T, NonFungibleToken<T>>() {

    companion object {
        val contractId = this::class.java.enclosingClass.canonicalName
    }

    override fun groupStates(tx: LedgerTransaction): List<InOutGroup<NonFungibleToken<T>, IssuedTokenType<T>>> {
        return tx.groupStates { state -> state.issuedTokenType }
    }

    override fun verifyIssue(
            issueCommand: CommandWithParties<TokenCommand<T>>,
            inputs: List<NonFungibleToken<T>>,
            outputs: List<NonFungibleToken<T>>
    ) {
        require(inputs.isEmpty()) { "When issuing tokens, there cannot be any input states." }
        require(outputs.isNotEmpty()) { "When issuing tokens, there must be output states." }
        val output = outputs.single()
        // There can only be one issuer per group as the issuer is part of the token which is used to group states.
        // If there are multiple issuers for the same tokens then there will be a group for each issued token. So,
        // the line below should never fail on single().
        require(output.issuer.owningKey == issueCommand.signers.single()) {
            "The issuer must be the only signing party when a token is issued."
        }
    }

    // NOTE:
    // We cannot have two of the same token; two token states containing the same info because they are linear states.
    // Even if we have two tokens containing the same info, they will have different linear IDs so end up in different
    // groups.
    override fun verifyMove(
            moveCommands: List<CommandWithParties<TokenCommand<T>>>,
            inputs: List<NonFungibleToken<T>>,
            outputs: List<NonFungibleToken<T>>
    ) {
        // There must be inputs and outputs present.
        require(inputs.isNotEmpty()) { "When moving a token, there must be one input state present." }
        require(outputs.isNotEmpty()) { "When moving a token, there must be one output state present." }
        // Sum the amount of input and output tokens.
        val input = inputs.single()
        val output = outputs.single()
        require(input.token == output.token) {
            "When moving a token, there must be an input and corresponding output for that token."
        }
        // There should only be one move command with one signature.
        require(moveCommands.size == 1) { "There should be only one move command." }
        val moveCommand: CommandWithParties<TokenCommand<T>> = moveCommands.single()
        require(input.holder.owningKey == moveCommand.signers.single()) {
            "The current holder must be the only signing party when a non-fungible (discrete) token is moved."
        }
    }

    override fun verifyRedeem(
            redeemCommand: CommandWithParties<TokenCommand<T>>,
            inputs: List<NonFungibleToken<T>>,
            outputs: List<NonFungibleToken<T>>
    ) {
        // There must be inputs and outputs present.
        require(outputs.isEmpty()) { "When redeeming an owned token, there must be no output." }
        require(inputs.size == 1) { "When redeeming an owned token, there must be only one input." }
        val ownedToken = inputs.single()
        // Only the issuer and holders should be signing the redeem command.
        // There will only ever be one issuer as the issuer forms part of the grouping key.
        val issuerKey = ownedToken.token.issuer.owningKey
        val holdersKeys = inputs.map { it.holder.owningKey }
        val signers = redeemCommand.signers
        require(issuerKey in signers) {
            "The issuer must be the signing party when an amount of tokens are redeemed."
        }
        require(signers.containsAll(holdersKeys)) {
            "Holders of redeemed states must be the signing parties."
        }
    }

}