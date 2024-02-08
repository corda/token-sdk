package com.r3.corda.lib.tokens.contracts

import com.r3.corda.lib.tokens.contracts.CommonTokens.Companion.GBP
import com.r3.corda.lib.tokens.contracts.CommonTokens.Companion.USD
import com.r3.corda.lib.tokens.contracts.commands.IssueTokenCommand
import com.r3.corda.lib.tokens.contracts.commands.MoveTokenCommand
import com.r3.corda.lib.tokens.contracts.commands.RedeemTokenCommand
import com.r3.corda.lib.tokens.contracts.states.FungibleToken
import com.r3.corda.lib.tokens.contracts.utilities.getAttachmentIdForGenericParam
import com.r3.corda.lib.tokens.contracts.utilities.heldBy
import com.r3.corda.lib.tokens.contracts.utilities.issuedBy
import com.r3.corda.lib.tokens.contracts.utilities.of
import com.r3.corda.lib.tokens.testing.states.DodgeToken
import com.r3.corda.lib.tokens.testing.states.DodgeTokenContract
import com.r3.corda.lib.tokens.testing.states.RUB
import com.r3.corda.lib.tokens.testing.states.RubleToken
import org.junit.Test

// TODO: Some of these tests are testing AbstractToken and should be moved into the super-class.
class FungibleTokenTests : ContractTestCommon() {

    @Test(timeout = 300_000)
    fun `issue token tests`() {

        val issuedToken = GBP issuedBy ISSUER.party
        transaction {
            // Start with only one output.
            output(FungibleTokenContract.contractId, 10 of issuedToken heldBy ALICE.party)
            // No command fails.
            tweak {
                this `fails with` "A transaction must contain at least one command"
            }
            // Signed by a party other than the issuer.
            tweak {
                command(BOB.publicKey, IssueTokenCommand(issuedToken, listOf(0)))
                this `fails with` "The issuer must be the signing party when an amount of tokens are issued."
            }
            // Non issuer signature present.
            tweak {
                command(listOf(BOB.publicKey, BOB.publicKey), IssueTokenCommand(issuedToken, listOf(0)))
                this `fails with` "The issuer must be the signing party when an amount of tokens are issued."
            }
            // Non issuer signature present.
            tweak {
                command(listOf(ISSUER.publicKey, BOB.publicKey), IssueTokenCommand(issuedToken, listOf(0)))
                verifies()
            }
            // With an incorrect command.
            tweak {
                command(BOB.publicKey, WrongCommand())
                this `fails with` "There must be at least one token command in this transaction."
            }
            // With different command types for one group.
            tweak {
                command(ISSUER.publicKey, IssueTokenCommand(issuedToken, listOf(0)))
                command(ISSUER.publicKey, MoveTokenCommand(issuedToken, listOf(0)))
                verifies()
            }
            // Includes a group with no assigned command.
            tweak {
                output(FungibleTokenContract.contractId, 10.ofType(USD) issuedBy ISSUER.party heldBy ALICE.party)
                command(ISSUER.publicKey, IssueTokenCommand(issuedToken, listOf(0)))
                this `fails with` "There is a token group with no assigned command!"
            }
            // With a zero amount in another group.
            tweak {
                val otherToken = USD issuedBy ISSUER.party
                output(FungibleTokenContract.contractId, 0 of otherToken heldBy ALICE.party)
                command(ISSUER.publicKey, IssueTokenCommand(issuedToken, listOf(0)))
                command(ISSUER.publicKey, IssueTokenCommand(otherToken, listOf(1)))
                this `fails with` "When issuing tokens an amount > ZERO must be issued."
            }
            // With some input states.
            tweak {
                input(FungibleTokenContract.contractId, 10 of issuedToken heldBy ALICE.party)
                command(ISSUER.publicKey, IssueTokenCommand(issuedToken))
                this `fails with` "There is a token group with no assigned command"
            }
            // Includes a zero output.
            tweak {
                output(FungibleTokenContract.contractId, 0 of issuedToken heldBy ALICE.party)
                command(ISSUER.publicKey, IssueTokenCommand(issuedToken))
                this `fails with` "There is a token group with no assigned command"
            }
            // Includes another token type and a matching command.
            tweak {
                val otherToken = USD issuedBy ISSUER.party
                output(FungibleTokenContract.contractId, 10 of otherToken heldBy ALICE.party)
                command(ISSUER.publicKey, IssueTokenCommand(issuedToken, listOf(0)))
                command(ISSUER.publicKey, IssueTokenCommand(otherToken, listOf(1)))
                verifies()
            }
            // Includes more output states of the same token type.
            tweak {
                output(FungibleTokenContract.contractId, 10 of issuedToken heldBy ALICE.party)
                output(FungibleTokenContract.contractId, 100 of issuedToken heldBy ALICE.party)
                output(FungibleTokenContract.contractId, 1000 of issuedToken heldBy ALICE.party)
                command(ISSUER.publicKey, IssueTokenCommand(issuedToken, listOf(0, 1, 2, 3)))
                verifies()
            }
            // Includes the same token issued by a different issuer.
            // You wouldn't usually do this but it is possible.
            tweak {
                output(FungibleTokenContract.contractId, 1.ofType(GBP) issuedBy BOB.party heldBy ALICE.party)
                command(ISSUER.publicKey, IssueTokenCommand(issuedToken, listOf(0)))
                command(BOB.publicKey, IssueTokenCommand(GBP issuedBy BOB.party, listOf(1)))
                verifies()
            }
            // With the correct command and signed by the issuer.
            tweak {
                command(ISSUER.publicKey, IssueTokenCommand(issuedToken, listOf(0)))
                verifies()
            }
        }
    }

    @Test(timeout = 300_000)
    fun `move token tests`() {
        val issuedToken = GBP issuedBy ISSUER.party
        transaction {
            // Start with a basic move which moves 10 tokens in entirety from ALICE to BOB.
            input(FungibleTokenContract.contractId, 10 of issuedToken heldBy ALICE.party)
            output(FungibleTokenContract.contractId, 10 of issuedToken heldBy BOB.party)
            //move command with indicies
            command(ALICE.publicKey, MoveTokenCommand(issuedToken, inputs = listOf(0), outputs = listOf(0)))

            // Add the move command, signed by ALICE.
            tweak {
                verifies()
            }

            // Move coupled with an issue.
            tweak {
                output(FungibleTokenContract.contractId, 10.ofType(USD) issuedBy BOB.party heldBy ALICE.party)
                //the issue token is added after the move tokens, so it will have index(1)
                command(BOB.publicKey, IssueTokenCommand(USD issuedBy BOB.party, outputs = listOf(1)))

                verifies()
            }

            // Input missing.
            tweak {
                output(FungibleTokenContract.contractId, 10.ofType(USD) issuedBy BOB.party heldBy BOB.party)
                command(ALICE.publicKey, MoveTokenCommand(USD issuedBy BOB.party, outputs = listOf(1)))

                this `fails with` "When moving tokens, there must be input states present."
            }

            // Output missing.
            tweak {
                input(FungibleTokenContract.contractId, 10.ofType(USD) issuedBy BOB.party heldBy ALICE.party)
                command(ALICE.publicKey, MoveTokenCommand(USD issuedBy BOB.party, inputs = listOf(1)))

                this `fails with` "When moving tokens, there must be output states present."
            }

            // Inputs sum to zero.
            tweak {
                input(FungibleTokenContract.contractId, 0.ofType(USD) issuedBy BOB.party heldBy ALICE.party)
                input(FungibleTokenContract.contractId, 0.ofType(USD) issuedBy BOB.party heldBy ALICE.party)
                output(FungibleTokenContract.contractId, 10.ofType(USD) issuedBy BOB.party heldBy BOB.party)
                command(ALICE.publicKey, MoveTokenCommand(USD issuedBy BOB.party, inputs = listOf(1, 2), outputs = listOf(1)))
                // Command for the move.
                this `fails with` "In move groups there must be an amount of input tokens > ZERO."
            }

            // Outputs sum to zero.
            tweak {
                input(FungibleTokenContract.contractId, 10.ofType(USD) issuedBy BOB.party heldBy ALICE.party)
                output(FungibleTokenContract.contractId, 0.ofType(USD) issuedBy BOB.party heldBy BOB.party)
                output(FungibleTokenContract.contractId, 0.ofType(USD) issuedBy BOB.party heldBy BOB.party)
                command(ALICE.publicKey, MoveTokenCommand(USD issuedBy BOB.party, inputs = listOf(1), outputs = listOf(1, 2)))
                // Command for the move.
                this `fails with` "In move groups there must be an amount of output tokens > ZERO."
            }

            // Unbalanced move.
            tweak {
                input(FungibleTokenContract.contractId, 10.ofType(USD) issuedBy BOB.party heldBy ALICE.party)
                output(FungibleTokenContract.contractId, 11.ofType(USD) issuedBy BOB.party heldBy BOB.party)
                command(ALICE.publicKey, MoveTokenCommand(USD issuedBy BOB.party, inputs = listOf(1), outputs = listOf(1)))
                // Command for the move.
                this `fails with` "In move groups the amount of input tokens MUST EQUAL the amount of output tokens. " +
                        "In other words, you cannot create or destroy value when moving tokens."
            }

            tweak {
                input(FungibleTokenContract.contractId, 10.ofType(USD) issuedBy BOB.party heldBy ALICE.party)
                output(FungibleTokenContract.contractId, 10.ofType(USD) issuedBy BOB.party heldBy BOB.party)
                output(FungibleTokenContract.contractId, 0.ofType(USD) issuedBy BOB.party heldBy BOB.party)
                command(ALICE.publicKey, MoveTokenCommand(USD issuedBy BOB.party, inputs = listOf(1), outputs = listOf(1, 2)))
                // Command for the move.
                this `fails with` "You cannot create output token amounts with a ZERO amount."
            }

            // Two moves (two different groups).
            tweak {
                input(FungibleTokenContract.contractId, 10.ofType(USD) issuedBy BOB.party heldBy ALICE.party)
                output(FungibleTokenContract.contractId, 10.ofType(USD) issuedBy BOB.party heldBy BOB.party)
                command(ALICE.publicKey, MoveTokenCommand(USD issuedBy BOB.party, inputs = listOf(1), outputs = listOf(1)))
                // Command for the move.
                verifies()
            }

            // Two moves (one group).
            tweak {
                input(FungibleTokenContract.contractId, 20 of GBP issuedBy CHARLIE.party heldBy CHARLIE.party)
                output(FungibleTokenContract.contractId, 20 of GBP issuedBy CHARLIE.party heldBy DAENERYS.party)

                input(FungibleTokenContract.contractId, 20 of RUB issuedBy CHARLIE.party heldBy CHARLIE.party)
                output(FungibleTokenContract.contractId, 10 of RUB issuedBy CHARLIE.party heldBy CHARLIE.party)
                output(FungibleTokenContract.contractId, 10 of RUB issuedBy CHARLIE.party heldBy CHARLIE.party)

                attachment(RUB.importAttachment(aliceServices.attachments))

                command(CHARLIE.publicKey, MoveTokenCommand(GBP issuedBy CHARLIE.party, inputs = listOf(1), outputs = listOf(1)))
                command(CHARLIE.publicKey, MoveTokenCommand(RUB issuedBy CHARLIE.party, inputs = listOf(2), outputs = listOf(2, 3)))
                verifies()
            }

            // Wrong public key.
            tweak {
                attachment(RUB.importAttachment(aliceServices.attachments))
                input(FungibleTokenContract.contractId, 20 of RUB issuedBy CHARLIE.party heldBy CHARLIE.party)
                output(FungibleTokenContract.contractId, 20 of RUB issuedBy CHARLIE.party heldBy DAENERYS.party)
                command(BOB.publicKey, MoveTokenCommand(RUB issuedBy CHARLIE.party, inputs = listOf(1), outputs = listOf(1)))
                this `fails with` "Required signers does not contain all the current owners of the tokens being moved"
            }
        }
    }

    @Test(timeout = 300_000)
    fun `should prevent moving of tokens to a subclass`() {
        val issuedToken = RUB issuedBy ISSUER.party
        val amount = 10 of issuedToken
        transaction {
            // Start with a basic move which moves 10 tokens in entirety from ALICE to BOB.
            input(FungibleTokenContract.contractId, FungibleToken(amount, ALICE.party))
            attachment(RUB.importAttachment(aliceServices.attachments))
            tweak {
                output(FungibleTokenContract.contractId, RubleToken(amount, BOB.party))
                command(ALICE.publicKey, MoveTokenCommand(issuedToken, inputs = listOf(0), outputs = listOf(0)))
                this `fails with` ("There is a token group with no assigned command")
            }
            tweak {
                // 10 FT (Alice) -> 10 FT (BOB)
                // 10 RT (BOB) -> 10 RT (ALICE)
                //add an input of the RubleToken owned by BOB
                input(FungibleTokenContract.contractId, RubleToken(amount, BOB.party))

                //add an output of normal FungibleToken owned by BOB
                output(FungibleTokenContract.contractId, FungibleToken(amount, BOB.party))
                //add an output of Ruble owned by Alice
                output(FungibleTokenContract.contractId, RubleToken(amount, ALICE.party))

                command(ALICE.publicKey, MoveTokenCommand(issuedToken, inputs = listOf(0), outputs = listOf(0)))
                command(BOB.publicKey, MoveTokenCommand(issuedToken, inputs = listOf(1), outputs = listOf(1)))
                verifies()
            }
        }
    }

    @Test(timeout = 300_000)
    fun `should prevent moving of tokens to a class controlled by different contract`() {
        val issuedToken = RUB issuedBy ISSUER.party
        val amount = 10 of issuedToken
        transaction {
            // Start with a basic move which moves 10 tokens in entirety from ALICE to BOB.
            input(FungibleTokenContract.contractId, amount heldBy ALICE.party)
            attachment(RUB.importAttachment(aliceServices.attachments))
            tweak {
                output(DodgeTokenContract::class.qualifiedName!!, DodgeToken(amount, BOB.party))
                command(ALICE.publicKey, MoveTokenCommand(issuedToken, inputs = listOf(0), outputs = listOf(0)))
                this `fails with` ("There is a token group with no assigned command")
            }
        }
    }


    @Test(timeout = 300_000)
    fun `should be possible to redeem single token without change output`() {
        val issuedToken = GBP issuedBy ISSUER.party
        transaction {
            // Start with a basic redeem which redeems 10 tokens in entirety from ALICE .
            input(FungibleTokenContract.contractId, 10 of issuedToken heldBy ALICE.party)
            command(listOf(ISSUER.publicKey, ALICE.publicKey), RedeemTokenCommand(issuedToken, inputs = listOf(0)))
            verifies()
        }
    }

    @Test(timeout = 300_000)
    fun `should be possible to redeem single token with change output`() {
        val issuedToken = GBP issuedBy ISSUER.party
        transaction {
            // Start with a basic redeem which redeems 10 tokens in entirety from ALICE .
            input(FungibleTokenContract.contractId, 10 of issuedToken heldBy ALICE.party)
            output(FungibleTokenContract.contractId, 5 of issuedToken heldBy ALICE.party)
            command(listOf(ISSUER.publicKey, ALICE.publicKey), RedeemTokenCommand(issuedToken, inputs = listOf(0), outputs = listOf(0)))
            verifies()
        }
    }

    @Test(timeout = 300_000)
    fun `should not be possible to redeem single token without owner signature`() {
        val issuedToken = GBP issuedBy ISSUER.party
        transaction {
            // Start with a basic redeem which redeems 10 tokens in entirety from ALICE .
            input(FungibleTokenContract.contractId, 10 of issuedToken heldBy ALICE.party)
            command(listOf(ISSUER.publicKey), RedeemTokenCommand(issuedToken, inputs = listOf(0)))
            this `fails with` "Contract verification failed: Owners of redeemed states must be the signing parties."
        }
    }

    @Test(timeout = 300_000)
    fun `should not be possible to redeem single token without issuer signature`() {
        val issuedToken = GBP issuedBy ISSUER.party
        transaction {
            // Start with a basic redeem which redeems 10 tokens in entirety from ALICE .
            input(FungibleTokenContract.contractId, 10 of issuedToken heldBy ALICE.party)
            command(listOf(ALICE.publicKey), RedeemTokenCommand(issuedToken, inputs = listOf(0)))
            this `fails with` "The issuer must be the signing party when an amount of tokens are redeemed."
        }
    }

    @Test(timeout = 300_000)
    fun `should not be possible to redeem multiple tokens with different issuers without all issuer signatures`() {
        val issuedToken = GBP issuedBy ISSUER.party
        transaction {
            // Start with a basic redeem which redeems 10 tokens in entirety from ALICE .
            input(FungibleTokenContract.contractId, 10 of issuedToken heldBy ALICE.party)
            input(FungibleTokenContract.contractId, 10 of GBP issuedBy BOB.party heldBy ALICE.party)
            command(listOf(ALICE.publicKey, ISSUER.publicKey), RedeemTokenCommand(issuedToken, inputs = listOf(0)))
            command(listOf(ALICE.publicKey, ISSUER.publicKey), RedeemTokenCommand(GBP issuedBy BOB.party, inputs = listOf(1)))
            this `fails with` "The issuer must be the signing party when an amount of tokens are redeemed."
        }
    }

    @Test(timeout = 300_000)
    fun `should fail to redeem token group if an unmatched group is also provided`() {
        val issuedToken = GBP issuedBy ISSUER.party
        transaction {
            // Start with a basic redeem which redeems 10 tokens in entirety from ALICE .
            input(FungibleTokenContract.contractId, 10 of issuedToken heldBy ALICE.party)
            input(FungibleTokenContract.contractId, 10 of GBP issuedBy BOB.party heldBy ALICE.party)
            command(listOf(ALICE.publicKey, ISSUER.publicKey), RedeemTokenCommand(issuedToken, inputs = listOf(0)))
            this `fails with` "There is a token group with no assigned command!"
        }
    }


    @Test(timeout = 300_000)
    fun `should allow redemption of two separate groups`() {
        val issuedToken = GBP issuedBy ISSUER.party
        transaction {
            // Start with a basic redeem which redeems 10 tokens in entirety from ALICE .
            input(FungibleTokenContract.contractId, 10 of issuedToken heldBy ALICE.party)
            input(FungibleTokenContract.contractId, 10 of GBP issuedBy BOB.party heldBy ALICE.party)
            command(listOf(ALICE.publicKey, ISSUER.publicKey), RedeemTokenCommand(issuedToken, inputs = listOf(0)))
            command(listOf(ALICE.publicKey, BOB.publicKey), RedeemTokenCommand(GBP issuedBy BOB.party, inputs = listOf(1)))
            verifies()
        }
    }

    @Test(timeout = 300_000)
    fun `should allow redemption with change to different owner`() {
        val issuedToken = GBP issuedBy ISSUER.party
        transaction {
            // Start with a basic redeem which redeems 10 tokens in entirety from ALICE .
            input(FungibleTokenContract.contractId, 10 of issuedToken heldBy ALICE.party)
            output(FungibleTokenContract.contractId, 5 of issuedToken heldBy BOB.party)
            command(listOf(ISSUER.publicKey, ALICE.publicKey), RedeemTokenCommand(issuedToken, inputs = listOf(0), outputs = listOf(0)))
            verifies()
        }
    }

    @Test(timeout = 300_000)
    fun `should not allow redemption if change is equal to input`() {
        val issuedToken = GBP issuedBy ISSUER.party
        transaction {
            // Start with a basic redeem which redeems 10 tokens in entirety from ALICE .
            input(FungibleTokenContract.contractId, 10 of issuedToken heldBy ALICE.party)
            output(FungibleTokenContract.contractId, 10 of issuedToken heldBy BOB.party)
            command(listOf(ISSUER.publicKey, ALICE.publicKey), RedeemTokenCommand(issuedToken, inputs = listOf(0), outputs = listOf(0)))
            this `fails with` "Change shouldn't exceed amount redeemed"
        }
    }

    @Test(timeout = 300_000)
    fun `should not allow redemption if change is greater than input`() {
        val issuedToken = GBP issuedBy ISSUER.party
        transaction {
            // Start with a basic redeem which redeems 10 tokens in entirety from ALICE .
            input(FungibleTokenContract.contractId, 10 of issuedToken heldBy ALICE.party)
            output(FungibleTokenContract.contractId, 11 of issuedToken heldBy BOB.party)
            command(listOf(ISSUER.publicKey, ALICE.publicKey), RedeemTokenCommand(issuedToken, inputs = listOf(0), outputs = listOf(0)))
            this `fails with` "Change shouldn't exceed amount redeemed"
        }
    }

    @Test(timeout = 300_000)
    fun `should not allow redemption if change is zero`() {
        val issuedToken = GBP issuedBy ISSUER.party
        transaction {
            // Start with a basic redeem which redeems 10 tokens in entirety from ALICE .
            input(FungibleTokenContract.contractId, 10 of issuedToken heldBy ALICE.party)
            output(FungibleTokenContract.contractId, 0 of issuedToken heldBy BOB.party)
            command(listOf(ISSUER.publicKey, ALICE.publicKey), RedeemTokenCommand(issuedToken, inputs = listOf(0), outputs = listOf(0)))
            this `fails with` "If there is an output, it must have a value greater than zero"
        }
    }

    @Test(timeout = 300_000)
    fun `should not allow redemption if more than one change output is present`() {
        val issuedToken = GBP issuedBy ISSUER.party
        transaction {
            // Start with a basic redeem which redeems 10 tokens in entirety from ALICE .
            input(FungibleTokenContract.contractId, 10 of issuedToken heldBy ALICE.party)
            output(FungibleTokenContract.contractId, 1 of issuedToken heldBy BOB.party)
            output(FungibleTokenContract.contractId, 9 of issuedToken heldBy BOB.party)
            command(listOf(ISSUER.publicKey, ALICE.publicKey), RedeemTokenCommand(issuedToken, inputs = listOf(0), outputs = listOf(0, 1)))
            this `fails with` "When redeeming tokens, there must be zero or one output state"
        }
    }

    @Test(timeout = 300_000)
    fun `should not allow redemption if no input states present`() {
        val issuedToken = GBP issuedBy ISSUER.party
        transaction {
            // Start with a basic redeem which redeems 10 tokens in entirety from ALICE .
            output(FungibleTokenContract.contractId, 1 of issuedToken heldBy BOB.party)
            command(listOf(ISSUER.publicKey, ALICE.publicKey), RedeemTokenCommand(issuedToken, outputs = listOf(0)))
            this `fails with` " When redeeming tokens, there must be input states present"
        }
    }

    @Test(timeout = 300_000)
    fun `should not allow redemption if zero valued inputs`() {
        val issuedToken = GBP issuedBy ISSUER.party
        transaction {
            // Start with a basic redeem which redeems 10 tokens in entirety from ALICE .
            input(FungibleTokenContract.contractId, 0 of issuedToken heldBy ALICE.party)
            command(listOf(ISSUER.publicKey, ALICE.publicKey), RedeemTokenCommand(issuedToken, inputs = listOf(0)))
            this `fails with` " When redeeming tokens an amount > ZERO must be redeemed"
        }
    }

    @Test(timeout = 300_000)
    fun `should enforce the presence of the token type jar`() {
        val issuedToken = RUB issuedBy ISSUER.party
        transaction {
            output(FungibleTokenContract.contractId, 10 of issuedToken heldBy BOB.party)
            command(ISSUER.publicKey, IssueTokenCommand(issuedToken, outputs = listOf(0)))
            this.failsWith("Expected to find type jar:")
            tweak {
                attachment(RUB.importAttachment(aliceServices.attachments))
                verifies()
            }
        }
    }

    @Test(timeout = 300_000)
    fun `should enforce that the hash providing the token type cannot change during a transaction`() {
        val issuedToken = GBP issuedBy ISSUER.party
        transaction {
            input(FungibleTokenContract.contractId, 10 of issuedToken heldBy BOB.party)
            output(FungibleTokenContract.contractId, FungibleToken(10 of issuedToken, BOB.party, RUB.getAttachmentIdForGenericParam()))
            command(BOB.party.owningKey, MoveTokenCommand(issuedToken, inputs = listOf(0), outputs = listOf(0)))

            attachment(RUB.importAttachment(aliceServices.attachments))

            this.failsWith("There must be exactly one Jar (Hash) providing extended TokenType: GBP")
        }
    }

    @Test(timeout = 300_000)
    fun `should enforce that the jar providing a tokentype cannot be null for non-sdk types`() {
        val issuedToken = RUB issuedBy ISSUER.party
        transaction {
            input(FungibleTokenContract.contractId, FungibleToken(10 of issuedToken, BOB.party, null))
            output(FungibleTokenContract.contractId, FungibleToken(10 of issuedToken, ALICE.party, null))
            command(BOB.party.owningKey, MoveTokenCommand(issuedToken, inputs = listOf(0), outputs = listOf(0)))

            this.failsWith("no jarHash has been provided to pin the providing jar")
        }
    }
}
