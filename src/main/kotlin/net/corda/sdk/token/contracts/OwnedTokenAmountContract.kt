package net.corda.sdk.token.contracts

import net.corda.core.contracts.Amount
import net.corda.core.contracts.CommandWithParties
import net.corda.core.contracts.Contract
import net.corda.core.transactions.LedgerTransaction
import net.corda.core.transactions.LedgerTransaction.InOutGroup
import net.corda.sdk.token.commands.Issue
import net.corda.sdk.token.commands.Move
import net.corda.sdk.token.commands.OwnedTokenCommand
import net.corda.sdk.token.commands.Redeem
import net.corda.sdk.token.states.OwnedTokenAmount
import net.corda.sdk.token.types.Issued
import net.corda.sdk.token.types.token.EmbeddableToken
import net.corda.sdk.token.utilities.sumTokens

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
abstract class OwnedTokenAmountContract : Contract {

    companion object {
        val contractId = this::class.java.enclosingClass.canonicalName
    }

    // TODO: Need to change the command matching logic. Need to match based upon issuer AND token, not just token.
    override fun verify(tx: LedgerTransaction) {
        // Group owned token amounts by token type. We need to do this because tokens of different types need to be
        // verified separately. This works for the same token type with different issuers, or different token types
        // altogether. The grouping function returns a list containing groups of input and output states grouped by
        // token type. The type is specified explicitly to aid understanding.
        val groups: List<InOutGroup<OwnedTokenAmount<EmbeddableToken>, Issued<EmbeddableToken>>> = tx.groupStates { state: OwnedTokenAmount<*> ->
            state.amount.token
        }
        // A list of only the commands which implement OwnedTokenCommand.
        val ownedTokenCommands = tx.commands.filterIsInstance<CommandWithParties<OwnedTokenCommand<EmbeddableToken>>>()
        // As inputs and outputs are just "bags of states" and the InOutGroups do not contain commands, we must match
        // the OwnedTokenCommand to each InOutGroup. There should be a single command for each group. If there isn't
        // then we don't know what to do for each group. For token moves it might be the case that there is one command
        // However, for issuances and redemptions we would expect to see only one command.
        groups.forEach { group: InOutGroup<OwnedTokenAmount<EmbeddableToken>, Issued<EmbeddableToken>> ->
            // Only the commands which contain this group's grouping key.
            val matchedCommands = ownedTokenCommands.filter { it.value.token == group.groupingKey }
            val matchedCommandValues = matchedCommands.map { it.value }.toSet()
            require(matchedCommandValues.size == 1) {
                "There must only be one command of OwnedTokenCommand type per group! For example: You cannot assign " +
                        "an Issue AND Move command to one group of tokens in a transaction."
            }
            // This should never fail due to the above check.
            val matchedCommandValue: OwnedTokenCommand<EmbeddableToken> = matchedCommandValues.single()
            // Handle each group individually. Although it is possible, there would not usually be a move group and an
            // issue group in the same transaction. It doesn't make sense for privacy reasons. It is common to see
            // multiple move groups in the same transaction.
            when (matchedCommandValue) {
                // Issuances should only contain one issue command.
                is Issue<*> -> handleIssue(group, matchedCommands.single())
                // Moves may contain more than one move command.
                is Move<*> -> handleMove(group, matchedCommands)
                // Redeems must only contain one redeem command.
                is Redeem<*> -> handleIssue(group, matchedCommands.single())
            }
        }
    }

    private fun handleIssue(group: InOutGroup<OwnedTokenAmount<EmbeddableToken>, Issued<EmbeddableToken>>, issueCommand: CommandWithParties<OwnedTokenCommand<EmbeddableToken>>) {
        val token = group.groupingKey
        require(group.inputs.isEmpty()) { "When issuing tokens, there cannot be any input states." }
        require(group.outputs.isNotEmpty()) { "When issuing tokens, there must be output states." }
        // We don't care about the token as the grouping function ensures that all the outputs are of the same token.
        val totalOutputAmount = group.outputs.sumTokens()
        require(totalOutputAmount > Amount.zero(token)) { "When issuing tokens an amount > ZERO must be issued." }
        val issuers = group.outputs.map { it.amount.token.issuer }.toSet()
        require(issuers.size == 1) { "All OwnedTokenAmounts must have the same issuer if they are issued in the same transaction" }
        require(issuers.toSet() == issueCommand.signers.toSet()) { "The issuer must be the only signing party when an amount of tokens are issued." }
    }

    private fun handleMove(group: InOutGroup<OwnedTokenAmount<*>, Issued<EmbeddableToken>>, moveCommands: List<CommandWithParties<OwnedTokenCommand<EmbeddableToken>>>) {
        // TODO: Most important thing is to conserve amounts.
    }

    private fun handleRedeem(group: InOutGroup<OwnedTokenAmount<*>, Issued<EmbeddableToken>>, redeemCommand: CommandWithParties<OwnedTokenCommand<EmbeddableToken>>) {
        // TODO
    }

}