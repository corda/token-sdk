package com.r3.corda.lib.tokens.contracts

import com.r3.corda.lib.tokens.contracts.commands.TokenCommand
import com.r3.corda.lib.tokens.contracts.states.AbstractToken
import com.r3.corda.lib.tokens.contracts.states.FungibleToken
import com.r3.corda.lib.tokens.contracts.types.IssuedTokenType
import com.r3.corda.lib.tokens.contracts.utilities.sumTokenStatesOrZero
import net.corda.core.contracts.*
import net.corda.core.internal.uncheckedCast
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
 * 2. Override the [AbstractTokenContract.dispatchOnCommand] method to add support for the new command, remembering
 *    to call the super method to handle the existing commands.
 * 3. Add a method to handle the new command in the new sub-class contract.
 */
open class FungibleTokenContract : AbstractTokenContract<FungibleToken>() {
    override val accepts: Class<FungibleToken> get() = uncheckedCast(FungibleToken::class.java)

    companion object {
        val contractId = this::class.java.enclosingClass.canonicalName
    }

    override fun verifyIssue(
            issueCommand: CommandWithParties<TokenCommand>,
            inputs: List<IndexedState<FungibleToken>>,
            outputs: List<IndexedState<FungibleToken>>,
            attachments: List<Attachment>,
            references: List<StateAndRef<ContractState>>
    ) {
        val issuedToken: IssuedTokenType = issueCommand.value.token
        require(inputs.isEmpty()) { "When issuing tokens, there cannot be any input states." }
        outputs.apply {
            require(isNotEmpty()) { "When issuing tokens, there must be output states." }
            // We don't care about the token as the grouping function ensures that all the outputs are of the same
            // token.
            require(this.map { it.state.data }.sumTokenStatesOrZero(issuedToken) > Amount.zero(issuedToken)) {
                "When issuing tokens an amount > ZERO must be issued."
            }
            val hasZeroAmounts = any { it.state.data.amount == Amount.zero(issuedToken) }
            require(hasZeroAmounts.not()) { "You cannot issue tokens with a zero amount." }
            // There can only be one issuer per group as the issuer is part of the token which is used to group states.
            // If there are multiple issuers for the same tokens then there will be a group for each issued token. So,
            // the line below should never fail on single().
            val issuerKey: PublicKey = this.map { it.state.data }.map(AbstractToken::issuer).toSet().single().owningKey
            val issueSigners: List<PublicKey> = issueCommand.signers
            // The issuer should be signing the issue command. Notice that it can be signed by more parties.
            require(issuerKey in issueSigners) {
                "The issuer must be the signing party when an amount of tokens are issued."
            }
        }

    }

    override fun verifyMove(
            moveCommands: List<CommandWithParties<TokenCommand>>,
            inputs: List<IndexedState<FungibleToken>>,
            outputs: List<IndexedState<FungibleToken>>,
            attachments: List<Attachment>,
            references: List<StateAndRef<ContractState>>
    ) {
        // Commands are grouped by Token Type, so we just need a token reference.
        val issuedToken: IssuedTokenType = moveCommands.first().value.token
        // There must be inputs and outputs present.
        require(inputs.isNotEmpty()) { "When moving tokens, there must be input states present." }
        require(outputs.isNotEmpty()) { "When moving tokens, there must be output states present." }
        // Sum the amount of input and output tokens.
        val inputAmount: Amount<IssuedTokenType> = inputs.map { it.state.data }.sumTokenStatesOrZero(issuedToken)
        require(inputAmount > Amount.zero(issuedToken)) { "In move groups there must be an amount of input tokens > ZERO." }
        val outputAmount: Amount<IssuedTokenType> = outputs.map { it.state.data }.sumTokenStatesOrZero(issuedToken)
        require(outputAmount > Amount.zero(issuedToken)) { "In move groups there must be an amount of output tokens > ZERO." }
        // Input and output amounts must be equal.
        require(inputAmount == outputAmount) {
            "In move groups the amount of input tokens MUST EQUAL the amount of output tokens. In other words, you " +
                    "cannot create or destroy value when moving tokens."
        }
        val hasZeroAmounts = outputs.any { it.state.data.amount == Amount.zero(issuedToken) }
        require(hasZeroAmounts.not()) { "You cannot create output token amounts with a ZERO amount." }
        // There can be different owners in each move group. There may be one command for each of the signers publickey
        // or all the public keys might be listed within one command.
        val inputOwningKeys: Set<PublicKey> = inputs.map { it.state.data.holder.owningKey }.toSet()
        val signers: Set<PublicKey> = moveCommands.flatMap(CommandWithParties<TokenCommand>::signers).toSet()
        require(signers.containsAll(inputOwningKeys)) {
            "Required signers does not contain all the current owners of the tokens being moved"
        }
    }

    override fun verifyRedeem(
            redeemCommand: CommandWithParties<TokenCommand>,
            inputs: List<IndexedState<FungibleToken>>,
            outputs: List<IndexedState<FungibleToken>>,
            attachments: List<Attachment>,
            references: List<StateAndRef<ContractState>>
    ) {
        val issuedToken: IssuedTokenType = redeemCommand.value.token
        // There can be at most one output treated as a change paid back to the owner. Issuer is used to group states,
        // so it will be the same as one for the input states.
        outputs.apply {
            require(size <= 1) { "When redeeming tokens, there must be zero or one output state." }
            if (isNotEmpty()) {
                val amount = single().state.data.amount
                require(amount > Amount.zero(issuedToken)) { "If there is an output, it must have a value greater than zero." }
            }
            // Outputs can be paid to any anonymous public key, so we cannot compare keys here.
        }
        inputs.apply {
            // There must be inputs present.
            require(isNotEmpty()) { "When redeeming tokens, there must be input states present." }
            // We don't care about the token as the grouping function ensures all the inputs are of the same token.
            val inputSum: Amount<IssuedTokenType> = this.map { it.state.data }.sumTokenStatesOrZero(issuedToken)
            require(inputSum > Amount.zero(issuedToken)) {
                "When redeeming tokens an amount > ZERO must be redeemed."
            }
            val outSum: Amount<IssuedTokenType> = outputs.firstOrNull()?.state?.data?.amount
                    ?: Amount.zero(issuedToken)
            // We can't pay back more than redeeming.
            // Additionally, it doesn't make sense to run redeem and pay exact change.
            require(inputSum > outSum) { "Change shouldn't exceed amount redeemed." }
            // There can only be one issuer per group as the issuer is part of the token which is used to group states.
            // If there are multiple issuers for the same tokens then there will be a group for each issued token. So,
            // the line below should never fail on single().
            val issuerKey: PublicKey = inputs.map { it.state.data }.map(FungibleToken::issuer).toSet().single().owningKey
            val ownersKeys: List<PublicKey> = inputs.map { it.state.data.holder.owningKey }
            val signers = redeemCommand.signers
            require(issuerKey in signers) {
                "The issuer must be the signing party when an amount of tokens are redeemed."
            }
            require(signers.containsAll(ownersKeys)) {
                "Owners of redeemed states must be the signing parties."
            }
        }
    }
}
