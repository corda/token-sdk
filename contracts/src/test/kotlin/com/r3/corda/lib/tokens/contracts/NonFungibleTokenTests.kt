package com.r3.corda.lib.tokens.contracts

import com.r3.corda.lib.tokens.contracts.commands.IssueTokenCommand
import com.r3.corda.lib.tokens.contracts.commands.MoveTokenCommand
import com.r3.corda.lib.tokens.contracts.utilities.heldBy
import com.r3.corda.lib.tokens.contracts.utilities.issuedBy
import com.r3.corda.lib.tokens.contracts.utilities.of
import com.r3.corda.lib.tokens.contracts.utilities.withNewHolder
import com.r3.corda.lib.tokens.money.GBP
import com.r3.corda.lib.tokens.money.USD
import com.r3.corda.lib.tokens.testing.states.PTK
import com.r3.corda.lib.tokens.testing.states.RUB
import org.junit.Test

// TODO: Some of these tests are testing AbstractToken and duplicate those in FungibleTokenTests. Move to superclass.
class NonFungibleTokenTests : ContractTestCommon() {



    private val issuedToken = PTK issuedBy ISSUER.party

    @Test
    fun `issue non fungible token tests`() {
        transaction {
            // Start with only one output.
            output(NonFungibleTokenContract.contractId, issuedToken heldBy ALICE.party)
            attachment(issuedToken.tokenType.importAttachment(aliceServices.attachments))
            attachment(GBP.importAttachment(aliceServices.attachments))
            // No command fails.
            tweak {
                this `fails with` "A transaction must contain at least one command"
            }
            // Signed by a party other than the issuer.
            tweak {
                command(BOB.publicKey, IssueTokenCommand(issuedToken))
                this `fails with` "The issuer must be the only signing party when a token is issued."
            }
            // Non issuer signature present.
            tweak {
                command(listOf(BOB.publicKey, BOB.publicKey), IssueTokenCommand(issuedToken))
                this `fails with` "The issuer must be the only signing party when a token is issued."
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
            // With some input states.
            tweak {
                input(NonFungibleTokenContract.contractId, issuedToken heldBy ALICE.party)
                command(ISSUER.publicKey, IssueTokenCommand(issuedToken))
                this `fails with` "When issuing non fungible tokens, there cannot be any input states."
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
                output(NonFungibleTokenContract.contractId, issuedToken heldBy ALICE.party)
                output(NonFungibleTokenContract.contractId, issuedToken heldBy ALICE.party)
                command(ISSUER.publicKey, IssueTokenCommand(issuedToken))
                this `fails with` "When issuing non fungible tokens, there must be a single output state."
            }
            // Includes the same token issued by a different issuer.
            // You wouldn't usually do this but it is possible.
            tweak {
                output(NonFungibleTokenContract.contractId, issuedToken issuedBy BOB.party heldBy ALICE.party)
                command(ISSUER.publicKey, IssueTokenCommand(issuedToken))
                command(BOB.publicKey, IssueTokenCommand(issuedToken issuedBy BOB.party))
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
    fun `move non fungible token tests`() {
        val heldByAlice = issuedToken heldBy ALICE.party
        val heldByBob = heldByAlice withNewHolder BOB.party
        transaction {
            // Start with a basic move which moves 10 tokens in entirety from ALICE to BOB.
            input(NonFungibleTokenContract.contractId, heldByAlice)
            output(NonFungibleTokenContract.contractId, heldByBob)
            attachment(PTK.importAttachment(aliceServices.attachments))
            attachment(USD.importAttachment(aliceServices.attachments))

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
                val output = issuedToken issuedBy BOB.party heldBy BOB.party
                output(FungibleTokenContract.contractId, output)
                command(ALICE.publicKey, MoveTokenCommand(issuedToken issuedBy BOB.party))
                // Command for the move.
                command(ALICE.publicKey, MoveTokenCommand(issuedToken))
                this `fails with` "When moving a non fungible token, there must be one input state present."
            }

            // Output missing.
            tweak {
                input(NonFungibleTokenContract.contractId, issuedToken issuedBy BOB.party heldBy ALICE.party)
                command(ALICE.publicKey, MoveTokenCommand(issuedToken issuedBy BOB.party))
                // Command for the move.
                command(ALICE.publicKey, MoveTokenCommand(issuedToken))
                this `fails with` "When moving a non fungible token, there must be one output state present."
            }

            // Two moves (two different groups).
            tweak {
                val anotherIssuedToken = RUB issuedBy CHARLIE.party
                val anotherIssuedTokenHeldByAlice = anotherIssuedToken heldBy ALICE.party
                input(NonFungibleTokenContract.contractId, anotherIssuedTokenHeldByAlice)
                output(NonFungibleTokenContract.contractId, anotherIssuedTokenHeldByAlice withNewHolder BOB.party)
                command(ALICE.publicKey, MoveTokenCommand(issuedToken))
                // Command for the move.
                command(ALICE.publicKey, MoveTokenCommand(anotherIssuedToken))
                verifies()
            }

            // Two moves (one group).
            tweak {
                // Add a basic move from Peter to Paul.
                input(FungibleTokenContract.contractId, 20 of issuedToken heldBy CHARLIE.party)
                output(FungibleTokenContract.contractId, 20 of issuedToken heldBy DAENERYS.party)
                command(ALICE.publicKey, MoveTokenCommand(issuedToken))
                command(CHARLIE.publicKey, MoveTokenCommand(issuedToken))
                this `fails with` "There should be only one move command per group when moving non fungible tokens."
            }

            // Wrong public key.
            tweak {
                command(BOB.publicKey, MoveTokenCommand(issuedToken))
                this `fails with` "The current holder must be the only signing party when a non-fungible (discrete) token is moved."
            }

            // Includes an incorrect public with the correct key still being present.
            tweak {
                command(listOf(BOB.publicKey, ALICE.publicKey), MoveTokenCommand(issuedToken))
                this `fails with` "The current holder must be the only signing party when a non-fungible (discrete) token is moved."
            }
        }
    }

    // TODO: Add redeem tests.
}