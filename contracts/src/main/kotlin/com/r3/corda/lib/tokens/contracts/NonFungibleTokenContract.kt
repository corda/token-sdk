package com.r3.corda.lib.tokens.contracts

import com.r3.corda.lib.tokens.contracts.commands.TokenCommand
import com.r3.corda.lib.tokens.contracts.states.NonFungibleToken
import net.corda.core.contracts.Attachment
import net.corda.core.contracts.CommandWithParties
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.StateAndRef
import net.corda.core.internal.uncheckedCast
import java.security.PublicKey

/**
 * See kdoc for [FungibleTokenContract].
 */
open class NonFungibleTokenContract : AbstractTokenContract<NonFungibleToken>() {

    override val accepts: Class<NonFungibleToken> get() = uncheckedCast(NonFungibleToken::class.java)

    companion object {
        val contractId = this::class.java.enclosingClass.canonicalName
    }

    override fun verifyIssue(
            issueCommand: CommandWithParties<TokenCommand>,
            inputs: List<IndexedState<NonFungibleToken>>,
            outputs: List<IndexedState<NonFungibleToken>>,
            attachments: List<Attachment>,
            references: List<StateAndRef<ContractState>>
    ) {
        require(inputs.isEmpty()) { "When issuing non fungible tokens, there cannot be any input states." }
        require(outputs.size == 1) { "When issuing non fungible tokens, there must be a single output state." }
        val output = outputs.single()
        val issuerKey: PublicKey = output.state.data.issuer.owningKey
        val issueSigners: List<PublicKey> = issueCommand.signers
        // The issuer should be signing the issue command. Notice that it can be signed by more parties.
        require(issuerKey in issueSigners) {
            "The issuer must be the signing party when a token is issued."
        }
    }

    // NOTE:
    // We cannot have two of the same token; two token states containing the same info because they are linear states.
    // Even if we have two tokens containing the same info, they will have different linear IDs so end up in different
    // groups.
    override fun verifyMove(
            moveCommands: List<CommandWithParties<TokenCommand>>,
            inputs: List<IndexedState<NonFungibleToken>>,
            outputs: List<IndexedState<NonFungibleToken>>,
            attachments: List<Attachment>,
            references: List<StateAndRef<ContractState>>
    ) {
        // There must be inputs and outputs present.
        require(inputs.isNotEmpty()) { "When moving a non fungible token, there must be one input state present." }
        require(outputs.isNotEmpty()) { "When moving a non fungible token, there must be one output state present." }
        // Sum the amount of input and output tokens.
        val input = inputs.single()
        val output = outputs.single()
        require(input.state.data.token == output.state.data.token) {
            "When moving a token, there must be an input and corresponding output for that token."
        }
        // There should only be one move command with one signature.
        require(moveCommands.size == 1) { "There should be only one move command per group when moving non fungible tokens." }
        require(input.state.data.linearId == output.state.data.linearId) { "The linear ID must not change." }
        val moveCommand: CommandWithParties<TokenCommand> = moveCommands.single()
        require(moveCommand.signers.toSet() == setOf(input.state.data.holder.owningKey)) {
            "The current holder must be the only signing party when a non-fungible (discrete) token is moved."
        }
    }

    override fun verifyRedeem(
            redeemCommand: CommandWithParties<TokenCommand>,
            inputs: List<IndexedState<NonFungibleToken>>,
            outputs: List<IndexedState<NonFungibleToken>>,
            attachments: List<Attachment>,
            references: List<StateAndRef<ContractState>>
    ) {
        // There must be inputs and outputs present.
        require(outputs.isEmpty()) { "When redeeming a held token, there must be no output." }
        require(inputs.size == 1) { "When redeeming a held token, there must be only one input." }
        val heldToken = inputs.single()
        // Only the issuer and holders should be signing the redeem command.
        // There will only ever be one issuer as the issuer forms part of the grouping key.
        val issuerKey = heldToken.state.data.token.issuer.owningKey
        val holdersKeys = inputs.map { it.state.data.holder.owningKey }
        val signers = redeemCommand.signers
        require(issuerKey in signers) {
            "The issuer must be a signing party when an amount of tokens are redeemed."
        }
        require(signers.containsAll(holdersKeys)) {
            "Holders of redeemed states must be the signing parties."
        }
    }
}
