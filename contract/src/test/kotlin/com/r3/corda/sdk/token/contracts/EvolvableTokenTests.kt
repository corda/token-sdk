package com.r3.corda.sdk.token.contracts

import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.whenever
import com.r3.corda.sdk.token.contracts.commands.Create
import com.r3.corda.sdk.token.contracts.commands.Update
import com.r3.corda.sdk.token.contracts.samples.TestEvolvableTokenContract
import net.corda.core.identity.CordaX500Name
import net.corda.core.node.NotaryInfo
import net.corda.node.services.api.IdentityServiceInternal
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.core.DUMMY_NOTARY_NAME
import net.corda.testing.core.SerializationEnvironmentRule
import net.corda.testing.core.TestIdentity
import net.corda.testing.dsl.EnforceVerifyOrFail
import net.corda.testing.dsl.TransactionDSL
import net.corda.testing.dsl.TransactionDSLInterpreter
import net.corda.testing.node.MockServices
import net.corda.testing.node.transaction
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test

class EvolvableTokenTests {

    private companion object {
        val NOTARY = TestIdentity(DUMMY_NOTARY_NAME, 20)
        val ALICE = TestIdentity(CordaX500Name("ALICE", "London", "GB"))
        val BOB = TestIdentity(CordaX500Name("BOB", "London", "GB"))
        val CHARLES = TestIdentity(CordaX500Name("CHARLES", "London", "GB"))
        val DENISE = TestIdentity(CordaX500Name("DENISE", "London", "GB"))
    }

    @Rule
    @JvmField
    val testSerialization = SerializationEnvironmentRule()

    private val aliceServices = MockServices(
            cordappPackages = listOf("com.r3.corda.sdk.token.contracts", "com.r3.corda.sdk.token.money"),
            initialIdentity = ALICE,
            identityService = mock<IdentityServiceInternal>().also {
                doReturn(ALICE.party).whenever(it).partyFromKey(ALICE.publicKey)
                doReturn(BOB.party).whenever(it).partyFromKey(BOB.publicKey)
                doReturn(CHARLES.party).whenever(it).partyFromKey(CHARLES.publicKey)
                doReturn(DENISE.party).whenever(it).partyFromKey(DENISE.publicKey)
            },
            networkParameters = testNetworkParameters(
                    minimumPlatformVersion = 4,
                    notaries = listOf(NotaryInfo(NOTARY.party, false))
            )
    )

    private fun transaction(script: TransactionDSL<TransactionDSLInterpreter>.() -> EnforceVerifyOrFail) {
        aliceServices.transaction(NOTARY.party, script)
    }

    @Test
    fun `allow only one command`() {
        val expectedError = "A transaction must contain at least one command"
        val token = TestEvolvableTokenContract.TestEvolvableTokenType(maintainers, participants)

        // With 1 maintainer
        transaction {
            // Standard transaction details
            output(
                    TestEvolvableTokenContract.ID,
                    TestEvolvableTokenContract.TestEvolvableTokenType(maintainers, participants)
            )

            // With correct command signed by maintainer
            tweak {
                command(ALICE.publicKey, Create())
                verifies()
            }

            // With correct command signed by maintainer
            tweak {
                command(ALICE.publicKey, Create())
                verifies()
            }

        }
    }

    @Test
    fun `create with 1 maintainer`() {
        val maintainers = listOf(ALICE.identity.party)
        val participants = maintainers
        val token = TestEvolvableTokenContract.TestEvolvableTokenType(maintainers, participants)
        transaction {
            output(TestEvolvableTokenContract.ID, token)

            // With correct command signed by maintainer
            tweak {
                command(ALICE.publicKey, Create())
                verifies()
            }

            // Without a command
            tweak {
                failsWith("A transaction must contain at least one command")
            }

            // With command signed by another party
            tweak {
                command(BOB.publicKey, Create())
                failsWith("All evolvable token maintainers must sign the create evolvable token transaction.")
            }

            // With command signed by maintainer and another party
            tweak {
                command(listOf(ALICE.publicKey, BOB.publicKey), Create())
                failsWith("Only evolvable token maintainers may sign the create evolvable token transaction.")
            }

            // With two CREATE commands
            tweak {
                command(ALICE.publicKey, Create())
                command(BOB.publicKey, Create())
                failsWith("A transaction must contain at least one command")
            }

            // With a CREATE and UPDATE command
            tweak {
                command(ALICE.publicKey, Create())
                command(ALICE.publicKey, Update())
                failsWith("A transaction must contain at least one command")
            }

            // With an input state
            tweak {
                input(TestEvolvableTokenContract.ID, token)
                command(ALICE.publicKey, Create())
                failsWith("Create evolvable token transactions must not contain any inputs.")
            }

            // With two output states
            tweak {
                output(TestEvolvableTokenContract.ID, token)
                command(ALICE.publicKey, Create())
                failsWith("Create evolvable token transactions must contain exactly one output.")
            }
        }
    }

    @Test
    fun `create with 2 maintainers`() {
        val maintainers = listOf(ALICE.party, BOB.party)
        val participants = maintainers
        val token = TestEvolvableTokenContract.TestEvolvableTokenType(maintainers, participants)
        transaction {
            output(TestEvolvableTokenContract.ID, token)

            // With correct command signed by both maintainers
            tweak {
                command(listOf(ALICE.publicKey, BOB.publicKey), Create())
                verifies()
            }

            // Without a command
            tweak {
                failsWith("A transaction must contain at least one command")
            }

            // With command signed by another party
            tweak {
                command(CHARLES.publicKey, Create())
                failsWith("All evolvable token maintainers must sign the create evolvable token transaction.")
            }

            // With command signed by maintainer and another party
            tweak {
                command(listOf(ALICE.publicKey, CHARLES.publicKey), Create())
                failsWith("All evolvable token maintainers must sign the create evolvable token transaction.")
            }

            // With command signed by both maintainers and another party
            tweak {
                command(listOf(ALICE.publicKey, BOB.publicKey, CHARLES.publicKey), Create())
                failsWith("Only evolvable token maintainers may sign the create evolvable token transaction.")
            }
        }
    }

    @Test
    fun `create with 1 maintainer and 1 additional participant`() {
        val maintainers = listOf(ALICE.party)
        val participants = maintainers + listOf(CHARLES.party)
        val token = TestEvolvableTokenContract.TestEvolvableTokenType(maintainers, participants)
        transaction {
            output(TestEvolvableTokenContract.ID, token)

            // With correct command signed by both maintainers
            tweak {
                command(ALICE.publicKey, Create())
                verifies()
            }

            // With command signed by another party
            tweak {
                command(DENISE.publicKey, Create())
                failsWith("All evolvable token maintainers must sign the create evolvable token transaction.")
            }

            // With command signed by participant party
            tweak {
                command(CHARLES.publicKey, Create())
                failsWith("All evolvable token maintainers must sign the create evolvable token transaction.")
            }

            // With command signed by maintainer and participant party
            tweak {
                command(listOf(ALICE.publicKey, CHARLES.publicKey), Create())
                failsWith("All evolvable token maintainers must sign the create evolvable token transaction.")
            }
        }
    }

    @Test
    fun `create with 2 maintainers and 1 additional participant`() {
        val maintainers = listOf(ALICE.party, BOB.party)
        val participants = maintainers + listOf(CHARLES.party)
        val token = TestEvolvableTokenContract.TestEvolvableTokenType(maintainers, participants)
        transaction {
            output(TestEvolvableTokenContract.ID, token)

            // With correct command signed by both maintainers
            tweak {
                command(listOf(ALICE.publicKey, BOB.publicKey), Create())
                verifies()
            }

            // With command signed by another party
            tweak {
                command(DENISE.publicKey, Create())
                failsWith("All evolvable token maintainers must sign the create evolvable token transaction.")
            }

            // With command signed by participant party
            tweak {
                command(CHARLES.publicKey, Create())
                failsWith("All evolvable token maintainers must sign the create evolvable token transaction.")
            }

            // With command signed by 1 maintainer and participant party
            tweak {
                command(listOf(ALICE.publicKey, CHARLES.publicKey), Create())
                failsWith("All evolvable token maintainers must sign the create evolvable token transaction.")
            }

            // With command signed by both maintainers and participant party
            tweak {
                command(listOf(ALICE.publicKey, BOB.publicKey, CHARLES.publicKey), Create())
                failsWith("Only evolvable token maintainers may sign the create evolvable token transaction.")
            }
        }
    }

    @Test
    fun `create with 2 maintainers and 2 additional participants`() {
        val maintainers = listOf(ALICE.party, BOB.party)
        val participants = maintainers + listOf(CHARLES.party, DENISE.party)
        val token = TestEvolvableTokenContract.TestEvolvableTokenType(maintainers, participants)
        transaction {
            output(TestEvolvableTokenContract.ID, token)

            // With correct command signed by both maintainers
            tweak {
                command(listOf(ALICE.publicKey, BOB.publicKey), Create())
                verifies()
            }

            // With command signed by participant parties
            tweak {
                command(listOf(CHARLES.publicKey, DENISE.publicKey), Create())
                failsWith("All evolvable token maintainers must sign the create evolvable token transaction.")
            }

            // With command signed by 1 maintainer and both participant parties
            tweak {
                command(listOf(ALICE.publicKey, CHARLES.publicKey, DENISE.publicKey), Create())
                failsWith("All evolvable token maintainers must sign the create evolvable token transaction.")
            }

            // With command signed by both maintainers and both participants
            tweak {
                command(listOf(ALICE.publicKey, BOB.publicKey, CHARLES.publicKey, DENISE.publicKey), Create())
                failsWith("Only evolvable token maintainers may sign the create evolvable token transaction.")
            }
        }
    }

    @Test
    fun `create with 1 maintainer and 1 participant`() {
        val maintainers = listOf(ALICE.party)
        val participants = listOf(DENISE.party)
        val token = TestEvolvableTokenContract.TestEvolvableTokenType(maintainers, participants)
        transaction {
            output(TestEvolvableTokenContract.ID, token)
            command(ALICE.publicKey, Create())
            failsWith("All evolvable token maintainers must also be participants.")
        }
    }

    @Test @Ignore
    fun `update evolvable token tests`() {
    }

}