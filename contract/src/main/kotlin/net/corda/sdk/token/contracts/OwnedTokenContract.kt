package net.corda.sdk.token.contracts

import net.corda.core.contracts.CommandWithParties
import net.corda.core.contracts.Contract
import net.corda.core.contracts.select
import net.corda.core.transactions.LedgerTransaction
import net.corda.sdk.token.contracts.commands.Issue
import net.corda.sdk.token.contracts.commands.Move
import net.corda.sdk.token.contracts.commands.OwnedTokenCommand
import net.corda.sdk.token.contracts.commands.Redeem
import net.corda.sdk.token.contracts.states.OwnedToken
import net.corda.sdk.token.contracts.types.EmbeddableToken
import net.corda.sdk.token.contracts.types.Issued

class OwnedTokenContract : Contract {

    companion object {
        val contractId = this::class.java.enclosingClass.canonicalName
    }

    // TODO: Much of this code is duplicated with the OwnedTokenAmount contract and can be moved to an abstract class.
    // Alternatively we can merge the code, delineate by type and use the same contract for both state types.
    override fun verify(tx: LedgerTransaction) {
        // Group owned tokens by token type. We need to do this because tokens of different types need to be
        // verified separately.
        val groups: List<LedgerTransaction.InOutGroup<OwnedToken<EmbeddableToken>, Issued<EmbeddableToken>>> = tx.groupStates { state ->
            state.token
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
                    is Redeem<*> -> handleIssue(group, matchedCommands.single())
                }
            }
        }
    }

    private fun handleIssue(group: LedgerTransaction.InOutGroup<OwnedToken<EmbeddableToken>, Issued<EmbeddableToken>>, issueCommand: CommandWithParties<OwnedTokenCommand<*>>) {
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
    private fun handleMove(group: LedgerTransaction.InOutGroup<OwnedToken<EmbeddableToken>, Issued<EmbeddableToken>>, moveCommands: List<CommandWithParties<OwnedTokenCommand<*>>>) {
        val token = group.groupingKey
        // There must be inputs and outputs present.
        require(group.inputs.isNotEmpty()) { "When moving a token, there must be one input state present." }
        require(group.outputs.isNotEmpty()) { "When moving a token, there must be one output state present." }
        // Sum the amount of input and output tokens.
        require(group.inputs.single() == group.outputs.single()) {
            "When moving a token, there must be an input and corresponding output for that token."
        }
        // There should only be one move command with one signature.
        // TODO: Split this check out so that we ensure there is only one command, THEN one signer.
        require(token.issuer.owningKey == moveCommands.single().signers.single()) {
            "The issuer must be the only signing party when a token is issued."
        }
    }

    private fun handleRedeem(group: LedgerTransaction.InOutGroup<OwnedToken<EmbeddableToken>, Issued<EmbeddableToken>>, redeemCommand: CommandWithParties<OwnedTokenCommand<*>>) {
        // There must be inputs and outputs present.
        require(group.outputs.isEmpty()) { "When redeeming an owned token, there must be no output." }
        require(group.inputs.size == 1) { "When redeeming an owned token, there must be only one input." }
        val ownedToken = group.inputs.single()
        // Only the issuer should be signing the redeem command.
        // There will only ever be one issuer as the issuer forms part of the grouping key.
        val issuerKey = ownedToken.token.issuer.owningKey
        val signer = redeemCommand.signers.single()
        require(issuerKey == signer) {
            "The issuer must be the only signing party when an amount of tokens are redeemed."
        }
    }

}