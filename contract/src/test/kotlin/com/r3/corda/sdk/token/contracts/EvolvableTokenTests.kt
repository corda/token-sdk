package com.r3.corda.sdk.token.contracts

import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.whenever
import com.r3.corda.sdk.token.contracts.commands.Create
import com.r3.corda.sdk.token.contracts.commands.Update
import net.corda.core.contracts.UniqueIdentifier
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
import org.junit.Rule
import org.junit.Test

object EvolvableTokenTests {

    val NOTARY = TestIdentity(DUMMY_NOTARY_NAME, 20)
    val ALICE = TestIdentity(CordaX500Name("ALICE", "London", "GB"))
    val BOB = TestIdentity(CordaX500Name("BOB", "London", "GB"))
    val CHARLES = TestIdentity(CordaX500Name("CHARLES", "London", "GB"))
    val DENISE = TestIdentity(CordaX500Name("DENISE", "London", "GB"))

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

    fun transaction(script: TransactionDSL<TransactionDSLInterpreter>.() -> EnforceVerifyOrFail) {
        aliceServices.transaction(NOTARY.party, script)
    }

    class TestEvolvableTokenTypeFactory {

        companion object {
            fun withOneMaintainer(linearId: UniqueIdentifier = UniqueIdentifier(), maintainer: TestIdentity = ALICE): TestEvolvableTokenContract.TestEvolvableTokenType {
                return TestEvolvableTokenContract.TestEvolvableTokenType(
                        maintainers = listOf(maintainer.identity.party),
                        linearId = linearId
                )
            }

            fun withTwoMaintainers(linearId: UniqueIdentifier = UniqueIdentifier(), maintainer1: TestIdentity = ALICE, maintainer2: TestIdentity = BOB): TestEvolvableTokenContract.TestEvolvableTokenType {
                return TestEvolvableTokenContract.TestEvolvableTokenType(
                        maintainers = listOf(maintainer1.identity.party, maintainer2.identity.party),
                        linearId = linearId
                )
            }

            fun withOneMaintainerAndOneObserver(linearId: UniqueIdentifier = UniqueIdentifier(), maintainer: TestIdentity = ALICE, observer: TestIdentity = CHARLES): TestEvolvableTokenContract.TestEvolvableTokenType {
                return TestEvolvableTokenContract.TestEvolvableTokenType(
                        maintainers = listOf(maintainer.identity.party),
                        observers = listOf(observer.identity.party),
                        linearId = linearId
                )
            }

            fun withTwoMaintainersAndTwoObservers(linearId: UniqueIdentifier = UniqueIdentifier(), maintainer1: TestIdentity = ALICE, maintainer2: TestIdentity = BOB, observer1: TestIdentity = CHARLES, observer2: TestIdentity = DENISE): TestEvolvableTokenContract.TestEvolvableTokenType {
                return TestEvolvableTokenContract.TestEvolvableTokenType(
                        maintainers = listOf(maintainer1.identity.party, maintainer2.identity.party),
                        observers = listOf(observer1.identity.party, observer2.identity.party),
                        linearId = linearId
                )
            }

            fun withDifferingMaintainersAndParticipants(linearId: UniqueIdentifier = UniqueIdentifier(), maintainer: TestIdentity = ALICE, participant: TestIdentity = CHARLES): TestEvolvableTokenContract.TestEvolvableTokenType {
                return TestEvolvableTokenContract.TestEvolvableTokenType(
                        maintainers = listOf(maintainer.identity.party),
                        participants = listOf(participant.identity.party),
                        linearId = linearId
                )
            }
        }
    }

    class OnCreateTests {

        @Test
        fun `valid transactions`() {
            // With 1 maintainer
            transaction {
                output(TestEvolvableTokenContract.ID, TestEvolvableTokenTypeFactory.withOneMaintainer())
                command(ALICE.publicKey, Create())
                verifies()
            }

            // With 2 maintainers
            transaction {
                output(TestEvolvableTokenContract.ID, TestEvolvableTokenTypeFactory.withTwoMaintainers())
                command(listOf(ALICE.publicKey, BOB.publicKey), Create())
                verifies()
            }

            // With 1 maintainer and 1 observer
            transaction {
                output(TestEvolvableTokenContract.ID, TestEvolvableTokenTypeFactory.withOneMaintainerAndOneObserver())
                command(ALICE.publicKey, Create())
                verifies()
            }

            // With 2 maintainers and 2 observers
            transaction {
                output(TestEvolvableTokenContract.ID, TestEvolvableTokenTypeFactory.withTwoMaintainersAndTwoObservers())
                command(listOf(ALICE.publicKey, BOB.publicKey), Create())
                verifies()
            }
        }

        @Test
        fun `all maintainers must be participants`() {
            val expectedError = "All evolvable token maintainers must also be participants."

            transaction {
                output(TestEvolvableTokenContract.ID, TestEvolvableTokenTypeFactory.withDifferingMaintainersAndParticipants())
                command(ALICE.publicKey, Create())
                failsWith(expectedError)
            }
        }

        @Test
        fun `requires one command`() {
            val expectedError = "A transaction must contain at least one command"

            // With 1 maintainer
            transaction {
                output(TestEvolvableTokenContract.ID, TestEvolvableTokenTypeFactory.withOneMaintainer())

                // Fails without a command
                failsWith(expectedError)
            }
        }

        @Test
        fun `must be signed by all maintainers`() {
            val expectedError = "All evolvable token maintainers must sign the create evolvable token transaction."

            // With 1 maintainer
            transaction {
                output(TestEvolvableTokenContract.ID, TestEvolvableTokenTypeFactory.withOneMaintainer())

                // With create command signed by non-participant
                tweak {
                    command(DENISE.publicKey, Create())
                    failsWith(expectedError)
                }
            }

            // With 2 maintainers
            transaction {
                output(TestEvolvableTokenContract.ID, TestEvolvableTokenTypeFactory.withTwoMaintainers())

                // With create command signed by first maintainer only
                tweak {
                    command(ALICE.publicKey, Create())
                    failsWith(expectedError)
                }

                // With create command signed by second maintainer only
                tweak {
                    command(BOB.publicKey, Create())
                    failsWith(expectedError)
                }
            }

            // With 1 maintainer and 1 observer
            transaction {
                output(TestEvolvableTokenContract.ID, TestEvolvableTokenTypeFactory.withOneMaintainerAndOneObserver())

                // With create command signed by observer
                tweak {
                    command(CHARLES.publicKey, Create())
                    failsWith(expectedError)
                }

                // With create command signed by non-participant
                tweak {
                    command(DENISE.publicKey, Create())
                    failsWith(expectedError)
                }
            }


            // With 2 maintainers and 2 observers
            transaction {
                output(TestEvolvableTokenContract.ID, TestEvolvableTokenTypeFactory.withTwoMaintainersAndTwoObservers())

                // With create command signed by 1 maintainer and 1 observer
                tweak {
                    command(listOf(ALICE.publicKey, CHARLES.publicKey), Create())
                    failsWith(expectedError)
                }

                // With create command signed by observers
                tweak {
                    command(listOf(CHARLES.publicKey, DENISE.publicKey), Create())
                    failsWith(expectedError)
                }
            }
        }

        @Test
        fun `may only be signed by maintainers`() {
            val expectedError = "Only evolvable token maintainers may sign the create evolvable token transaction."

            // With 1 maintainer
            transaction {
                output(TestEvolvableTokenContract.ID, TestEvolvableTokenTypeFactory.withOneMaintainer())

                // With create command signed by maintainer and someone else
                tweak {
                    command(listOf(ALICE.publicKey, DENISE.publicKey), Create())
                    failsWith(expectedError)
                }
            }

            // With 2 maintainers
            transaction {
                output(TestEvolvableTokenContract.ID, TestEvolvableTokenTypeFactory.withTwoMaintainers())

                // With create command signed by both maintainers and someone else
                tweak {
                    command(listOf(ALICE.publicKey, BOB.publicKey, DENISE.publicKey), Create())
                    failsWith(expectedError)
                }
            }

            // With 1 maintainer and 1 observer
            transaction {
                output(TestEvolvableTokenContract.ID, TestEvolvableTokenTypeFactory.withOneMaintainerAndOneObserver())

                // With create command signed by maintainer and observer
                tweak {
                    command(listOf(ALICE.publicKey, CHARLES.publicKey), Create())
                    failsWith(expectedError)
                }
            }


            // With 2 maintainers and 2 observers
            transaction {
                output(TestEvolvableTokenContract.ID, TestEvolvableTokenTypeFactory.withTwoMaintainersAndTwoObservers())

                // With create command signed by both maintainers and first observer
                tweak {
                    command(listOf(ALICE.publicKey, BOB.publicKey, CHARLES.publicKey), Create())
                    failsWith(expectedError)
                }

                // With create command signed by both maintainers and second observer
                tweak {
                    command(listOf(ALICE.publicKey, BOB.publicKey, DENISE.publicKey), Create())
                    failsWith(expectedError)
                }

                // With create command signed by both maintainers and both observers
                tweak {
                    command(listOf(ALICE.publicKey, BOB.publicKey, CHARLES.publicKey, DENISE.publicKey), Create())
                    failsWith(expectedError)
                }
            }
        }

        @Test
        fun `may only have one command`() {
            val expectedError = "Evolvable token transactions support exactly one command only."

            // With 1 maintainer
            transaction {
                output(TestEvolvableTokenContract.ID, TestEvolvableTokenTypeFactory.withOneMaintainer())

                // With 1 create command
                tweak {
                    command(ALICE.publicKey, Create())
                    verifies()
                }

                // With 2 create commands from the same maintainer
                // Note: Due to change in V4, the TransactionBuilder will coalesce commands by CommandData. This means
                // that multiple commands of the same type are merged with the union of all signers.
                // This will _not_ error.
                // TODO Ensure this test passes/fails as expected for the given version of Corda
                tweak {
                    command(ALICE.publicKey, Create())
                    command(ALICE.publicKey, Create())
                    // V5 behaviour
//                    failsWith(expectedError)
                    // V4 behaviour
                    verifies()
                }

                // With 1 create and 1 update command from the same maintainer
                tweak {
                    command(ALICE.publicKey, Create())
                    command(ALICE.publicKey, Update())
                    failsWith(expectedError)
                }

                // With 2 create commands from different parties
                // Note: Due to change in V4, the TransactionBuilder will coalesce commands by CommandData. This means
                // that multiple commands of the same type are merged with the union of all signers.
                // This will _not_ error the same way as other multiple commands.
                // TODO Ensure this test passes/fails as expected for the given version of Corda
                tweak {
                    command(ALICE.publicKey, Create())
                    command(BOB.publicKey, Create())
                    // V5 behaviour
//                    failsWith(expectedError)
                    // V4 behaviour
                    failsWith("Only evolvable token maintainers may sign the create evolvable token transaction.")

                }

                // With 1 create and 1 update command from different maintainers
                tweak {
                    command(ALICE.publicKey, Create())
                    command(BOB.publicKey, Update())
                    failsWith(expectedError)
                }
            }

            // With 2 maintainers
            transaction {
                output(TestEvolvableTokenContract.ID, TestEvolvableTokenTypeFactory.withTwoMaintainers())

                // With 2 create commands
                // Note: Due to change in V4, the TransactionBuilder will coalesce commands by CommandData. This means
                // that multiple commands of the same type are merged with the union of all signers.
                // This will _not_ error as the result will be one Create command signed by both maintainers.
                // TODO Ensure this test passes/fails as expected for the given version of Corda
                tweak {
                    command(ALICE.publicKey, Create())
                    command(BOB.publicKey, Create())
                    // V5 behaviour
//                    failsWith(expectedError)
                    // V4 behaviour
                    verifies()
                }

                // With 3 create commands from different parties
                // Note: Due to change in V4, the TransactionBuilder will coalesce commands by CommandData. This means
                // that multiple commands of the same type are merged with the union of all signers.
                // This will _not_ error the same way as other multiple commands.
                // TODO Ensure this test passes/fails as expected for the given version of Corda
                tweak {
                    command(ALICE.publicKey, Create())
                    command(BOB.publicKey, Create())
                    command(CHARLES.publicKey, Create())
                    // V5 behaviour
//                    failsWith(expectedError)
                    // V4 behaviour
                    failsWith("Only evolvable token maintainers may sign the create evolvable token transaction.")
                }
            }
        }

        @Test
        fun `may not contain input states`() {
            val expectedError = "Create evolvable token transactions must not contain any inputs."

            transaction {
                input(TestEvolvableTokenContract.ID, TestEvolvableTokenTypeFactory.withOneMaintainer())
                output(TestEvolvableTokenContract.ID, TestEvolvableTokenTypeFactory.withOneMaintainer())
                command(ALICE.publicKey, Create())
                failsWith(expectedError)
            }
        }

        @Test
        fun `may only contain one output state`() {
            val expectedError = "Create evolvable token transactions must contain exactly one output."

            transaction {
                output(TestEvolvableTokenContract.ID, TestEvolvableTokenTypeFactory.withOneMaintainer())
                output(TestEvolvableTokenContract.ID, TestEvolvableTokenTypeFactory.withOneMaintainer())
                command(ALICE.publicKey, Create())
                failsWith(expectedError)
            }
        }
    }

    class OnUpdateTests {
        @Test
        fun `valid transactions`() {

            // With 1 maintainer, no ownership change
            transaction {
                val inputToken = TestEvolvableTokenTypeFactory.withOneMaintainer()
                val outputToken = TestEvolvableTokenTypeFactory.withOneMaintainer(inputToken.linearId)
                input(TestEvolvableTokenContract.ID, inputToken)
                output(TestEvolvableTokenContract.ID, outputToken)
                command(ALICE.publicKey, Update())
                verifies()
            }

            // With 1 maintainer, transferring ownership
            transaction {
                val inputToken = TestEvolvableTokenTypeFactory.withOneMaintainer()
                val outputToken = TestEvolvableTokenTypeFactory.withOneMaintainer(inputToken.linearId, BOB)
                input(TestEvolvableTokenContract.ID, inputToken)
                output(TestEvolvableTokenContract.ID, outputToken)
                command(listOf(ALICE.publicKey, BOB.publicKey), Update())
                verifies()
            }

            // With 1 maintainer, adding observer
            transaction {
                val inputToken = TestEvolvableTokenTypeFactory.withOneMaintainer()
                val outputToken = TestEvolvableTokenTypeFactory.withOneMaintainerAndOneObserver(inputToken.linearId)
                input(TestEvolvableTokenContract.ID, inputToken)
                output(TestEvolvableTokenContract.ID, outputToken)
                command(ALICE.publicKey, Update())
                verifies()
            }

            // With 2 maintainers, dropping one maintainer
            transaction {
                val inputToken = TestEvolvableTokenTypeFactory.withTwoMaintainers()
                val outputToken = TestEvolvableTokenTypeFactory.withOneMaintainer(inputToken.linearId, BOB)
                input(TestEvolvableTokenContract.ID, inputToken)
                output(TestEvolvableTokenContract.ID, outputToken)
                command(listOf(ALICE.publicKey, BOB.publicKey), Update())
                verifies()
            }

            // With 2 maintainers, transferring ownership to a third
            transaction {
                val inputToken = TestEvolvableTokenTypeFactory.withTwoMaintainers()
                val outputToken = TestEvolvableTokenTypeFactory.withOneMaintainer(inputToken.linearId, CHARLES)
                input(TestEvolvableTokenContract.ID, inputToken)
                output(TestEvolvableTokenContract.ID, outputToken)
                command(listOf(ALICE.publicKey, BOB.publicKey, CHARLES.publicKey), Update())
                verifies()
            }

            // With 1 maintainer, adding an observer
            transaction {
                val inputToken = TestEvolvableTokenTypeFactory.withOneMaintainer()
                val outputToken = TestEvolvableTokenTypeFactory.withOneMaintainerAndOneObserver(inputToken.linearId)
                input(TestEvolvableTokenContract.ID, inputToken)
                output(TestEvolvableTokenContract.ID, outputToken)
                command(ALICE.publicKey, Update())
                verifies()
            }

            // With 2 maintainers, adding 2 observers
            transaction {
                val inputToken = TestEvolvableTokenTypeFactory.withTwoMaintainers()
                val outputToken = TestEvolvableTokenTypeFactory.withTwoMaintainersAndTwoObservers(inputToken.linearId)
                input(TestEvolvableTokenContract.ID, inputToken)
                output(TestEvolvableTokenContract.ID, outputToken)
                command(listOf(ALICE.publicKey, BOB.publicKey), Update())
                verifies()
            }

            // With 2 maintainers, removing observers
            transaction {
                val inputToken = TestEvolvableTokenTypeFactory.withTwoMaintainersAndTwoObservers()
                val outputToken = TestEvolvableTokenTypeFactory.withTwoMaintainers(inputToken.linearId)
                input(TestEvolvableTokenContract.ID, inputToken)
                output(TestEvolvableTokenContract.ID, outputToken)
                command(listOf(ALICE.publicKey, BOB.publicKey), Update())
                verifies()
            }
        }

        @Test
        fun `all maintainers must be participants`() {
            val expectedError = "All evolvable token maintainers must also be participants."

            transaction {
                val inputToken = TestEvolvableTokenTypeFactory.withOneMaintainer()
                val outputToken = TestEvolvableTokenTypeFactory.withDifferingMaintainersAndParticipants(inputToken.linearId)
                input(TestEvolvableTokenContract.ID, inputToken)
                output(TestEvolvableTokenContract.ID, outputToken)
                command(ALICE.publicKey, Update())
                failsWith(expectedError)
            }
        }

        @Test
        fun `requires one command`() {
            val expectedError = "A transaction must contain at least one command"

            // With 1 maintainer
            transaction {
                input(TestEvolvableTokenContract.ID, TestEvolvableTokenTypeFactory.withOneMaintainer())
                output(TestEvolvableTokenContract.ID, TestEvolvableTokenTypeFactory.withTwoMaintainers())

                // Fails without a command
                failsWith(expectedError)
            }
        }

        @Test
        fun `must be signed by all maintainers`() {
            val expectedError = "All evolvable token maintainers (from inputs and outputs) must sign the update evolvable token transaction."

            // With 1 maintainer, no ownership transfer
            transaction {
                val inputToken = TestEvolvableTokenTypeFactory.withOneMaintainer()
                val outputToken = TestEvolvableTokenTypeFactory.withOneMaintainer(inputToken.linearId)
                input(TestEvolvableTokenContract.ID, inputToken)
                output(TestEvolvableTokenContract.ID, outputToken)

                // With create command signed by non-participant
                tweak {
                    command(DENISE.publicKey, Update())
                    failsWith(expectedError)
                }
            }

            // With 1 maintainer -> 1 maintainer
            transaction {
                val inputToken = TestEvolvableTokenTypeFactory.withOneMaintainer()
                val outputToken = TestEvolvableTokenTypeFactory.withOneMaintainer(inputToken.linearId, BOB)
                input(TestEvolvableTokenContract.ID, inputToken)
                output(TestEvolvableTokenContract.ID, outputToken)

                // With update command signed by old maintainer only
                tweak {
                    command(ALICE.publicKey, Update())
                    failsWith(expectedError)
                }

                // With update command signed by new maintainer only
                tweak {
                    command(BOB.publicKey, Update())
                    failsWith(expectedError)
                }
            }

            // With 1 maintainer -> 2 maintainers
            transaction {
                val inputToken = TestEvolvableTokenTypeFactory.withOneMaintainer()
                val outputToken = TestEvolvableTokenTypeFactory.withTwoMaintainers(inputToken.linearId)
                input(TestEvolvableTokenContract.ID, inputToken)
                output(TestEvolvableTokenContract.ID, outputToken)

                // With update command signed by old maintainer only
                tweak {
                    command(ALICE.publicKey, Update())
                    failsWith(expectedError)
                }

                // With update command signed by one new maintainer only
                tweak {
                    command(BOB.publicKey, Update())
                    failsWith(expectedError)
                }
            }


        }

        @Test
        fun `may only be signed by maintainers`() {
            val expectedError = "Only evolvable token maintainers (from inputs and outputs) may sign the update evolvable token transaction."

            // With 1 maintainer
            transaction {
                val inputToken = TestEvolvableTokenTypeFactory.withOneMaintainer()
                val outputToken = TestEvolvableTokenTypeFactory.withOneMaintainer(inputToken.linearId)
                input(TestEvolvableTokenContract.ID, inputToken)
                output(TestEvolvableTokenContract.ID, outputToken)

                // With create command signed by non-participant
                tweak {
                    command(listOf(ALICE.publicKey, DENISE.publicKey), Update())
                    failsWith(expectedError)
                }
            }

            // With 1 maintainer and 1 observer
            transaction {
                val inputToken = TestEvolvableTokenTypeFactory.withOneMaintainer()
                val outputToken = TestEvolvableTokenTypeFactory.withOneMaintainerAndOneObserver(inputToken.linearId)
                input(TestEvolvableTokenContract.ID, inputToken)
                output(TestEvolvableTokenContract.ID, outputToken)

                // With create command signed by non-participant
                tweak {
                    command(listOf(ALICE.publicKey, CHARLES.publicKey), Update())
                    failsWith(expectedError)
                }
            }
        }

        @Test
        fun `may only have one command`() {
            val expectedError = "Evolvable token transactions support exactly one command only."

            // With 1 maintainer
            transaction {
                val inputToken = TestEvolvableTokenTypeFactory.withOneMaintainer()
                val outputToken = TestEvolvableTokenTypeFactory.withOneMaintainer(inputToken.linearId)
                input(TestEvolvableTokenContract.ID, inputToken)
                output(TestEvolvableTokenContract.ID, outputToken)

                // With 1 create command
                tweak {
                    command(ALICE.publicKey, Update())
                    verifies()
                }

                // With 2 create commands from the same maintainer
                // Note: Due to change in V4, the TransactionBuilder will coalesce commands by CommandData. This means
                // that multiple commands of the same type are merged with the union of all signers.
                // This will _not_ error.
                // TODO Ensure this test passes/fails as expected for the given version of Corda
                tweak {
                    command(ALICE.publicKey, Update())
                    command(ALICE.publicKey, Update())
                    verifies()
                }

                // With 1 create and 1 update command from the same maintainer
                tweak {
                    command(ALICE.publicKey, Create())
                    command(ALICE.publicKey, Update())
                    failsWith(expectedError)
                }

                // With 2 create commands from different parties
                // Note: Due to change in V4, the TransactionBuilder will coalesce commands by CommandData. This means
                // that multiple commands of the same type are merged with the union of all signers.
                // This will _not_ error the same way as other multiple commands.
                // TODO Ensure this test passes/fails as expected for the given version of Corda
                tweak {
                    command(ALICE.publicKey, Update())
                    command(BOB.publicKey, Update())
                    failsWith("Only evolvable token maintainers (from inputs and outputs) may sign the update evolvable token transaction.")
                }

                // With 1 create and 1 update command from different maintainers
                tweak {
                    command(ALICE.publicKey, Create())
                    command(BOB.publicKey, Update())
                    failsWith(expectedError)
                }
            }

            // With 2 maintainers
            transaction {
                val inputToken = TestEvolvableTokenTypeFactory.withTwoMaintainers()
                val outputToken = TestEvolvableTokenTypeFactory.withTwoMaintainers(inputToken.linearId)
                input(TestEvolvableTokenContract.ID, inputToken)
                output(TestEvolvableTokenContract.ID, outputToken)

                // With 2 create commands
                // Note: Due to change in V4, the TransactionBuilder will coalesce commands by CommandData. This means
                // that multiple commands of the same type are merged with the union of all signers.
                // This will _not_ error as the result will be one Create command signed by both maintainers.
                // TODO Ensure this test passes/fails as expected for the given version of Corda
                tweak {
                    command(ALICE.publicKey, Update())
                    command(BOB.publicKey, Update())
                    verifies()
                }

                // With 3 create commands from different parties
                // Note: Due to change in V4, the TransactionBuilder will coalesce commands by CommandData. This means
                // that multiple commands of the same type are merged with the union of all signers.
                // This will _not_ error the same way as other multiple commands.
                // TODO Ensure this test passes/fails as expected for the given version of Corda
                tweak {
                    command(ALICE.publicKey, Update())
                    command(BOB.publicKey, Update())
                    command(CHARLES.publicKey, Update())
                    failsWith("Only evolvable token maintainers (from inputs and outputs) may sign the update evolvable token transaction.")
                }
            }
        }

        @Test
        fun `may only contain one input state`() {
            val expectedError = "Update evolvable token transactions must contain exactly one input."

            // With no inputs
            transaction {
                output(TestEvolvableTokenContract.ID, TestEvolvableTokenTypeFactory.withOneMaintainer())
                command(ALICE.publicKey, Update())
                failsWith(expectedError)
            }

            // With two inputs
            transaction {
                input(TestEvolvableTokenContract.ID, TestEvolvableTokenTypeFactory.withOneMaintainer())
                input(TestEvolvableTokenContract.ID, TestEvolvableTokenTypeFactory.withOneMaintainer())
                output(TestEvolvableTokenContract.ID, TestEvolvableTokenTypeFactory.withOneMaintainer())
                command(ALICE.publicKey, Update())
                failsWith(expectedError)
            }
        }

        @Test
        fun `may only contain one output state`() {
            val expectedError = "Update evolvable token transactions must contain exactly one output."

            // With no outputs
            transaction {
                input(TestEvolvableTokenContract.ID, TestEvolvableTokenTypeFactory.withOneMaintainer())
                command(ALICE.publicKey, Update())
                failsWith(expectedError)
            }

            // With two outputs
            transaction {
                input(TestEvolvableTokenContract.ID, TestEvolvableTokenTypeFactory.withOneMaintainer())
                output(TestEvolvableTokenContract.ID, TestEvolvableTokenTypeFactory.withOneMaintainer())
                output(TestEvolvableTokenContract.ID, TestEvolvableTokenTypeFactory.withOneMaintainer())
                command(ALICE.publicKey, Update())
                failsWith(expectedError)
            }
        }

    }

}