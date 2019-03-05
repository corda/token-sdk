package com.r3.corda.sdk.token.contracts

import com.r3.corda.sdk.token.contracts.commands.TokenCommand
import com.r3.corda.sdk.token.contracts.states.AbstractToken
import com.r3.corda.sdk.token.contracts.states.FungibleToken
import com.r3.corda.sdk.token.contracts.types.IssuedTokenType
import com.r3.corda.sdk.token.contracts.types.TokenType
import com.r3.corda.sdk.token.contracts.utilities.sumTokenStatesOrZero
import net.corda.core.contracts.Amount
import net.corda.core.contracts.CommandWithParties
import net.corda.core.transactions.LedgerTransaction
import net.corda.core.transactions.LedgerTransaction.InOutGroup
import java.security.PublicKey

/**
 * This is the [FungibleToken] contract. It is likely to be present in MANY transactions. The [FungibleToken]
 * state is a "lowest common denominator" state in that its contract does not reference any other state types, only the
 * [FungibleToken]. However, the [FungibleToken] state can and will be referenced by many other contracts, for
 * example, the obligation contract.
 *
 * The [FungibleToken] contract sub-classes the [AbstractToken] contract which contains the "verify" method.
 * To add functionality to this contract, developers should:
 * 1. Create their own commands which implement the [TokenCommand] interface.
 * 2. override the [AbstractTokenContract.dispatchOnCommand] method to add support for the new command, remembering
 *    to call the super method to handle the existing commands.
 * 3. Add a method to handle the new command in the new sub-class contract.
 */
open class FungibleTokenContract : AbstractTokenContract<FungibleToken<TokenType>>() {

    companion object {
        val contractId = this::class.java.enclosingClass.canonicalName
    }

    override fun groupStates(
            tx: LedgerTransaction
    ): List<InOutGroup<FungibleToken<TokenType>, IssuedTokenType<TokenType>>> {
        return tx.groupStates { state -> state.amount.token }
    }

    override fun handleIssue(
            group: InOutGroup<FungibleToken<TokenType>, IssuedTokenType<TokenType>>,
            issueCommand: CommandWithParties<TokenCommand<*>>
    ) {
        val token = group.groupingKey
        require(group.inputs.isEmpty()) { "When issuing tokens, there cannot be any input states." }
        group.outputs.apply {
            require(isNotEmpty()) { "When issuing tokens, there must be output states." }
            // We don't care about the token as the grouping function ensures that all the outputs are of the same
            // token.
            require(sumTokenStatesOrZero(token) > Amount.zero(token)) {
                "When issuing tokens an amount > ZERO must be issued."
            }
            val hasZeroAmounts = any { it.amount == Amount.zero(token) }
            require(hasZeroAmounts.not()) { "You cannot issue tokens with a zero amount." }
            // There can only be one issuer per group as the issuer is part of the token which is used to group states.
            // If there are multiple issuers for the same tokens then there will be a group for each issued token. So,
            // the line below should never fail on single().
            val issuer = map { it.amount.token.issuer }.toSet().single()
            // Only the issuer should be signing the issuer command.
            require(issuer.owningKey == issueCommand.signers.single()) {
                "The issuer must be the only signing party when an amount of tokens are issued."
            }
        }
    }

    override fun handleMove(
            group: InOutGroup<FungibleToken<TokenType>, IssuedTokenType<TokenType>>,
            moveCommands: List<CommandWithParties<TokenCommand<*>>>
    ) {
        val token = group.groupingKey
        // There must be inputs and outputs present.
        require(group.inputs.isNotEmpty()) { "When moving tokens, there must be input states present." }
        require(group.outputs.isNotEmpty()) { "When moving tokens, there must be output states present." }
        // Sum the amount of input and output tokens.
        val inputAmount: Amount<IssuedTokenType<TokenType>> = group.inputs.sumTokenStatesOrZero(token)
        require(inputAmount > Amount.zero(token)) { "In move groups there must be an amount of input tokens > ZERO." }
        val outputAmount: Amount<IssuedTokenType<TokenType>> = group.outputs.sumTokenStatesOrZero(token)
        require(outputAmount > Amount.zero(token)) { "In move groups there must be an amount of output tokens > ZERO." }
        // Input and output amounts must be equal.
        require(inputAmount == outputAmount) {
            "In move groups the amount of input tokens MUST EQUAL the amount of output tokens. In other words, you " +
                    "cannot create or destroy value when moving tokens."
        }
        val hasZeroAmounts = group.outputs.any { it.amount == Amount.zero(token) }
        require(hasZeroAmounts.not()) { "You cannot create output token amounts with a ZERO amount." }
        // There can be different owners in each move group. There map be one command for each of the signers publickey
        // or all the public keys might be listed within one command.
        val inputOwningKeys: Set<PublicKey> = group.inputs.map { it.holder }.map { it.owningKey }.toSet()
        val signers: Set<PublicKey> = moveCommands.flatMap { it.signers }.toSet()
        require(inputOwningKeys == signers) {
            "There are required signers missing or some of the specified signers are not required. A transaction " +
                    "to move owned token amounts must be signed by ONLY ALL the owners of ALL the input owned " +
                    "token amounts."
        }
    }

    override fun handleRedeem(
            group: InOutGroup<FungibleToken<TokenType>, IssuedTokenType<TokenType>>,
            redeemCommand: CommandWithParties<TokenCommand<*>>
    ) {
        val token = group.groupingKey
        // There must be inputs and outputs present.
        require(group.outputs.isEmpty()) { "When redeeming tokens, there must be no output states." }
        group.inputs.apply {
            require(isNotEmpty()) { "When redeems tokens, there must be input states present." }
            // We don't care about the token as the grouping function ensures all the outputs are of the same token.
            require(sumTokenStatesOrZero(token) > Amount.zero(token)) {
                "When redeeming tokens an amount > ZERO must be redeemed."
            }
            // There can only be one issuer per group as the issuer is part of the token which is used to group states.
            // If there are multiple issuers for the same tokens then there will be a group for each issued token. So,
            // the line below should never fail on single().
            val issuerKey = group.inputs.map { it.amount.token.issuer }.toSet().single().owningKey
            val signer = redeemCommand.signers.single()
            require(issuerKey == signer) {
                "The issuer must be the only signing party when an amount of tokens are redeemed."
            }
        }
    }

}