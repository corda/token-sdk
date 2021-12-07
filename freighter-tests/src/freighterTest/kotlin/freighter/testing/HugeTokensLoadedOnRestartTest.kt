package freighter.testing

import com.r3.corda.lib.tokens.contracts.states.FungibleToken
import com.r3.corda.lib.tokens.contracts.types.IssuedTokenType
import com.r3.corda.lib.tokens.contracts.types.TokenType
import com.r3.corda.lib.tokens.integration.workflows.LockEverythingGetValue
import com.r3.corda.lib.tokens.workflows.flows.rpc.IssueTokens
import com.stress.flows.CreateNewCIFlow
import freighter.deployments.DeploymentContext
import freighter.deployments.NodeBuilder
import freighter.deployments.SingleNodeDeployment
import freighter.deployments.UnitOfDeployment
import freighter.machine.DeploymentMachineProvider
import freighter.machine.generateRandomString
import net.corda.core.contracts.Amount
import net.corda.core.messaging.startFlow
import net.corda.core.utilities.getOrThrow
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import utility.getOrThrow
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.locks.ReentrantLock
import java.util.stream.StreamSupport
import kotlin.concurrent.withLock
import kotlin.streams.toList

@Tag("LARGE_TEST")
annotation class LargeTest

@LargeTest
class HugeTokensLoadedOnRestartTest : DockerRemoteMachineBasedTest() {

    val loadingThreads = 8
    val pageSize = 10_000

    val tokenCurrentContracts =
            NodeBuilder.DeployedCordapp.fromClassPath("tokens-contracts").signedWithFreighterKey()

    val testFlows =
            NodeBuilder.DeployedCordapp.fromClassPath("workflows-integration-test").signedWithFreighterKey()

    val tokenCurrentWorkflows =
            NodeBuilder.DeployedCordapp.fromClassPath("tokens-workflows").withConfig(
                    """
				stateSelection.inMemory.enabled=true
				stateSelection.inMemory.indexingStrategies=[EXTERNAL_ID, PUBLIC_KEY]
				stateSelection.inMemory.pageSize=${pageSize}
				stateSelection.inMemory.loadingSleepSeconds=-1
				stateSelection.inMemory.loadingThreads=${loadingThreads}
			""".trimIndent().byteInputStream()
            )

    val modernCiV1 = NodeBuilder.DeployedCordapp.fromGradleArtifact(
            group = "com.r3.corda.lib.ci",
            artifact = "ci-workflows",
            version = "1.0"
    )

    val freighterHelperCordapp = NodeBuilder.DeployedCordapp.fromClassPath("freighter-cordapp-flows")

    @Test
    fun `tokens can be loaded async during node startup on postgres 9_6`() {
        run(DeploymentMachineProvider.DatabaseType.PG_9_6)
    }

    @Test
    fun `tokens can be loaded async during node startup on H2`() {
        run(DeploymentMachineProvider.DatabaseType.H2)
    }

    private fun run(db: DeploymentMachineProvider.DatabaseType) {
        val randomString = generateRandomString()
        val deploymentContext = DeploymentContext(machineProvider, nms, artifactoryUsername, artifactoryPassword)
        val node1 = SingleNodeDeployment(
                NodeBuilder().withX500("O=PartyB, C=GB, L=LONDON, CN=$randomString")
                        .withCordapp(tokenCurrentContracts)
                        .withCordapp(tokenCurrentWorkflows)
                        .withCordapp(modernCiV1)
                        .withCordapp(freighterHelperCordapp)
                        .withCordapp(testFlows)
                        .withDatabase(machineProvider.requestDatabase(db))
        ).withVersion(UnitOfDeployment.CORDA_4_7)
                .deploy(deploymentContext)

        val nodeMachine1 = node1.getOrThrow().nodeMachines.single()

        val createdCi = nodeMachine1.rpc {
            startFlow(::CreateNewCIFlow).returnValue.getOrThrow().also {
                println("Successfully created CI: $it")
            }
        }

        val tokenType = TokenType("StefCoin", 2)
        val issuedTokenType = IssuedTokenType(nodeMachine1.identity(), tokenType)
        val issuedTotal = AtomicLong(0)

        val tokenToIssue = (0.until(100)).map { FungibleToken(Amount(1, issuedTokenType), createdCi) }.toList()

        val numberIssued = StreamSupport.stream((0.until(5000)).chunked(200).spliterator(), true).map { toIssue ->
            nodeMachine1.rpc {
                repeat(toIssue.size) {
                    startFlow(
                            ::IssueTokens,
                            tokenToIssue, listOf()
                    ).returnValue.getOrThrow(Duration.ofMinutes(1))
                    println("[${Thread.currentThread().name}] Total number issued: ${issuedTotal.addAndGet(tokenToIssue.size * 1L)}")
                }

            }

            toIssue.size
        }.toList().sum()

        nodeMachine1.stopNode()
        println()
        nodeMachine1.startNode()

        val nodeStartTime = System.currentTimeMillis()

        var amountLoaded = 0L

        val lock = ReentrantLock()
        val condition = lock.newCondition()


        //whilst we are loading, issue 500 more tokens to see if they are correctly loaded
        CompletableFuture.runAsync {
            lock.withLock {
                condition.await()
            }
            nodeMachine1.rpc {
                repeat(5) {
                    startFlow(
                            ::IssueTokens,
                            tokenToIssue, listOf()
                    ).returnValue.getOrThrow(Duration.ofMinutes(1))
                    println("[${Thread.currentThread().name}] Total number issued: ${issuedTotal.addAndGet(tokenToIssue.size * 1L)}")
                }
            }
        }

        while (amountLoaded != (issuedTotal.toLong())) {
            nodeMachine1.rpc {
                amountLoaded = startFlow(
                        ::LockEverythingGetValue,
                        tokenType
                ).returnValue.getOrThrow(Duration.ofMinutes(1))
            }
            println("TOTAL TOKEN VALUE IN CACHE: $amountLoaded")

            if (amountLoaded > 0) {
                lock.withLock {
                    condition.signal()
                }
            }

            if (amountLoaded != issuedTotal.toLong()) {
                Thread.sleep(20000)
            }
        }

        val loadEndTime = System.currentTimeMillis()

        println("it took: ${(loadEndTime - nodeStartTime) / 1000} seconds to populate $amountLoaded states using $loadingThreads loading threads and pageSize: $pageSize")


    }


}