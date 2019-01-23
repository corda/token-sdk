package net.corda.sdk.token.contracts

import net.corda.core.contracts.Amount
import net.corda.core.contracts.CommandWithParties
import net.corda.core.contracts.Contract
import net.corda.core.contracts.select
import net.corda.core.transactions.LedgerTransaction
import net.corda.core.transactions.LedgerTransaction.InOutGroup
import net.corda.sdk.token.contracts.commands.Issue
import net.corda.sdk.token.contracts.commands.Move
import net.corda.sdk.token.contracts.commands.OwnedTokenCommand
import net.corda.sdk.token.contracts.commands.Redeem
import net.corda.sdk.token.contracts.states.OwnedTokenAmount
import net.corda.sdk.token.contracts.types.EmbeddableToken
import net.corda.sdk.token.contracts.types.Issued
import net.corda.sdk.token.contracts.utilities.sumTokens
import net.corda.sdk.token.contracts.utilities.sumTokensOrNull
import java.security.PublicKey

/**
 * This is the [OwnedTokenAmount] contract. It is likely to be present in MANY transacrtions. The [OwnedTokenAmount]
 * state is a "lowest common denominator" state in that its contract does not reference any other state types, only the
 * [OwnedTokenAmount]. However, the [OwnedTokenAmount] state can and will be referenced by many other contracts, for
 * example, the obligation contract.
 *
 * This contract works by grouping tokens by type and then verifying each group individually. It must do this because
 * different tokens are not fungible. For example: 10 GBP is not equal to 10 GBP.
 *
 * This contract doesn't need to care about the specific details of tokens. It's really only concerned with ensuring
 * that tokens are issued, moved (input amount == output amount) and redeemed correctly.
 */
class OwnedTokenAmountContract : Contract {

    companion object {
        val contractId = this::class.java.enclosingClass.canonicalName
    }

    override fun verify(tx: LedgerTransaction) {
        // Group owned token amounts by token type. We need to do this because tokens of different types need to be
        // verified separately. This works for the same token type with different issuers, or different token types
        // altogether. The grouping function returns a list containing groups of input and output states grouped by
        // token type. The type is specified explicitly to aid understanding.
        val groups: List<InOutGroup<OwnedTokenAmount<EmbeddableToken>, Issued<EmbeddableToken>>> = tx.groupStates { state ->
            state.amount.token
        }
        // A list of only the commands which implement OwnedTokenCommand.
        val ownedTokenCommands = tx.commands.select<OwnedTokenCommand<Issued<EmbeddableToken>>>()
        require(ownedTokenCommands.isNotEmpty()) { "There must be at least one owned token command this transaction." }
        // As inputs and outputs are just "bags of states" and the InOutGroups do not contain commands, we must match
        // the OwnedTokenCommand to each InOutGroup. There should be at least a single command for each group. If there
        // isn't then we don't know what to do for each group. For token moves it might be the case that there is more
        // than one command. However, for issuances and redemptions we would expect to see only one command.
        groups.forEach { group ->
            // Discard commands with a token which does not match the grouping key.
            val matchedCommands = ownedTokenCommands.filter { it.value.token == group.groupingKey }
            val matchedCommandValues = matchedCommands.map { it.value }.toSet()
            when {
                // There is a command of more than one type. The intended behaviour is ambiguous, so bail out.
                matchedCommandValues.size > 1 -> throw IllegalArgumentException("There must be exactly one " +
                        "OwnedTokenCommand type per group! For example: You cannot map an Issue AND a Move command " +
                        "to one group of tokens in a transaction."
                )
                // No commands in this group.
                matchedCommandValues.isEmpty() -> throw IllegalArgumentException("There is a token group with no assigned command!")
                // This should never fail due to the above check.
                // Handle each group individually. Although it is possible, there would not usually be a move group and
                // an issue group in the same transaction. It doesn't make sense for privacy reasons. However, it is
                // common to see multiple move groups in the same transaction.
                matchedCommandValues.size == 1 -> when (matchedCommandValues.single()) {
                    // Issuances should only contain one issue command.
                    is Issue<*> -> handleIssue(group, matchedCommands.single())
                    // Moves may contain more than one move command.
                    is Move<*> -> handleMove(group, matchedCommands)
                    // Redeems must only contain one redeem command.
                    is Redeem<*> -> handleRedeem(group, matchedCommands.single())
                }
            }
        }
    }

    private fun handleIssue(group: InOutGroup<OwnedTokenAmount<EmbeddableToken>, Issued<EmbeddableToken>>, issueCommand: CommandWithParties<OwnedTokenCommand<*>>) {
        val token = group.groupingKey
        require(group.inputs.isEmpty()) { "When issuing tokens, there cannot be any input states." }
        group.outputs.apply {
            require(isNotEmpty()) { "When issuing tokens, there must be output states." }
            // We don't care about the token as the grouping function ensures that all the outputs are of the same token.
            require(sumTokens() > Amount.zero(token)) { "When issuing tokens an amount > ZERO must be issued." }
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

    private fun handleMove(group: InOutGroup<OwnedTokenAmount<EmbeddableToken>, Issued<EmbeddableToken>>, moveCommands: List<CommandWithParties<OwnedTokenCommand<*>>>) {
        val token = group.groupingKey
        // There must be inputs and outputs present.
        require(group.inputs.isNotEmpty()) { "When moving tokens, there must be input states present." }
        require(group.outputs.isNotEmpty()) { "When moving tokens, there must be output states present." }
        // Sum the amount of input and output tokens.
        val inputAmount: Amount<Issued<EmbeddableToken>> = group.inputs.sumTokensOrNull()
                ?: throw IllegalArgumentException("In move groups there must an amount of input tokens > ZERO.")
        val outputAmount: Amount<Issued<EmbeddableToken>> = group.inputs.sumTokensOrNull()
                ?: throw IllegalArgumentException("In move groups there must an amount of output tokens > ZERO.")
        // Input and output amounts must be equal.
        require(inputAmount == outputAmount) {
            "In move groups the amount of input tokens MUST EQUAL the amount of output tokens. In other words, you " +
                    "cannot create or destroy value when moving tokens."
        }
        val hasZeroAmounts = group.outputs.any { it.amount == Amount.zero(token) }
        require(hasZeroAmounts.not()) { "You cannot create output token amounts with a ZERO amount." }
        // There can be different owners in each move group. There map be one command for each of the signers publickey
        // or all the public keys might be listed within one command.
        val inputOwningKeys: Set<PublicKey> = group.inputs.map { it.owner }.map { it.owningKey }.toSet()
        val signers: Set<PublicKey> = moveCommands.flatMap { it.signers }.toSet()
        require(inputOwningKeys == signers) {
            "There are required signers missing or some of the specified signers are not required. A transaction " +
                    "to move owned token amounts must be signed by ONLY ALL the owners of ALL the input owned " +
                    "token amounts."
        }
    }

    private fun handleRedeem(group: InOutGroup<OwnedTokenAmount<EmbeddableToken>, Issued<EmbeddableToken>>, redeemCommand: CommandWithParties<OwnedTokenCommand<*>>) {
        val token = group.groupingKey
        // There must be inputs and outputs present.
        require(group.outputs.isEmpty()) { "When redeeming tokens, there must be no output states." }
        group.inputs.apply {
            require(isNotEmpty()) { "When redeems tokens, there must be input states present." }
            // We don't care about the token as the grouping function ensures that all the outputs are of the same token.
            require(sumTokens() > Amount.zero(token)) { "When redeeming tokens an amount > ZERO must be redeemed." }
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