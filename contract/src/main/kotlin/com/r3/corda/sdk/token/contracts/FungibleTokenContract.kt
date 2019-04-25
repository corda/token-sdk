package com.r3.corda.sdk.token.contracts

import com.r3.corda.sdk.token.contracts.commands.TokenCommand
import com.r3.corda.sdk.token.contracts.states.AbstractToken
import com.r3.corda.sdk.token.contracts.states.FungibleToken
import com.r3.corda.sdk.token.contracts.types.IssuedTokenType
import com.r3.corda.sdk.token.contracts.types.TokenType
import com.r3.corda.sdk.token.contracts.utilities.sumTokenStatesOrZero
import net.corda.core.contracts.Amount
import net.corda.core.contracts.CommandWithParties
import net.corda.core.identity.Party
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
 * 2. Override the [AbstractTokenContract.dispatchOnCommand] method to add support for the new command, remembering
 *    to call the super method to handle the existing commands.
 * 3. Add a method to handle the new command in the new sub-class contract.
 */
open class FungibleTokenContract<T : TokenType> : AbstractTokenContract<T, FungibleToken<T>>() {

    companion object {
        val contractId = this::class.java.enclosingClass.canonicalName
    }

    override fun groupStates(tx: LedgerTransaction): List<InOutGroup<FungibleToken<T>, IssuedTokenType<T>>> {
        return tx.groupStates { state -> state.issuedTokenType }
    }


    override fun verifyIssue(
            issueCommand: CommandWithParties<TokenCommand<T>>,
            inputs: List<FungibleToken<T>>,
            outputs: List<FungibleToken<T>>
    ) {
        val issuedToken: IssuedTokenType<T> = issueCommand.value.token
        require(inputs.isEmpty()) { "When issuing tokens, there cannot be any input states." }
        outputs.apply {
            require(isNotEmpty()) { "When issuing tokens, there must be output states." }
            // We don't care about the token as the grouping function ensures that all the outputs are of the same
            // token.
            require(sumTokenStatesOrZero(issuedToken) > Amount.zero(issuedToken)) {
                "When issuing tokens an amount > ZERO must be issued."
            }
            val hasZeroAmounts = any { it.amount == Amount.zero(issuedToken) }
            require(hasZeroAmounts.not()) { "You cannot issue tokens with a zero amount." }
            // There can only be one issuer per group as the issuer is part of the token which is used to group states.
            // If there are multiple issuers for the same tokens then there will be a group for each issued token. So,
            // the line below should never fail on single().
            val issuer: Party = map(AbstractToken<T>::issuer).toSet().single()
            // Only the issuer should be signing the issuer command.
            require(issueCommand.signers.singleOrNull { it == issuer.owningKey } != null) {
                "The issuer must be the only signing party when an amount of tokens are issued."
            }
        }
    }

    override fun verifyMove(
            moveCommands: List<CommandWithParties<TokenCommand<T>>>,
            inputs: List<FungibleToken<T>>,
            outputs: List<FungibleToken<T>>
    ) {
        val issuedToken: IssuedTokenType<T> = moveCommands.single().value.token
        // There must be inputs and outputs present.
        require(inputs.isNotEmpty()) { "When moving tokens, there must be input states present." }
        require(outputs.isNotEmpty()) { "When moving tokens, there must be output states present." }
        // Sum the amount of input and output tokens.
        val inputAmount: Amount<IssuedTokenType<T>> = inputs.sumTokenStatesOrZero(issuedToken)
        require(inputAmount > Amount.zero(issuedToken)) { "In move groups there must be an amount of input tokens > ZERO." }
        val outputAmount: Amount<IssuedTokenType<T>> = outputs.sumTokenStatesOrZero(issuedToken)
        require(outputAmount > Amount.zero(issuedToken)) { "In move groups there must be an amount of output tokens > ZERO." }
        // Input and output amounts must be equal.
        require(inputAmount == outputAmount) {
            "In move groups the amount of input tokens MUST EQUAL the amount of output tokens. In other words, you " +
                    "cannot create or destroy value when moving tokens."
        }
        val hasZeroAmounts = outputs.any { it.amount == Amount.zero(issuedToken) }
        require(hasZeroAmounts.not()) { "You cannot create output token amounts with a ZERO amount." }
        // There can be different owners in each move group. There map be one command for each of the signers publickey
        // or all the public keys might be listed within one command.
        val inputOwningKeys: Set<PublicKey> = inputs.map { it.holder.owningKey }.toSet()
        val signers: Set<PublicKey> = moveCommands.flatMap(CommandWithParties<TokenCommand<T>>::signers).toSet()
        require(inputOwningKeys == signers) {
            "There are required signers missing or some of the specified signers are not required. A transaction " +
                    "to move token amounts must be signed by ONLY ALL the owners of ALL the input token amounts."
        }
    }

    override fun verifyRedeem(
            redeemCommand: CommandWithParties<TokenCommand<T>>,
            inputs: List<FungibleToken<T>>,
            outputs: List<FungibleToken<T>>
    ) {
        val issuedToken: IssuedTokenType<T> = redeemCommand.value.token
        // There can be at most one output treated as a change paid back to the owner. Issuer is used to group states,
        // so it will be the same as one for the input states.
        outputs.apply {
            require(size <= 1) { "When redeeming tokens, there must be zero or one output state." }
            if (isNotEmpty()) {
                val amount = single().amount
                require(amount > Amount.zero(issuedToken)) { "If there is an output, it must have a value greater than zero." }
            }
            // Outputs can be paid to any anonymous public key, so we cannot compare keys here.
        }
        inputs.apply {
            // There must be inputs present.
            require(isNotEmpty()) { "When redeeming tokens, there must be input states present." }
            // We don't care about the token as the grouping function ensures all the inputs are of the same token.
            val inputSum: Amount<IssuedTokenType<T>> = sumTokenStatesOrZero(issuedToken)
            require(inputSum > Amount.zero(issuedToken)) {
                "When redeeming tokens an amount > ZERO must be redeemed."
            }
            val outSum: Amount<IssuedTokenType<T>> = outputs.firstOrNull()?.amount ?: Amount.zero(issuedToken)
            // We can't pay back more than redeeming.
            // Additionally, it doesn't make sense to run redeem and pay exact change.
            require(inputSum > outSum) { "Change shouldn't exceed amount redeemed." }
            // There can only be one issuer per group as the issuer is part of the token which is used to group states.
            // If there are multiple issuers for the same tokens then there will be a group for each issued token. So,
            // the line below should never fail on single().
            val issuerKey: PublicKey = inputs.map(FungibleToken<T>::issuer).toSet().single().owningKey
            val ownersKeys: List<PublicKey> = inputs.map { it.holder.owningKey }
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