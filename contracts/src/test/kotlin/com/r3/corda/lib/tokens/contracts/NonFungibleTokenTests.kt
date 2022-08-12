package com.r3.corda.lib.tokens.contracts

import com.r3.corda.lib.tokens.contracts.commands.IssueTokenCommand
import com.r3.corda.lib.tokens.contracts.commands.MoveTokenCommand
import com.r3.corda.lib.tokens.contracts.commands.RedeemTokenCommand
import com.r3.corda.lib.tokens.contracts.utilities.heldBy
import com.r3.corda.lib.tokens.contracts.utilities.issuedBy
import com.r3.corda.lib.tokens.contracts.utilities.of
import com.r3.corda.lib.tokens.testing.tokentypes.PhoBowl
import com.r3.corda.lib.tokens.testing.tokentypes.Ruble
import org.junit.Test

// TODO: Some of these tests are testing AbstractToken and duplicate those in FungibleTokenTests. Move to superclass.
class NonFungibleTokenTests : ContractTestCommon() {

	private val issuedToken = PhoBowl issuedBy ISSUER.party

	@Test
	fun `issue non fungible token tests`() {
		transaction {
			// Start with only one output.
			output(NonFungibleTokenContract.contractId, issuedToken.heldBy(ALICE.party))
			attachment(issuedToken.tokenType.importAttachment(aliceServices.attachments))
			// No command fails.
			tweak {
				this `fails with` "A transaction must contain at least one command"
			}
			// Signed by a party other than the issuer.
			tweak {
				command(BOB.publicKey, IssueTokenCommand(issuedToken, outputIndexes = listOf(0)))
				this `fails with` "The issuer must be the signing party when a token is issued."
			}
			// Non issuer signature present.
			tweak {
				command(listOf(BOB.publicKey, BOB.publicKey), IssueTokenCommand(issuedToken, outputIndexes = listOf(0)))
				this `fails with` "The issuer must be the signing party when a token is issued."
			}

			// Issuer and other signature present.
			tweak {
				command(listOf(ISSUER.publicKey, BOB.publicKey), IssueTokenCommand(issuedToken, outputIndexes = listOf(0)))
				verifies()
			}

			// With an incorrect command.
			tweak {
				command(BOB.publicKey, WrongCommand())
				this `fails with` "There must be at least one token command in this transaction."
			}
			// With different command types for one group.
			tweak {
				command(ISSUER.publicKey, IssueTokenCommand(issuedToken, outputIndexes = listOf(0)))
				command(ISSUER.publicKey, MoveTokenCommand(issuedToken, outputIndexes = listOf(0)))
				this `fails with` "There must be exactly one TokenCommand type per group! For example: You cannot " +
						"map an Issue AND a Move command to one group of tokens in a transaction."
			}
			// Includes a group with no assigned command.
			tweak {
				output(FungibleTokenContract.contractId, 10.ofType(CommonTokens.USD) issuedBy ISSUER.party heldBy ALICE.party)
				command(ISSUER.publicKey, IssueTokenCommand(issuedToken, outputIndexes = listOf(0)))
				this `fails with` "There is a token group with no assigned command!"
			}
			// With some input states.
			tweak {
				input(NonFungibleTokenContract.contractId, issuedToken heldBy ALICE.party)
				command(ISSUER.publicKey, IssueTokenCommand(issuedToken, outputIndexes = listOf(0)))
				this `fails with` "There is a token group with no assigned command"
			}

			// Includes another token type and a matching command.
			tweak {
				val otherToken = CommonTokens.USD issuedBy ISSUER.party
				output(FungibleTokenContract.contractId, 10 of otherToken heldBy ALICE.party)
				command(ISSUER.publicKey, IssueTokenCommand(issuedToken, outputIndexes = listOf(0)))
				command(ISSUER.publicKey, IssueTokenCommand(otherToken, outputIndexes = listOf(1)))
				verifies()
			}
			// Includes more output states of the same token type.
			tweak {
				output(NonFungibleTokenContract.contractId, issuedToken heldBy ALICE.party)
				output(NonFungibleTokenContract.contractId, issuedToken heldBy ALICE.party)
				command(ISSUER.publicKey, IssueTokenCommand(issuedToken, outputIndexes = listOf(0, 1, 2)))
				this `fails with` "When issuing non fungible tokens, there must be a single output state."
			}
			// Includes the same token issued by a different issuer.
			// You wouldn't usually do this but it is possible.
			tweak {
				output(NonFungibleTokenContract.contractId, issuedToken issuedBy BOB.party heldBy ALICE.party)
				command(ISSUER.publicKey, IssueTokenCommand(issuedToken, outputIndexes = listOf(0)))
				command(BOB.publicKey, IssueTokenCommand(issuedToken issuedBy BOB.party, outputIndexes = listOf(1)))
				verifies()
			}
			// With the correct command and signed by the issuer.
			tweak {
				command(ISSUER.publicKey, IssueTokenCommand(issuedToken, outputIndexes = listOf(0)))
				verifies()
			}
		}
	}

	@Test
	fun `move non fungible token tests`() {
		val heldByAlice = issuedToken heldBy ALICE.party
		val heldByBob = heldByAlice.withNewHolder(BOB.party)

		transaction {
			// Start with a basic move a PhoBowl from ALICE to BOB.
			input(NonFungibleTokenContract.contractId, heldByAlice)
			output(NonFungibleTokenContract.contractId, heldByBob)
			attachment(PhoBowl.importAttachment(aliceServices.attachments))

			// Add the move command, signed by ALICE.
			tweak {
				command(ALICE.publicKey, MoveTokenCommand(issuedToken, inputIndexes = listOf(0), outputIndexes = listOf(0)))
				verifies()
			}

			// Move coupled with an issue.
			tweak {
				output(FungibleTokenContract.contractId, 10.ofType(CommonTokens.USD) issuedBy BOB.party heldBy ALICE.party)
				command(BOB.publicKey, IssueTokenCommand(CommonTokens.USD issuedBy BOB.party, outputIndexes = listOf(1)))
				// Command for the move.
				command(ALICE.publicKey, MoveTokenCommand(issuedToken, inputIndexes = listOf(0), outputIndexes = listOf(0)))
				verifies()
			}

			// Input missing.
			tweak {
				val output = issuedToken issuedBy BOB.party heldBy BOB.party
				output(FungibleTokenContract.contractId, output)
				command(ALICE.publicKey, MoveTokenCommand(issuedToken issuedBy BOB.party, outputIndexes = listOf(1)))
				// Command for the move.
				command(ALICE.publicKey, MoveTokenCommand(issuedToken, inputIndexes = listOf(0), outputIndexes = listOf(0)))
				this `fails with` "When moving a non fungible token, there must be one input state present."
			}

			// Output missing.
			tweak {
				input(NonFungibleTokenContract.contractId, issuedToken issuedBy BOB.party heldBy ALICE.party)
				command(ALICE.publicKey, MoveTokenCommand(issuedToken issuedBy BOB.party, inputIndexes = listOf(1)))
				// Command for the move.
				command(ALICE.publicKey, MoveTokenCommand(issuedToken, inputIndexes = listOf(0), outputIndexes = listOf(0)))
				this `fails with` "When moving a non fungible token, there must be one output state present."
			}

			// Two moves (two different groups).
			tweak {
				val anotherIssuedToken = Ruble issuedBy CHARLIE.party
				val anotherIssuedTokenHeldByAlice = anotherIssuedToken heldBy ALICE.party

				input(NonFungibleTokenContract.contractId, anotherIssuedTokenHeldByAlice)
				output(NonFungibleTokenContract.contractId, anotherIssuedTokenHeldByAlice.withNewHolder(BOB.party))

				command(ALICE.publicKey, MoveTokenCommand(issuedToken, inputIndexes = listOf(0), outputIndexes = listOf(0)))
				// Command for the move.
				command(ALICE.publicKey, MoveTokenCommand(anotherIssuedToken, inputIndexes = listOf(1), outputIndexes = listOf(1)))
				verifies()
			}

			// Two moves (one group).
			tweak {
				//This is now possible with the indexed commands
				input(FungibleTokenContract.contractId, 20 of issuedToken heldBy CHARLIE.party)
				output(FungibleTokenContract.contractId, 20 of issuedToken heldBy DAENERYS.party)
				command(ALICE.publicKey, MoveTokenCommand(issuedToken, inputIndexes = listOf(0), outputIndexes = listOf(0)))
				command(CHARLIE.publicKey, MoveTokenCommand(issuedToken, inputIndexes = listOf(1), outputIndexes = listOf(1)))
				verifies()
			}

			// Wrong public key.
			tweak {
				command(BOB.publicKey, MoveTokenCommand(issuedToken, inputIndexes = listOf(0), outputIndexes = listOf(0)))
				this `fails with` "The current holder must be the only signing party when a non-fungible (discrete) token is moved."
			}

			// Includes an incorrect public with the correct key still being present.
			tweak {
				command(listOf(BOB.publicKey, ALICE.publicKey), MoveTokenCommand(issuedToken, inputIndexes = listOf(0), outputIndexes = listOf(0)))
				this `fails with` "The current holder must be the only signing party when a non-fungible (discrete) token is moved."
			}
		}
	}

	@Test
	fun `redeem non fungible token tests`() {
		val heldByAlice = issuedToken heldBy ALICE.party
		transaction {
			// Start with a basic move which redeems a non fungible token.
			input(NonFungibleTokenContract.contractId, heldByAlice)
			attachment(PhoBowl.importAttachment(aliceServices.attachments))

			// Add the redeem command, signed by ALICE but not the issuer.
			tweak {
				command(ALICE.publicKey, RedeemTokenCommand(issuedToken, inputIndexes = listOf(0)))
				this `fails with` "The issuer must be a signing party when an amount of tokens are redeemed"
			}

			// Add the redeem command, signed by the issuer but not alice.
			tweak {
				command(ISSUER.publicKey, RedeemTokenCommand(issuedToken, inputIndexes = listOf(0)))
				this `fails with` "Holders of redeemed states must be the signing parties."
			}

			// Add the issuer's and alice's public key. Will verify.
			tweak {
				command(listOf(ALICE.publicKey, ISSUER.publicKey), RedeemTokenCommand(issuedToken, inputIndexes = listOf(0)))
				verifies()
			}

			// Spurious output.
			tweak {
				command(listOf(ALICE.publicKey, ISSUER.publicKey), RedeemTokenCommand(issuedToken, inputIndexes = listOf(0), outputIndexes = listOf(0)))
				output(NonFungibleTokenContract.contractId, issuedToken heldBy ALICE.party)
				this `fails with` "When redeeming a held token, there must be no output."
			}

			// Additional input.
			tweak {
				command(listOf(ALICE.publicKey, ISSUER.publicKey), RedeemTokenCommand(issuedToken, inputIndexes = listOf(0, 1)))
				input(NonFungibleTokenContract.contractId, heldByAlice)
				this `fails with` "When redeeming a held token, there must be only one input."
			}

			// Two redeem groups. This technically won't happen, as you won't redeem a token from two different issuers
			// at the same time but the contract does support it.
			tweak {
				val otherIssuedToken = PhoBowl issuedBy BOB.party
				input(NonFungibleTokenContract.contractId, otherIssuedToken heldBy ALICE.party)
				command(listOf(ALICE.publicKey, BOB.publicKey), RedeemTokenCommand(otherIssuedToken, inputIndexes = listOf(1)))
				command(listOf(ALICE.publicKey, ISSUER.publicKey), RedeemTokenCommand(issuedToken, inputIndexes = listOf(0)))
				verifies()
			}
		}
	}
}




