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

            // TODO: Write more test cases.

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