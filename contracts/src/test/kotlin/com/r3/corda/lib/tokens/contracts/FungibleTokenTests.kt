package com.r3.corda.lib.tokens.contracts

import com.r3.corda.lib.tokens.contracts.commands.IssueTokenCommand
import com.r3.corda.lib.tokens.contracts.commands.MoveTokenCommand
import com.r3.corda.lib.tokens.contracts.commands.RedeemTokenCommand
import com.r3.corda.lib.tokens.contracts.states.FungibleToken
import com.r3.corda.lib.tokens.contracts.utilities.getAttachmentIdForGenericParam
import com.r3.corda.lib.tokens.contracts.utilities.heldBy
import com.r3.corda.lib.tokens.contracts.utilities.issuedBy
import com.r3.corda.lib.tokens.contracts.utilities.of
import com.r3.corda.lib.tokens.money.GBP
import com.r3.corda.lib.tokens.money.USD
import com.r3.corda.lib.tokens.testing.states.RUB
import net.corda.core.contracts.Amount
import org.junit.Test

// TODO: Some of these tests are testing AbstractToken and should be moved into the super-class.
class FungibleTokenTests : ContractTestCommon() {

    @Test
    fun `issue token tests`() {
        val gbpHash = GBP.importAttachment(aliceServices.attachments)

        val issuedToken = GBP issuedBy ISSUER.party
        transaction {
            // Start with only one output.
            output(FungibleTokenContract.contractId, 10 of issuedToken heldBy ALICE.party)
            attachment(gbpHash)
            // No command fails.
            tweak {
                this `fails with` "A transaction must contain at least one command"
            }
            // Signed by a party other than the issuer.
            tweak {
                command(BOB.publicKey, IssueTokenCommand(issuedToken))
                this `fails with` "The issuer must be the only signing party when an amount of tokens are issued."
            }
            // Non issuer signature present.
            tweak {
                command(listOf(BOB.publicKey, BOB.publicKey), IssueTokenCommand(issuedToken))
                this `fails with` "The issuer must be the only signing party when an amount of tokens are issued."
            }
            // With an incorrect command.
            tweak {
                command(BOB.publicKey, WrongCommand())
                this `fails with` "There must be at least one token command in this transaction."
            }
            // With different command types for one group.
            tweak {
                command(ISSUER.publicKey, IssueTokenCommand(issuedToken))
                command(ISSUER.publicKey, MoveTokenCommand(issuedToken))
                this `fails with` "There must be exactly one TokenCommand type per group! For example: You cannot " +
                        "map an Issue AND a Move command to one group of tokens in a transaction."
            }
            // Includes a group with no assigned command.
            tweak {
                output(FungibleTokenContract.contractId, 10.USD issuedBy ISSUER.party heldBy ALICE.party)
                command(ISSUER.publicKey, IssueTokenCommand(issuedToken))
                this `fails with` "There is a token group with no assigned command!"
            }
            // With a zero amount in another group.
            tweak {
                val otherToken = USD issuedBy ISSUER.party
                output(FungibleTokenContract.contractId, 0 of otherToken heldBy ALICE.party)
                command(ISSUER.publicKey, IssueTokenCommand(issuedToken))
                command(ISSUER.publicKey, IssueTokenCommand(otherToken))
                this `fails with` "When issuing tokens an amount > ZERO must be issued."
            }
            // With some input states.
            tweak {
                input(FungibleTokenContract.contractId, 10 of issuedToken heldBy ALICE.party)
                command(ISSUER.publicKey, IssueTokenCommand(issuedToken))
                this `fails with` "When issuing tokens, there cannot be any input states."
            }
            // Includes a zero output.
            tweak {
                output(FungibleTokenContract.contractId, 0 of issuedToken heldBy ALICE.party)
                command(ISSUER.publicKey, IssueTokenCommand(issuedToken))
                this `fails with` "You cannot issue tokens with a zero amount."
            }
            // Includes another token type and a matching command.
            tweak {
                val otherToken = USD issuedBy ISSUER.party
                output(FungibleTokenContract.contractId, 10 of otherToken heldBy ALICE.party)
                command(ISSUER.publicKey, IssueTokenCommand(issuedToken))
                command(ISSUER.publicKey, IssueTokenCommand(otherToken))
                verifies()
            }
            // Includes more output states of the same token type.
            tweak {
                output(FungibleTokenContract.contractId, 10 of issuedToken heldBy ALICE.party)
                output(FungibleTokenContract.contractId, 100 of issuedToken heldBy ALICE.party)
                output(FungibleTokenContract.contractId, 1000 of issuedToken heldBy ALICE.party)
                command(ISSUER.publicKey, IssueTokenCommand(issuedToken))
                verifies()
            }
            // Includes the same token issued by a different issuer.
            // You wouldn't usually do this but it is possible.
            tweak {
                output(FungibleTokenContract.contractId, 1.GBP issuedBy BOB.party heldBy ALICE.party)
                command(ISSUER.publicKey, IssueTokenCommand(issuedToken))
                command(BOB.publicKey, IssueTokenCommand(GBP issuedBy BOB.party))
                verifies()
            }
            // With the correct command and signed by the issuer.
            tweak {
                command(ISSUER.publicKey, IssueTokenCommand(issuedToken))
                verifies()
            }
        }
    }

    @Test
    fun `move token tests`() {

        val gbpHash = GBP.importAttachment(aliceServices.attachments)

        val issuedToken = GBP issuedBy ISSUER.party
        transaction {
            // Start with a basic move which moves 10 tokens in entirety from ALICE to BOB.
            input(FungibleTokenContract.contractId, 10 of issuedToken heldBy ALICE.party)
            output(FungibleTokenContract.contractId, 10 of issuedToken heldBy BOB.party)
            attachment(gbpHash)

            // Add the move command, signed by ALICE.
            tweak {
                command(ALICE.publicKey, MoveTokenCommand(issuedToken))
                verifies()
            }

            // Move coupled with an issue.
            tweak {
                output(FungibleTokenContract.contractId, 10.USD issuedBy BOB.party heldBy ALICE.party)
                command(BOB.publicKey, IssueTokenCommand(USD issuedBy BOB.party))
                // Command for the move.
                command(ALICE.publicKey, MoveTokenCommand(issuedToken))
                verifies()
            }

            // Input missing.
            tweak {
                output(FungibleTokenContract.contractId, 10.USD issuedBy BOB.party heldBy BOB.party)
                command(ALICE.publicKey, MoveTokenCommand(USD issuedBy BOB.party))
                // Command for the move.
                command(ALICE.publicKey, MoveTokenCommand(issuedToken))
                this `fails with` "When moving tokens, there must be input states present."
            }

            // Output missing.
            tweak {
                input(FungibleTokenContract.contractId, 10.USD issuedBy BOB.party heldBy ALICE.party)
                command(ALICE.publicKey, MoveTokenCommand(USD issuedBy BOB.party))
                // Command for the move.
                command(ALICE.publicKey, MoveTokenCommand(issuedToken))
                this `fails with` "When moving tokens, there must be output states present."
            }

            // Inputs sum to zero.
            tweak {
                input(FungibleTokenContract.contractId, 0.USD issuedBy BOB.party heldBy ALICE.party)
                input(FungibleTokenContract.contractId, 0.USD issuedBy BOB.party heldBy ALICE.party)
                output(FungibleTokenContract.contractId, 10.USD issuedBy BOB.party heldBy BOB.party)
                command(ALICE.publicKey, MoveTokenCommand(USD issuedBy BOB.party))
                // Command for the move.
                command(ALICE.publicKey, MoveTokenCommand(issuedToken))
                this `fails with` "In move groups there must be an amount of input tokens > ZERO."
            }

            // Outputs sum to zero.
            tweak {
                input(FungibleTokenContract.contractId, 10.USD issuedBy BOB.party heldBy ALICE.party)
                output(FungibleTokenContract.contractId, 0.USD issuedBy BOB.party heldBy BOB.party)
                output(FungibleTokenContract.contractId, 0.USD issuedBy BOB.party heldBy BOB.party)
                command(ALICE.publicKey, MoveTokenCommand(USD issuedBy BOB.party))
                // Command for the move.
                command(ALICE.publicKey, MoveTokenCommand(issuedToken))
                this `fails with` "In move groups there must be an amount of output tokens > ZERO."
            }

            // Unbalanced move.
            tweak {
                input(FungibleTokenContract.contractId, 10.USD issuedBy BOB.party heldBy ALICE.party)
                output(FungibleTokenContract.contractId, 11.USD issuedBy BOB.party heldBy BOB.party)
                command(ALICE.publicKey, MoveTokenCommand(USD issuedBy BOB.party))
                // Command for the move.
                command(ALICE.publicKey, MoveTokenCommand(issuedToken))
                this `fails with` "In move groups the amount of input tokens MUST EQUAL the amount of output tokens. " +
                        "In other words, you cannot create or destroy value when moving tokens."
            }

            tweak {
                input(FungibleTokenContract.contractId, 10.USD issuedBy BOB.party heldBy ALICE.party)
                output(FungibleTokenContract.contractId, 10.USD issuedBy BOB.party heldBy BOB.party)
                output(FungibleTokenContract.contractId, 0.USD issuedBy BOB.party heldBy BOB.party)
                command(ALICE.publicKey, MoveTokenCommand(USD issuedBy BOB.party))
                // Command for the move.
                command(ALICE.publicKey, MoveTokenCommand(issuedToken))
                this `fails with` "You cannot create output token amounts with a ZERO amount."
            }

            // Two moves (two different groups).
            tweak {
                input(FungibleTokenContract.contractId, 10.USD issuedBy BOB.party heldBy ALICE.party)
                output(FungibleTokenContract.contractId, 10.USD issuedBy BOB.party heldBy BOB.party)
                command(ALICE.publicKey, MoveTokenCommand(USD issuedBy BOB.party))
                // Command for the move.
                command(ALICE.publicKey, MoveTokenCommand(issuedToken))
                verifies()
            }

            // Two moves (one group).
            tweak {
                // Add a basic move from Peter to Paul.
                input(FungibleTokenContract.contractId, 20 of issuedToken heldBy CHARLIE.party)
                output(FungibleTokenContract.contractId, 20 of issuedToken heldBy DAENERYS.party)
                command(ALICE.publicKey, MoveTokenCommand(issuedToken))
                command(CHARLIE.publicKey, MoveTokenCommand(issuedToken))
                verifies()
            }

            // Wrong public key.
            tweak {
                command(BOB.publicKey, MoveTokenCommand(issuedToken))
                this `fails with` "There are required signers missing or some of the specified signers are not " +
                        "required. A transaction to move token amounts must be signed by ONLY ALL the owners " +
                        "of ALL the input token amounts."
            }

            // Includes an incorrect public with the correct key still being present.
            tweak {
                command(listOf(BOB.publicKey, ALICE.publicKey), MoveTokenCommand(issuedToken))
                this `fails with` "There are required signers missing or some of the specified signers are not " +
                        "required. A transaction to move token amounts must be signed by ONLY ALL the owners " +
                        "of ALL the input token amounts."
            }
        }
    }

    @Test
    fun `redeem token tests`() {
        val gbpHash = GBP.importAttachment(aliceServices.attachments)
        val issuedToken = GBP issuedBy ISSUER.party
        val otherIssuerToken = GBP issuedBy BOB.party
        transaction {
            // Start with a basic redeem which redeems 10 tokens in entirety from ALICE .
            input(FungibleTokenContract.contractId, 10 of issuedToken heldBy ALICE.party)
            attachment(gbpHash)

            // Add the redeem command, signed by the ISSUER.
            tweak {
                command(ISSUER.publicKey, RedeemTokenCommand(issuedToken))
                this `fails with` "Contract verification failed: Owners of redeemed states must be the signing parties."
            }
            // Add the redeem command, signed by the holder but not issuer.
            tweak {
                command(ALICE.publicKey, RedeemTokenCommand(issuedToken))
                this `fails with` "Contract verification failed: The issuer must be the signing party when an amount of tokens are redeemed."
            }
            // Add the redeem command, signed by the ISSUER and ALICE - owner. Zero outputs.
            tweak {
                command(listOf(ISSUER.publicKey, ALICE.publicKey), RedeemTokenCommand(issuedToken))
                verifies()
            }

            // Add additional input to redeem, by different issuer. Fails because it requires command signed by that issuer.
            tweak {
                input(FungibleTokenContract.contractId, 10 of otherIssuerToken heldBy ALICE.party)
                command(listOf(ISSUER.publicKey, ALICE.publicKey), RedeemTokenCommand(otherIssuerToken))
                this `fails with` "There is a token group with no assigned command!"
            }

            // Two redeem groups verify.
            tweak {
                input(FungibleTokenContract.contractId, 10 of otherIssuerToken heldBy ALICE.party)
                command(listOf(ISSUER.publicKey, ALICE.publicKey), RedeemTokenCommand(issuedToken))
                command(listOf(BOB.publicKey, ALICE.publicKey), RedeemTokenCommand(otherIssuerToken))
                verifies()
            }

            // Add the redeem command, signed by the ISSUER and ALICE - owner. One output.
            tweak {
                command(listOf(ISSUER.publicKey, ALICE.publicKey), RedeemTokenCommand(issuedToken))
                // Return change back to Alice.
                output(FungibleTokenContract.contractId, 5 of issuedToken heldBy ALICE.party)
                verifies()
            }

            // Add the redeem command, signed by the ISSUER and ALICE - owner. One output - but different owner, can happen with anonymous keys.
            tweak {
                command(listOf(ISSUER.publicKey, ALICE.publicKey), RedeemTokenCommand(issuedToken))
                // Return change back to BOB.
                output(FungibleTokenContract.contractId, 5 of issuedToken heldBy BOB.party)
                verifies()
            }

            // Add the redeem command, signed by the ISSUER and ALICE - owner. Output too big.
            tweak {
                command(listOf(ISSUER.publicKey, ALICE.publicKey), RedeemTokenCommand(issuedToken))
                // Return change back to Alice.
                output(FungibleTokenContract.contractId, 10 of issuedToken heldBy ALICE.party)
                this `fails with` "Change shouldn't exceed amount redeemed."
            }

            // Add the redeem command, signed by the ISSUER and ALICE - owner. Output is zero.
            tweak {
                command(listOf(ISSUER.publicKey, ALICE.publicKey), RedeemTokenCommand(issuedToken))
                // Return change back to Alice.
                output(FungibleTokenContract.contractId, Amount.zero(issuedToken) heldBy ALICE.party)
                this `fails with` "If there is an output, it must have a value greater than zero."
            }

            // Add the redeem command, signed by the ISSUER and ALICE - owner. Too many outputs.
            tweak {
                command(listOf(ISSUER.publicKey, ALICE.publicKey), RedeemTokenCommand(issuedToken))
                // Return change back to Alice.
                output(FungibleTokenContract.contractId, 2 of issuedToken heldBy ALICE.party)
                output(FungibleTokenContract.contractId, 2 of issuedToken heldBy ALICE.party)
                this `fails with` "When redeeming tokens, there must be zero or one output state."
            }

            // Add the redeem command, signed by the ISSUER and ALICE - owner. No input for one group.
            // The commands signed by BOB and ALICE is ignored as there are no tokens issued by BOB in this transaction.
            tweak {
                command(listOf(ISSUER.publicKey, ALICE.publicKey), RedeemTokenCommand(issuedToken))
                command(listOf(BOB.publicKey, ALICE.publicKey), RedeemTokenCommand(otherIssuerToken))
                verifies()
            }

            tweak {
                command(listOf(ISSUER.publicKey, ALICE.publicKey), RedeemTokenCommand(issuedToken))
                command(listOf(BOB.publicKey, ALICE.publicKey), RedeemTokenCommand(otherIssuerToken))
                output(FungibleTokenContract.contractId, 2 of otherIssuerToken heldBy ALICE.party)
                this `fails with` " When redeeming tokens, there must be input states present."
            }

            // Zero amount input - fail.
            tweak {
                input(FungibleTokenContract.contractId, Amount.zero(otherIssuerToken) heldBy ALICE.party)
                command(listOf(ISSUER.publicKey, ALICE.publicKey), RedeemTokenCommand(issuedToken))
                command(listOf(BOB.publicKey, ALICE.publicKey), RedeemTokenCommand(otherIssuerToken))
                this `fails with` "When redeeming tokens an amount > ZERO must be redeemed."
            }
        }
    }

    @Test
    fun `should enforce the presence of the token type jar`() {
        val issuedToken = GBP issuedBy ISSUER.party
        transaction {
            output(FungibleTokenContract.contractId, 10 of issuedToken heldBy BOB.party)
            command(ISSUER.publicKey, IssueTokenCommand(issuedToken))
            this.failsWith("Expected to find type jar:")


            tweak {
                attachment(GBP.importAttachment(aliceServices.attachments))
                verifies()
            }
        }
    }

    @Test
    fun `should enforce that the hash providing the token type cannot change during a transaction`() {
        val issuedToken = GBP issuedBy ISSUER.party
        transaction {
            input(FungibleTokenContract.contractId, 10 of issuedToken heldBy BOB.party)
            output(FungibleTokenContract.contractId, FungibleToken(10 of issuedToken, BOB.party, RUB.getAttachmentIdForGenericParam()))
            command(BOB.party.owningKey, MoveTokenCommand(issuedToken))

            attachment(GBP.importAttachment(aliceServices.attachments))
            attachment(RUB.importAttachment(aliceServices.attachments))

            this.failsWith("There must only be one Jar (Hash) providing TokenType: GBP")
        }
    }
}
