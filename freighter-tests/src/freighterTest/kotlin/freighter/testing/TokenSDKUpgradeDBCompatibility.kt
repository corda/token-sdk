package freighter.testing

import com.r3.corda.lib.tokens.contracts.states.FungibleToken
import com.r3.corda.lib.tokens.contracts.types.IssuedTokenType
import com.r3.corda.lib.tokens.contracts.types.TokenType
import com.r3.corda.lib.tokens.workflows.flows.rpc.IssueTokens
import com.r3.corda.lib.tokens.workflows.utilities.heldTokenAmountCriteria
import freighter.deployments.DeploymentContext
import freighter.deployments.NodeBuilder
import freighter.deployments.SingleNodeDeployment
import freighter.machine.DeploymentMachineProvider
import freighter.machine.generateRandomString
import net.corda.core.contracts.Amount
import net.corda.core.messaging.startFlow
import net.corda.core.utilities.getOrThrow
import org.hamcrest.MatcherAssert
import org.hamcrest.Matchers.containsInAnyOrder
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import utility.getOrThrow
import java.time.Duration
import java.util.concurrent.CompletableFuture

class TokenSDKUpgradeDBCompatibility : DockerRemoteMachineBasedTest() {

    val tokensV1Contracts = NodeBuilder.DeployedCordapp.fromGradleArtifact(
            group = "com.r3.corda.lib.tokens",
            artifact = "tokens-contracts",
            version = "1.1"
    )

    val tokensV1Workflows = NodeBuilder.DeployedCordapp.fromGradleArtifact(
            group = "com.r3.corda.lib.tokens",
            artifact = "tokens-workflows",
            version = "1.1"
    )

    val tokensV1Selection = NodeBuilder.DeployedCordapp.fromGradleArtifact(
            group = "com.r3.corda.lib.tokens",
            artifact = "tokens-selection",
            version = "1.1"
    )

    val tokenCurrentContracts =
            NodeBuilder.DeployedCordapp.fromClassPath("tokens-contracts")

    val tokenCurrentWorkflows =
            NodeBuilder.DeployedCordapp.fromClassPath("tokens-workflows")

    val tokenCurrentSelection =
            NodeBuilder.DeployedCordapp.fromClassPath("tokens-selection")

    val modernCiV1 = NodeBuilder.DeployedCordapp.fromGradleArtifact(
            group = "com.r3.corda.lib.ci",
            artifact = "ci-workflows",
            version = "1.0"
    )

    lateinit var nms: CompletableFuture<DeploymentMachineProvider.NetworkServicesInfo>

    @BeforeEach
    fun setupNMS() {
        nms = machineProvider.generateNMSEnvironment()
    }

    @Test
    fun `tokens can be upgraded on a node running postgres 9_6`() {
        runTokensOnNodeRunningDatabase(DeploymentMachineProvider.DatabaseType.PG_9_6)
    }

    @Test
    fun `tokens can be upgraded on a node running postgres 10_10`() {
        runTokensOnNodeRunningDatabase(DeploymentMachineProvider.DatabaseType.PG_10_10)
    }

    @Test
    fun `tokens can be upgraded on a node running postgres 11_5`() {
        runTokensOnNodeRunningDatabase(DeploymentMachineProvider.DatabaseType.PG_11_5)
    }

    @Test
    fun `tokens can be upgraded on a node running ms_sql`() {
        runTokensOnNodeRunningDatabase(DeploymentMachineProvider.DatabaseType.MS_SQL)
    }

    @Test
    @OracleTest
    fun `tokens can be upgraded on a node running oracle 12 r2`() {
        runTokensOnNodeRunningDatabase(DeploymentMachineProvider.DatabaseType.ORACLE_12_R2)
    }

    private fun runTokensOnNodeRunningDatabase(db: DeploymentMachineProvider.DatabaseType) {
        val randomString = generateRandomString()

        val userName = System.getenv("ARTIFACTORY_USERNAME")
                ?: throw IllegalStateException("Please ensure that ARTIFACTORY_USERNAME is defined in the environment")
        val password = System.getenv("ARTIFACTORY_PASSWORD")
                ?: throw IllegalStateException("Please ensure that ARTIFACTORY_PASSWORD is defined in the environment")

        val deploymentContext = DeploymentContext(machineProvider, nms, userName, password)

        val deploymentResult = SingleNodeDeployment(
                NodeBuilder().withX500("O=PartyB, C=GB, L=LONDON, CN=$randomString")
                        .withCordapp(tokensV1Contracts)
                        .withCordapp(tokensV1Workflows)
                        .withCordapp(tokensV1Selection)
                        .withCordapp(modernCiV1)
                        .withDatabase(machineProvider.requestDatabase(db))
        ).withVersion("4.3")
                .deploy(deploymentContext)

        val nodeMachine = deploymentResult.getOrThrow().nodeMachines.single()
        val ourIdentity = nodeMachine.rpc { nodeInfo().legalIdentities.first() }


        val tokenType = TokenType("StefCoin", 2)
        val issuedTokenType = IssuedTokenType(ourIdentity, tokenType)
        val amount = Amount(100_000_000, issuedTokenType)
        val tokenToIssue1 = FungibleToken(amount, ourIdentity)
        val tokenToIssue2 = FungibleToken(amount, ourIdentity)

        val issueTx = nodeMachine.rpc {
            startFlow(
                    ::IssueTokens,
                    listOf(tokenToIssue1, tokenToIssue2), emptyList()
            ).returnValue.getOrThrow(Duration.ofMinutes(1))
        }


        nodeMachine.stopNode()
        nodeMachine.upgradeCordapp(tokensV1Contracts, tokenCurrentContracts)
        nodeMachine.upgradeCordapp(tokensV1Workflows, tokenCurrentWorkflows)
        nodeMachine.upgradeCordapp(tokensV1Selection, tokenCurrentSelection)
        nodeMachine.startNode()

        nodeMachine.rpc {
            val keyCriteria = heldTokenAmountCriteria(tokenType, ourIdentity)
            val actual = vaultQueryByCriteria(keyCriteria, FungibleToken::class.java).states.map { it.state.data }
            MatcherAssert.assertThat(actual, containsInAnyOrder(tokenToIssue1, tokenToIssue2))
        }

    }


}