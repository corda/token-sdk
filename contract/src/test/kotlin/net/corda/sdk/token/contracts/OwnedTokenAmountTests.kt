package net.corda.sdk.token.contracts

import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.whenever
import net.corda.core.contracts.TypeOnlyCommandData
import net.corda.core.identity.CordaX500Name
import net.corda.core.node.NotaryInfo
import net.corda.node.services.api.IdentityServiceInternal
import net.corda.sdk.token.contracts.commands.Issue
import net.corda.sdk.token.contracts.commands.Move
import net.corda.sdk.token.contracts.commands.Redeem
import net.corda.sdk.token.contracts.utilities.issuedBy
import net.corda.sdk.token.contracts.utilities.of
import net.corda.sdk.token.contracts.utilities.ownedBy
import net.corda.sdk.token.money.GBP
import net.corda.sdk.token.money.USD
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.core.DUMMY_NOTARY_NAME
import net.corda.testing.core.SerializationEnvironmentRule
import net.corda.testing.core.TestIdentity
import net.corda.testing.dsl.EnforceVerifyOrFail
import net.corda.testing.dsl.TransactionDSL
import net.corda.testing.dsl.TransactionDSLInterpreter
import net.corda.testing.node.MockServices
import net.corda.testing.node.transaction
import org.junit.Rule
import org.junit.Test

class OwnedTokenAmountTests {

    private companion object {
        val NOTARY = TestIdentity(DUMMY_NOTARY_NAME, 20)
        val ISSUER = TestIdentity(CordaX500Name("ISSUER", "London", "GB"))
        val ALICE = TestIdentity(CordaX500Name("ALICE", "London", "GB"))
        val BOB = TestIdentity(CordaX500Name("BOB", "London", "GB"))
    }

    @Rule
    @JvmField
    val testSerialization = SerializationEnvironmentRule()

    private val aliceServices = MockServices(
            cordappPackages = listOf("net.corda.sdk.token"),
            initialIdentity = ALICE,
            identityService = mock<IdentityServiceInternal>().also {
                doReturn(ALICE.party).whenever(it).partyFromKey(ALICE.publicKey)
                doReturn(BOB.party).whenever(it).partyFromKey(BOB.publicKey)
                doReturn(ISSUER.party).whenever(it).partyFromKey(ISSUER.publicKey)
            },
            networkParameters = testNetworkParameters(minimumPlatformVersion = 4, notaries = listOf(NotaryInfo(NOTARY.party, false)))
    )

    private fun transaction(script: TransactionDSL<TransactionDSLInterpreter>.() -> EnforceVerifyOrFail) {
        MockServices(ALICE).transaction(NOTARY.party, script)
    }

    class WrongCommand : TypeOnlyCommandData()

    /**
     * This is likely the most common issuance transaction we'll see.
     */
    @Test
    fun `issue token tests`() {
        val issuedToken = GBP issuedBy ISSUER.party
        transaction {
            // Start with only one output.
            output(OwnedTokenAmountContract.contractId, 10 of issuedToken ownedBy ALICE.party)
            // No command fails.
            tweak {
                this `fails with` "A transaction must contain at least one command"
            }
            // Signed by a party other than the issuer.
            tweak {
                command(BOB.publicKey, Issue(issuedToken))
                this `fails with` "The issuer must be the only signing party when an amount of tokens are issued."
            }
            // Non issuer signature present.
            tweak {
                command(listOf(BOB.publicKey, BOB.publicKey), Issue(issuedToken))
                this `fails with` "The issuer must be the only signing party when an amount of tokens are issued."
            }
            // With an incorrect command.
            tweak {
                command(BOB.publicKey, WrongCommand())
                this `fails with` "There must be at least one owned token command this transaction."
            }
            // With different command types for one group.
            tweak {
                command(ISSUER.publicKey, Issue(issuedToken))
                command(ISSUER.publicKey, Move(issuedToken))
                this `fails with` "There must be exactly one OwnedTokenCommand type per group! For example: You cannot " +
                        "map an Issue AND a Move command to one group of tokens in a transaction."
            }
            // Includes a group with no assigned command.
            tweak {
                output(OwnedTokenAmountContract.contractId, 10.USD issuedBy ISSUER.party ownedBy ALICE.party)
                command(ISSUER.publicKey, Issue(issuedToken))
                this `fails with` "There is a token group with no assigned command!"
            }
            // With a zero amount in another group.
            tweak {
                val otherToken = USD issuedBy ISSUER.party
                output(OwnedTokenAmountContract.contractId, 0 of otherToken ownedBy ALICE.party)
                command(ISSUER.publicKey, Issue(issuedToken))
                command(ISSUER.publicKey, Issue(otherToken))
                this `fails with` "When issuing tokens an amount > ZERO must be issued."
            }
            // With some input states.
            tweak {
                input(OwnedTokenAmountContract.contractId, 10 of issuedToken ownedBy ALICE.party)
                command(ISSUER.publicKey, Issue(issuedToken))
                this `fails with` "When issuing tokens, there cannot be any input states."
            }
            // Includes a zero output.
            tweak {
                output(OwnedTokenAmountContract.contractId, 0 of issuedToken ownedBy ALICE.party)
                command(ISSUER.publicKey, Issue(issuedToken))
                this `fails with` "You cannot issue tokens with a zero amount."
            }
            // Includes another token type and a matching command.
            tweak {
                val otherToken = USD issuedBy ISSUER.party
                output(OwnedTokenAmountContract.contractId, 10 of otherToken ownedBy ALICE.party)
                command(ISSUER.publicKey, Issue(issuedToken))
                command(ISSUER.publicKey, Issue(otherToken))
                verifies()
            }
            // Includes more output states of the same token type.
            tweak {
                output(OwnedTokenAmountContract.contractId, 10 of issuedToken ownedBy ALICE.party)
                output(OwnedTokenAmountContract.contractId, 100 of issuedToken ownedBy ALICE.party)
                output(OwnedTokenAmountContract.contractId, 1000 of issuedToken ownedBy ALICE.party)
                command(ISSUER.publicKey, Issue(issuedToken))
                verifies()
            }
            // Includes the same token issued by a different issuer.
            // You wouldn't usually do this but it is possible.
            tweak {
                output(OwnedTokenAmountContract.contractId, 1.GBP issuedBy BOB.party ownedBy ALICE.party)
                command(ISSUER.publicKey, Issue(issuedToken))
                command(BOB.publicKey, Issue(GBP issuedBy BOB.party))
                verifies()
            }
            // With the correct command and signed by the issuer.
            tweak {
                command(ISSUER.publicKey, Issue(issuedToken))
                verifies()
            }
        }
    }

    @Test
    fun `move token tests`() {
        val issuedToken = GBP issuedBy ISSUER.party
        transaction {
            // Start with a basic move which moves 10 tokens in entirety from ALICE to BOB.
            input(OwnedTokenAmountContract.contractId, 10 of issuedToken ownedBy ALICE.party)
            output(OwnedTokenAmountContract.contractId, 10 of issuedToken ownedBy BOB.party)

            // Add the move command, signed by ALICE.
            tweak {
                command(ALICE.publicKey, Move(issuedToken))
                verifies()
            }

            // Move coupled with an issue.
            tweak {
                output(OwnedTokenAmountContract.contractId, 10.USD issuedBy BOB.party ownedBy ALICE.party)
                command(BOB.publicKey, Issue(USD issuedBy BOB.party))
                // Command for the move.
                command(ALICE.publicKey, Move(issuedToken))
                verifies()
            }

            // Input missing.
            tweak {
                output(OwnedTokenAmountContract.contractId, 10.USD issuedBy BOB.party ownedBy BOB.party)
                command(ALICE.publicKey, Move(USD issuedBy BOB.party))
                // Command for the move.
                command(ALICE.publicKey, Move(issuedToken))
                this `fails with` "When moving tokens, there must be input states present."
            }

            // Output missing.
            tweak {
                input(OwnedTokenAmountContract.contractId, 10.USD issuedBy BOB.party ownedBy ALICE.party)
                command(ALICE.publicKey, Move(USD issuedBy BOB.party))
                // Command for the move.
                command(ALICE.publicKey, Move(issuedToken))
                this `fails with` "When moving tokens, there must be output states present."
            }

            // Inputs sum to zero.
            tweak {
                input(OwnedTokenAmountContract.contractId, 0.USD issuedBy BOB.party ownedBy ALICE.party)
                input(OwnedTokenAmountContract.contractId, 0.USD issuedBy BOB.party ownedBy ALICE.party)
                output(OwnedTokenAmountContract.contractId, 10.USD issuedBy BOB.party ownedBy BOB.party)
                command(ALICE.publicKey, Move(USD issuedBy BOB.party))
                // Command for the move.
                command(ALICE.publicKey, Move(issuedToken))
                this `fails with` "In move groups there must be an amount of input tokens > ZERO."
            }

            // Outputs sum to zero.
            tweak {
                input(OwnedTokenAmountContract.contractId, 10.USD issuedBy BOB.party ownedBy ALICE.party)
                output(OwnedTokenAmountContract.contractId, 0.USD issuedBy BOB.party ownedBy BOB.party)
                output(OwnedTokenAmountContract.contractId, 0.USD issuedBy BOB.party ownedBy BOB.party)
                command(ALICE.publicKey, Move(USD issuedBy BOB.party))
                // Command for the move.
                command(ALICE.publicKey, Move(issuedToken))
                this `fails with` "In move groups there must be an amount of output tokens > ZERO."
            }

            // Unbalanced move.
            tweak {
                input(OwnedTokenAmountContract.contractId, 10.USD issuedBy BOB.party ownedBy ALICE.party)
                output(OwnedTokenAmountContract.contractId, 11.USD issuedBy BOB.party ownedBy BOB.party)
                command(ALICE.publicKey, Move(USD issuedBy BOB.party))
                // Command for the move.
                command(ALICE.publicKey, Move(issuedToken))
                this `fails with` "In move groups the amount of input tokens MUST EQUAL the amount of output tokens. " +
                        "In other words, you cannot create or destroy value when moving tokens."
            }

            tweak {
                input(OwnedTokenAmountContract.contractId, 10.USD issuedBy BOB.party ownedBy ALICE.party)
                output(OwnedTokenAmountContract.contractId, 10.USD issuedBy BOB.party ownedBy BOB.party)
                output(OwnedTokenAmountContract.contractId, 0.USD issuedBy BOB.party ownedBy BOB.party)
                command(ALICE.publicKey, Move(USD issuedBy BOB.party))
                // Command for the move.
                command(ALICE.publicKey, Move(issuedToken))
                this `fails with` "You cannot create output token amounts with a ZERO amount."
            }

            // Two moves (two different groups).
            tweak {
                input(OwnedTokenAmountContract.contractId, 10.USD issuedBy BOB.party ownedBy ALICE.party)
                output(OwnedTokenAmountContract.contractId, 10.USD issuedBy BOB.party ownedBy BOB.party)
                command(ALICE.publicKey, Move(USD issuedBy BOB.party))
                // Command for the move.
                command(ALICE.publicKey, Move(issuedToken))
                verifies()
            }

            // Wrong public key.
            tweak {
                command(BOB.publicKey, Move(issuedToken))
                this `fails with` "There are required signers missing or some of the specified signers are not " +
                        "required. A transaction to move owned token amounts must be signed by ONLY ALL the owners " +
                        "of ALL the input owned token amounts."
            }

            // Includes an incorrect public with the correct key still being present.
            tweak {
                command(listOf(BOB.publicKey, ALICE.publicKey), Move(issuedToken))
                this `fails with` "There are required signers missing or some of the specified signers are not " +
                        "required. A transaction to move owned token amounts must be signed by ONLY ALL the owners " +
                        "of ALL the input owned token amounts."
            }

        }
    }

    @Test
    fun `redeem token tests`() {
        val issuedToken = GBP issuedBy ISSUER.party
        transaction {
            // Start with a basic redeem which redeems 10 tokens in entirety from ALICE .
            input(OwnedTokenAmountContract.contractId, 10 of issuedToken ownedBy ALICE.party)

            // Add the redeem command, signed by the ISSUER.
            tweak {
                command(ISSUER.publicKey, Redeem(issuedToken))
                verifies()
            }

            // TODO: Write more test cases.

        }
    }

}