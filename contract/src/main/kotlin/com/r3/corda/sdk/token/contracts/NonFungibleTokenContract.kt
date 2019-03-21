package com.r3.corda.sdk.token.contracts

import com.r3.corda.sdk.token.contracts.commands.TokenCommand
import com.r3.corda.sdk.token.contracts.states.NonFungibleToken
import com.r3.corda.sdk.token.contracts.types.IssuedTokenType
import com.r3.corda.sdk.token.contracts.types.TokenType
import net.corda.core.contracts.CommandWithParties
import net.corda.core.internal.WaitForStateConsumption.Companion.logger
import net.corda.core.transactions.LedgerTransaction

/**
 * See kdoc for [FungibleTokenContract].
 */
class NonFungibleTokenContract : AbstractTokenContract<NonFungibleToken<TokenType>>() {

    companion object {
        val contractId = this::class.java.enclosingClass.canonicalName
    }

    override fun groupStates(
            tx: LedgerTransaction
    ): List<LedgerTransaction.InOutGroup<NonFungibleToken<TokenType>, IssuedTokenType<TokenType>>> {
        return tx.groupStates { state -> state.token }
    }

    override fun handleIssue(
            group: LedgerTransaction.InOutGroup<NonFungibleToken<TokenType>, IssuedTokenType<TokenType>>,
            issueCommand: CommandWithParties<TokenCommand<*>>
    ) {
        val token = group.groupingKey
        require(group.inputs.isEmpty()) { "When issuing tokens, there cannot be any input states." }
        require(group.outputs.isNotEmpty()) { "When issuing tokens, there must be output states." }
        // There can only be one issuer per group as the issuer is part of the token which is used to group states.
        // If there are multiple issuers for the same tokens then there will be a group for each issued token. So,
        // the line below should never fail on single().
        // Only the issuer should be signing the issuer command.
        require(token.issuer.owningKey == issueCommand.signers.single()) {
            "The issuer must be the only signing party when a token is issued."
        }
    }

    // NOTE:
    // We cannot have two of the same token; two token states containing the same info because they are linear states.
    // Even if we have two tokens containing the same info, they will have different linear IDs so end up in different
    // groups.
    override fun handleMove(
            group: LedgerTransaction.InOutGroup<NonFungibleToken<TokenType>, IssuedTokenType<TokenType>>,
            moveCommands: List<CommandWithParties<TokenCommand<*>>>
    ) {
        val token = group.groupingKey
        // There must be inputs and outputs present.
        require(group.inputs.isNotEmpty()) { "When moving a token, there must be one input state present." }
        require(group.outputs.isNotEmpty()) { "When moving a token, there must be one output state present." }
        // Sum the amount of input and output tokens.
        require(group.inputs.single().token == group.outputs.single().token) {
            "When moving a token, there must be an input and corresponding output for that token."
        }
        // There should only be one move command with one signature.
        // TODO: Split this check out so that we ensure there is only one command, THEN one signer.
        require(group.inputs.single().holder.owningKey == moveCommands.single().signers.single()) {
            "The current holder must be the only signing party when a nonfungible (discrete) token is moved."
        }
    }

    override fun handleRedeem(
            group: LedgerTransaction.InOutGroup<NonFungibleToken<TokenType>, IssuedTokenType<TokenType>>,
            redeemCommand: CommandWithParties<TokenCommand<*>>
    ) {
        // There must be inputs and outputs present.
        require(group.outputs.isEmpty()) { "When redeeming an owned token, there must be no output." }
        require(group.inputs.size == 1) { "When redeeming an owned token, there must be only one input." }
        val ownedToken = group.inputs.single()
        // Only the issuer and owners should be signing the redeem command.
        // There will only ever be one issuer as the issuer forms part of the grouping key.
        val issuerKey = ownedToken.token.issuer.owningKey
        val ownersKeys = group.inputs.map { it.holder.owningKey }
        val signers = redeemCommand.signers
        require(issuerKey in signers) {
            "The issuer must be the signing party when an amount of tokens are redeemed."
        }
        require(signers.containsAll(ownersKeys)) {
            "Owners of redeemed states must be the signing parties."
        }
    }

}