package com.r3.corda.lib.tokens.contracts

import com.r3.corda.lib.tokens.contracts.commands.IssueTokenCommand
import com.r3.corda.lib.tokens.contracts.commands.MoveTokenCommand
import com.r3.corda.lib.tokens.contracts.commands.RedeemTokenCommand
import com.r3.corda.lib.tokens.contracts.commands.TokenCommand
import com.r3.corda.lib.tokens.contracts.states.AbstractToken
import com.r3.corda.lib.tokens.contracts.types.IssuedTokenType
import com.r3.corda.lib.tokens.contracts.types.TokenType
import net.corda.core.contracts.*
import net.corda.core.crypto.SecureHash
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

    abstract val accepts: Class<U>

    /**
     * This method can be overridden to handle additional command types. The assumption here is that only the command
     * inputs and outputs are required to verify issuances, moves and redemptions. Attachments and the timestamp
     * are not provided.
     */
    open fun dispatchOnCommand(commands: List<CommandWithParties<TokenCommand<T>>>, inputs: List<AbstractToken<*>>, outputs: List<AbstractToken<*>>, attachments: List<Attachment>) {
        val jarHash = verifyAllTokensUseSameTypeJar(inputs = inputs, outputs = outputs)
        verifyTypeJarPresentInTransaction(jarHash, attachments)
        when (commands.first().value) {

            //verify the type jar presence and correctness
            // Issuances should only contain one issue command.
            is IssueTokenCommand<*> -> {
                verifyIssue(commands.single(), inputs as List<U>, outputs as List<U>, attachments)
            }
            // Moves may contain more than one move command.
            is MoveTokenCommand<*> -> {
                verifyMove(commands, inputs as List<U>, outputs as List<U>, attachments)
            }
            // Redeems must only contain one redeem command.
            is RedeemTokenCommand<*> -> {
                verifyRedeem(commands.single(), inputs as List<U>, outputs as List<U>, attachments)
            }
        }
    }

    /**
     * Provide custom logic for handling issuance of a token. With issuances, the assumption is that only one issuer
     * will be involved in any one issuance, therefore there will only be one [IssueTokenCommand] per group.
     */
    abstract fun verifyIssue(issueCommand: CommandWithParties<TokenCommand<T>>, inputs: List<U>, outputs: List<U>, attachments: List<Attachment>)

    /**
     * Provide custom logic for handling the moving of a token. More than one move command can be supplied because
     * multiple parties may need to move the same [IssuedTokenType] in one atomic transaction. Each party adds their
     * own command with the required public keys for the tokens they are moving.
     */
    abstract fun verifyMove(moveCommands: List<CommandWithParties<TokenCommand<T>>>, inputs: List<U>, outputs: List<U>, attachments: List<Attachment>)

    /**
     * Provide custom logic for handling the redemption of a token. There is an assumption in that only one issuer will
     * be involved in a single redemption transaction, therefore there will only be one [RedeemTokenCommand] per
     * group of [IssuedTokenType]s.
     */
    abstract fun verifyRedeem(redeemCommand: CommandWithParties<TokenCommand<T>>, inputs: List<U>, outputs: List<U>, attachments: List<Attachment>)

    final override fun verify(tx: LedgerTransaction) {
        // Group token amounts by token type. We need to do this because tokens of different types need to be
        // verified separately. This works for the same token type with different issuers, or different token types
        // altogether. The grouping function returns a list containing groups of input and output states grouped by
        // token type. The type is specified explicitly to aid understanding.
        val groups = groupStates(tx) { TokenInfo(it.data.javaClass, it.data.issuedTokenType, it.contract) }
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
                it.value.token == group.groupingKey.issuedTokenType
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
                matchedCommandValues.isEmpty() -> {
                    throw IllegalArgumentException("There is a token group with no assigned command!")
                }
                // This should never fail due to the above check.
                // Handle each group individually. Although it is possible, there would not usually be a move group and
                // an issue group in the same transaction. It doesn't make sense for privacy reasons. However, it is
                // common to see multiple move groups in the same transaction.
                matchedCommandValues.size == 1 -> dispatchOnCommand(matchedCommands, group.inputs, group.outputs, tx.attachments)
            }
        }
    }

    fun verifyTypeJarPresentInTransaction(jar: SecureHash, attachments: List<Attachment>) {
        require(jar in attachments.map { it.id }) { "Expected to find type jar: $jar in transaction attachment list, but did not" }
    }

    fun verifyAllTokensUseSameTypeJar(inputs: List<AbstractToken<*>>, outputs: List<AbstractToken<*>>): SecureHash {
        val jarHashes = (inputs + outputs).map { it.tokenTypeJarHash }.toSet()
        require(jarHashes.size == 1) { "There must only be one Jar (Hash) providing TokenType: ${outputs.first().tokenType.tokenIdentifier}" }
        return jarHashes.single()
    }

    fun groupStates(tx: LedgerTransaction, selector: (TransactionState<AbstractToken<*>>) -> TokenInfo): List<InOutGroup<AbstractToken<*>, TokenInfo>> {
        val inputs: List<TransactionState<AbstractToken<*>>> = tx.inputs.map { it.state }.mapNotNull(::castIfPossible)
        val outputs: List<TransactionState<AbstractToken<*>>> = tx.outputs.mapNotNull(::castIfPossible)

        val inGroups = inputs.groupBy(selector)
        val outGroups = outputs.groupBy(selector)
        val groups = (inGroups.keys + outGroups.keys).map { uniqKey ->
            val inputGrouping = inGroups[uniqKey]?.map { it.data } ?: emptyList()
            val outputGrouping = outGroups[uniqKey]?.map { it.data } ?: emptyList()
            InOutGroup(inputGrouping, outputGrouping, uniqKey)
        }
        return groups
    }

    data class TokenInfo(val c: Class<*>, val issuedTokenType: IssuedTokenType<TokenType>, val cc: ContractClassName)

    //This function checks that the underlying State is of a specific type, whilst returning the TransactionState that wraps it
    //it also enforces the fact that each
    private fun castIfPossible(input: (TransactionState<ContractState>)): TransactionState<AbstractToken<*>>? {
        return if (AbstractToken::class.java.isInstance(input.data) && accepts.isInstance(input.data)) {
            input as TransactionState<AbstractToken<*>>
        } else {
            null
        }
    }

}