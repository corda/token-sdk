package com.r3.corda.sdk.token.contracts

import com.r3.corda.sdk.token.contracts.commands.IssueTokenCommand
import com.r3.corda.sdk.token.contracts.commands.MoveTokenCommand
import com.r3.corda.sdk.token.contracts.commands.RedeemTokenCommand
import com.r3.corda.sdk.token.contracts.commands.TokenCommand
import com.r3.corda.sdk.token.contracts.states.AbstractToken
import com.r3.corda.sdk.token.contracts.types.IssuedTokenType
import com.r3.corda.sdk.token.contracts.types.TokenType
import net.corda.core.contracts.CommandWithParties
import net.corda.core.contracts.Contract
import net.corda.core.contracts.select
import net.corda.core.transactions.LedgerTransaction
import net.corda.core.transactions.LedgerTransaction.InOutGroup

/**
 * This is an abstract contract which contains common functionality used by [FungibleTokenContract] and
 * [NonFungibleTokenContract]. It works by grouping tokens by type and then verifying each group individually. It must
 * do this because different [IssuedTokenType]s are not fungible. For example: 10 GBP issued by ALICE is not equal to 10
 * GBP issued by BOB. Likewise, 10 USD is not equal to 10 GBP. This contract doesn't need to care about the specific
 * details of tokens. It's really only concerned with ensuring that tokens are issued, moved (input amount == output
 * amount) and redeemed correctly. [FungibleTokenContract] and [NonFungibleTokenContract] specify their own
 * implementations for issue, move and redeem.
 */
abstract class AbstractTokenContract<T : TokenType, U : AbstractToken<T>> : Contract {

    /** This method can be overridden to handle additional command types. */
    open fun dispatchOnCommand(matchedCommands: List<CommandWithParties<TokenCommand<T>>>, group: InOutGroup<U, IssuedTokenType<T>>) {
        when (matchedCommands.single().value) {
            // Issuances should only contain one issue command.
            is IssueTokenCommand<*> -> handleIssue(group, matchedCommands.single())
            // Moves may contain more than one move command.
            is MoveTokenCommand<*> -> handleMove(group, matchedCommands)
            // Redeems must only contain one redeem command.
            is RedeemTokenCommand<*> -> handleRedeem(group, matchedCommands.single())
        }
    }

    /** Provide custom logic for handling issuance of a token. */
    abstract fun handleIssue(group: InOutGroup<U, IssuedTokenType<T>>, issueCommand: CommandWithParties<TokenCommand<T>>)

    /** Provide custom logic for handling the moving of a token. */
    abstract fun handleMove(group: InOutGroup<U, IssuedTokenType<T>>, moveCommands: List<CommandWithParties<TokenCommand<T>>>)

    /** Provide custom logic for handling the redemption of a token. */
    abstract fun handleRedeem(group: InOutGroup<U, IssuedTokenType<T>>, redeemCommand: CommandWithParties<TokenCommand<T>>)

    /** Necessary for providing a grouping function, which is usually the [IssuedTokenType]. */
    abstract fun groupStates(tx: LedgerTransaction): List<InOutGroup<U, IssuedTokenType<T>>>

    final override fun verify(tx: LedgerTransaction) {
        // Group token amounts by token type. We need to do this because tokens of different types need to be
        // verified separately. This works for the same token type with different issuers, or different token types
        // altogether. The grouping function returns a list containing groups of input and output states grouped by
        // token type. The type is specified explicitly to aid understanding.
        val groups: List<InOutGroup<U, IssuedTokenType<T>>> = groupStates(tx)
        // A list of only the commands which implement TokenCommand.
        val tokenCommands = tx.commands.select<TokenCommand<T>>()
        require(tokenCommands.isNotEmpty()) { "There must be at least one token command in this transaction." }
        // As inputs and outputs are just "bags of states" and the InOutGroups do not contain commands, we must match
        // the TokenCommand to each InOutGroup. There should be at least a single command for each group. If there
        // isn't then we don't know what to do for each group. For token moves it might be the case that there is more
        // than one command. However, for issuances and redemptions we would expect to see only one command.
        groups.forEach { group ->
            // Discard commands with a token which does not match the grouping key.
            val matchedCommands: List<CommandWithParties<TokenCommand<T>>> = tokenCommands.filter {
                it.value.token == group.groupingKey
            }
            val matchedCommandValues: Set<TokenCommand<T>> = matchedCommands.map { it.value }.toSet()
            // Dispatch.
            when {
                // There is a command of more than one type. The intended behaviour is ambiguous, so bail out.
                matchedCommandValues.size > 1 -> throw IllegalArgumentException("There must be exactly one " +
                        "TokenCommand type per group! For example: You cannot map an Issue AND a Move command " +
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