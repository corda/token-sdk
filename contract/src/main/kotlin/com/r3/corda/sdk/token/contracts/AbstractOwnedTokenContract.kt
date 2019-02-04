package com.r3.corda.sdk.token.contracts

import com.r3.corda.sdk.token.contracts.commands.Issue
import com.r3.corda.sdk.token.contracts.commands.Move
import com.r3.corda.sdk.token.contracts.commands.OwnedTokenCommand
import com.r3.corda.sdk.token.contracts.commands.Redeem
import com.r3.corda.sdk.token.contracts.states.AbstractOwnedToken
import com.r3.corda.sdk.token.contracts.types.EmbeddableToken
import com.r3.corda.sdk.token.contracts.types.Issued
import net.corda.core.contracts.CommandWithParties
import net.corda.core.contracts.Contract
import net.corda.core.contracts.select
import net.corda.core.transactions.LedgerTransaction

/**
 * This is an abstract contract which contains common functionality used by [OwnedTokenAmountContract] and
 * [OwnedTokenContract]. It works by grouping tokens by type and then verifying each group individually. It must do this
 * because different tokens are not fungible. For example: 10 GBP issued by ALICE is not equal to 10 GBP issued by BOB.
 * Likewise, 10 USD is not equal to 10 GBP.
 *
 * This contract doesn't need to care about the specific details of tokens. It's really only concerned with ensuring
 * that tokens are issued, moved (input amount == output amount) and redeemed correctly. [OwnedTokenAmountContract] and
 * [OwnedTokenContract] specify their own implementations for issue, move and redeem.
 */
abstract class AbstractOwnedTokenContract<T : AbstractOwnedToken> : Contract {

    /** This method can be overridden to handle additional command types. */
    open fun dispatchOnCommand(
            matchedCommands: List<CommandWithParties<OwnedTokenCommand<Issued<EmbeddableToken>>>>,
            group: LedgerTransaction.InOutGroup<T, Issued<EmbeddableToken>>
    ) {
        when (matchedCommands.single().value) {
            // Issuances should only contain one issue command.
            is Issue<*> -> handleIssue(group, matchedCommands.single())
            // Moves may contain more than one move command.
            is Move<*> -> handleMove(group, matchedCommands)
            // Redeems must only contain one redeem command.
            is Redeem<*> -> handleRedeem(group, matchedCommands.single())
        }
    }

    abstract fun handleIssue(
            group: LedgerTransaction.InOutGroup<T, Issued<EmbeddableToken>>,
            issueCommand: CommandWithParties<OwnedTokenCommand<*>>
    )

    abstract fun handleMove(
            group: LedgerTransaction.InOutGroup<T, Issued<EmbeddableToken>>,
            moveCommands: List<CommandWithParties<OwnedTokenCommand<*>>>
    )

    abstract fun handleRedeem(
            group: LedgerTransaction.InOutGroup<T, Issued<EmbeddableToken>>,
            redeemCommand: CommandWithParties<OwnedTokenCommand<*>>
    )

    abstract fun groupStates(tx: LedgerTransaction): List<LedgerTransaction.InOutGroup<T, Issued<EmbeddableToken>>>

    final override fun verify(tx: LedgerTransaction) {
        // Group owned token amounts by token type. We need to do this because tokens of different types need to be
        // verified separately. This works for the same token type with different issuers, or different token types
        // altogether. The grouping function returns a list containing groups of input and output states grouped by
        // token type. The type is specified explicitly to aid understanding.
        val groups: List<LedgerTransaction.InOutGroup<T, Issued<EmbeddableToken>>> = groupStates(tx)
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
            val matchedCommandValues: Set<OwnedTokenCommand<Issued<EmbeddableToken>>> = matchedCommands.map {
                it.value
            }.toSet()

            // Dispatch.
            when {
                // There is a command of more than one type. The intended behaviour is ambiguous, so bail out.
                matchedCommandValues.size > 1 -> throw IllegalArgumentException("There must be exactly one " +
                        "OwnedTokenCommand type per group! For example: You cannot map an Issue AND a Move command " +
                        "to one group of tokens in a transaction."
                )
                // No commands in this group.
                matchedCommandValues.isEmpty() ->
                    throw IllegalArgumentException("There is a token group with no assigned command!")
                // This should never fail due to the above check.
                // Handle each group individually. Although it is possible, there would not usually be a move group and
                // an issue group in the same transaction. It doesn't make sense for privacy reasons. However, it is
                // common to see multiple move groups in the same transaction.
                matchedCommandValues.size == 1 -> dispatchOnCommand(matchedCommands, group)
            }
        }
    }

}