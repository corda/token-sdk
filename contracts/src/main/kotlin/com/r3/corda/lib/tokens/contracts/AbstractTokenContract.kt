package com.r3.corda.lib.tokens.contracts

import com.r3.corda.lib.tokens.contracts.commands.IssueTokenCommand
import com.r3.corda.lib.tokens.contracts.commands.MoveTokenCommand
import com.r3.corda.lib.tokens.contracts.commands.RedeemTokenCommand
import com.r3.corda.lib.tokens.contracts.commands.TokenCommand
import com.r3.corda.lib.tokens.contracts.states.AbstractToken
import com.r3.corda.lib.tokens.contracts.types.IssuedTokenType
import com.r3.corda.lib.tokens.contracts.types.TokenPointer
import com.r3.corda.lib.tokens.contracts.types.TokenType
import net.corda.core.contracts.*
import net.corda.core.crypto.SecureHash
import net.corda.core.internal.uncheckedCast
import net.corda.core.transactions.LedgerTransaction
/**
 * This is an abstract contract which contains common functionality used by [FungibleTokenContract] and
 * [NonFungibleTokenContract]. It works by grouping tokens by type and then verifying each group individually. It must
 * do this because different [IssuedTokenType]s are not fungible. For example: 10 GBP issued by ALICE is not equal to 10
 * GBP issued by BOB. Likewise, 10 USD is not equal to 10 GBP. This contract doesn't need to care about the specific
 * details of tokens. It's really only concerned with ensuring that tokens are issued, moved (input amount == output
 * amount) and redeemed correctly. [FungibleTokenContract] and [NonFungibleTokenContract] specify their own
 * implementations for issue, move and redeem.
 */
abstract class AbstractTokenContract<AT : AbstractToken> : Contract {

	abstract val accepts: Class<AT>

	/**
	 * This method can be overridden to handle additional command types. The assumption here is that only the command
	 * inputs and outputs are required to verify issuances, moves and redemptions. Attachments and the timestamp
	 * are not provided.
	 */
	open fun dispatchOnCommand(
		commands: List<CommandWithParties<TokenCommand>>,
		inputs: List<IndexedState<AT>>,
		outputs: List<IndexedState<AT>>,
		attachments: List<Attachment>,
		references: List<StateAndRef<ContractState>>,
		summary: List<String>
	) {
		// Get the JAR which implements the TokenType for this group.
		val jarHash: SecureHash? = verifyAllTokensUseSameTypeJar(
			inputs = inputs.map { it.state.data },
			outputs = outputs.map { it.state.data }
		)
		// This group involves a custom TokenType, so we need to check the correct JAR is attached.
		jarHash?.let { verifyTypeJarPresentInTransaction(jar = jarHash, attachments = attachments) }
		when (commands.first().value) {
			//verify the type jar presence and correctness
			// Issuances should only contain one issue command.
			is IssueTokenCommand -> verifyIssue(commands.single(), inputs, outputs, attachments, references)
			// Moves may contain more than one move command.
			is MoveTokenCommand -> verifyMove(commands, inputs, outputs, attachments, references, summary)
			// Redeems must only contain one redeem command.
			is RedeemTokenCommand -> verifyRedeem(commands.single(), inputs, outputs, attachments, references)
		}
	}

	/**
	 * Provide custom logic for handling issuance of a token. With issuances, the assumption is that only one issuer
	 * will be involved in any one issuance, therefore there will only be one [IssueTokenCommand] per group.
	 */
	abstract fun verifyIssue(
		issueCommand: CommandWithParties<TokenCommand>,
		inputs: List<IndexedState<AT>>,
		outputs: List<IndexedState<AT>>,
		attachments: List<Attachment>,
		references: List<StateAndRef<ContractState>>
	)

	/**
	 * Provide custom logic for handling the moving of a token. More than one move command can be supplied because
	 * multiple parties may need to move the same [IssuedTokenType] in one atomic transaction. Each party adds their
	 * own command with the required public keys for the tokens they are moving.
	 */
	abstract fun verifyMove(
		moveCommands: List<CommandWithParties<TokenCommand>>,
		inputs: List<IndexedState<AT>>,
		outputs: List<IndexedState<AT>>,
		attachments: List<Attachment>,
		references: List<StateAndRef<ContractState>>,
		summary: List<String>
	)

	/**
	 * Provide custom logic for handling the redemption of a token. There is an assumption in that only one issuer will
	 * be involved in a single redemption transaction, therefore there will only be one [RedeemTokenCommand] per
	 * group of [IssuedTokenType]s.
	 */
	abstract fun verifyRedeem(
		redeemCommand: CommandWithParties<TokenCommand>,
		inputs: List<IndexedState<AT>>,
		outputs: List<IndexedState<AT>>,
		attachments: List<Attachment>,
		references: List<StateAndRef<ContractState>>
	)

	final override fun verify(tx: LedgerTransaction) {
		// Group token amounts by token type. We need to do this because tokens of different types need to be
		// verified separately. This works for the same token type with different issuers, or different token types
		// altogether. The grouping function returns a list containing groups of input and output states grouped by
		// token type. The type is specified explicitly to aid understanding.
		val groups = groupStates(tx) { TokenInfo(it.state.data.javaClass, it.state.data.issuedTokenType, it.state.contract) }
		// A list of only the commands which implement TokenCommand.
		val tokenCommands = tx.commands.select<TokenCommand>()
		require(tokenCommands.isNotEmpty()) { "There must be at least one token command in this transaction." }
		// As inputs and outputs are just "bags of states" and the InOutGroups do not contain commands, we must match
		// the TokenCommand to each InOutGroup. There should be at least a single command for each group. If there
		// isn't then we don't know what to do for each group. For token moves it might be the case that there is more
		// than one command. However, for issuances and redemptions we would expect to see only one command.

		val groupsAndCommands = groups.map { group ->
			val matchedCommands: List<CommandWithParties<TokenCommand>> = tokenCommands.filter { groupMatchesCommand(it, group) }
			matchedCommands to group
		}


		groupsAndCommands.forEach { (commands, group) ->
			require(commands.isNotEmpty()) { "There is a token group with no assigned command!" }
			require(commands.size == 1) {
				"There must be exactly one " +
						"TokenCommand type per group! For example: You cannot map an Issue AND a Move command " +
						"to one group of tokens in a transaction."
			}
			dispatchOnCommand(commands, group.inputs, group.outputs, tx.attachments, tx.references, tx.summary)
		}


		val allMatchedCommands = groupsAndCommands.map { it.first.first() }.toSet()
	}

	private fun groupMatchesCommand(it: CommandWithParties<TokenCommand>, group: IndexedInOutGroup<AbstractToken, TokenInfo>): Boolean {
		return it.value.token == group.groupingKey.issuedTokenType
				&& it.value.inputIndicies() == group.inputIndicies
				&& it.value.outputIndicies() == group.outputIndicies

	}

	private fun verifyTypeJarPresentInTransaction(jar: SecureHash, attachments: List<Attachment>) {
		require(jar in attachments.map { it.id }) { "Expected to find type jar: $jar in transaction attachment list, but did not" }
	}

	private fun verifyAllTokensUseSameTypeJar(inputs: List<AbstractToken>, outputs: List<AbstractToken>): SecureHash? {
		val inputsAndOutputs = inputs + outputs
		val tokenTypes = inputsAndOutputs.map(AbstractToken::tokenType).toSet()

		require(tokenTypes.size == 1) { "There should only be one TokenType per group" }
		val jarHashes = inputsAndOutputs.map(AbstractToken::tokenTypeJarHash).toSet()
		require(jarHashes.size == 1) {
			"There must be exactly one Jar (Hash) providing extended TokenType: ${outputs.first().tokenType.tokenIdentifier} / ${outputs.first().tokenType.javaClass}"
		}

		if (jarHashes.single() == null) {
			//all token types in this group seem to be non-extended types
			require(tokenTypes.single().javaClass == TokenType::class.java || tokenTypes.single().javaClass == TokenPointer::class.java) {
				"Extended TokenType: ${tokenTypes.single().javaClass} has been used, whilst no jarHash has been provided to pin the providing jar"
			}
		}

		return jarHashes.single()
	}

	private fun groupStates(tx: LedgerTransaction, selector: (IndexedState<AT>) -> TokenInfo): List<IndexedInOutGroup<AT, TokenInfo>> {
		val inputsToVerify = (0 until tx.inputs.size).map { it to tx.inputs[it].state }.mapNotNull { castIfPossible(it) }
		val outputsToVerify = (0 until tx.outputs.size).map { it to tx.outputs[it] }.mapNotNull { castIfPossible(it) }
		val inGroups = inputsToVerify.groupBy(selector)
		val outGroups = outputsToVerify.groupBy(selector)
		val groups = (inGroups.keys + outGroups.keys).map { uniqKey ->
			val inputGrouping = inGroups[uniqKey] ?: emptyList()
			val outputGrouping = outGroups[uniqKey] ?: emptyList()
			IndexedInOutGroup(inputGrouping, outputGrouping, uniqKey)
		}
		return groups
	}

	private data class TokenInfo(val concreteClass: Class<*>, val issuedTokenType: IssuedTokenType, val contractClass: ContractClassName)

	//This function checks that the underlying State is of a specific type, whilst returning the TransactionState that wraps it
	//it also enforces the fact that each state is of a type accepted by the current concrete contract class
	private fun castIfPossible(pair: Pair<Int, TransactionState<ContractState>>): IndexedState<AT>? {
		val input = pair.second
		val index = pair.first
		return if (AbstractToken::class.java.isInstance(input.data) && accepts.isInstance(input.data)) {
			IndexedState(uncheckedCast(input), index)
		} else {
			null
		}
	}


	data class IndexedInOutGroup<out T : ContractState, out K : Any>(
		val inputs: List<IndexedState<T>>,
		val outputs: List<IndexedState<T>>,
		val groupingKey: K
	) {
		val inputIndicies = inputs.map { it.index }.sortedBy { it }
		val outputIndicies = outputs.map { it.index }.sortedBy { it }
	}

	data class IndexedState<out T : ContractState>(val state: TransactionState<T>, val index: Int)
}