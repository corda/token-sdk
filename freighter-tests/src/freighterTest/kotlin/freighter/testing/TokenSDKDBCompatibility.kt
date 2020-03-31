package freighter.testing

import com.r3.corda.lib.tokens.contracts.states.FungibleToken
import com.r3.corda.lib.tokens.contracts.types.IssuedTokenType
import com.r3.corda.lib.tokens.contracts.types.TokenType
import com.r3.corda.lib.tokens.workflows.OwnerMigration
import com.r3.corda.lib.tokens.workflows.flows.rpc.IssueTokens
import freighter.deployments.DeploymentContext
import freighter.deployments.NodeBuilder
import freighter.deployments.SingleNodeDeployment
import freighter.machine.DeploymentMachineProvider
import freighter.machine.generateRandomString
import liquibase.database.DatabaseConnection
import liquibase.database.core.PostgresDatabase
import net.corda.core.contracts.Amount
import net.corda.core.messaging.startFlow
import net.corda.core.utilities.getOrThrow
import net.corda.nodeapi.internal.persistence.DatabaseConfig
import org.hamcrest.MatcherAssert
import org.hamcrest.core.Is
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import utility.LoggingUtils
import utility.Retry
import utility.getArtifactAndDependencies
import utility.getOrThrow
import java.net.URLClassLoader
import java.sql.Connection
import java.sql.Driver
import java.time.Duration
import java.util.*
import java.util.concurrent.CompletableFuture

class TokenSDKDBCompatibility : DockerRemoteMachineBasedTest() {

	val tokenContracts =
		NodeBuilder.DeployedCordapp.fromClassPath("tokens-contracts")

	val tokenWorkflows =
		NodeBuilder.DeployedCordapp.fromClassPath("tokens-workflows")

	val tokenSelection =
		NodeBuilder.DeployedCordapp.fromClassPath("tokens-selection")

	val modernCiV1 = NodeBuilder.DeployedCordapp.fromGradleArtifact(
		group = "com.r3.corda.lib.ci",
		artifact = "ci-workflows",
		version = "1.0"
	)

	val stressTesterCordapp = NodeBuilder.DeployedCordapp.fromClassPath("freighter-cordapp-flows")

	lateinit var nms: CompletableFuture<DeploymentMachineProvider.NetworkServicesInfo>

	@BeforeEach
	fun setupNMS() {
		val newInstance = DatabaseConfig::class.java.getConstructor().newInstance()
		nms = machineProvider.generateNMSEnvironment()
	}

	@Test
	fun `tokens can be loaded on a node running postgres 9_6`() {
		runTokensOnNodeRunningDatabase(DeploymentMachineProvider.DatabaseType.PG_9_6)
	}

	@Test
	fun `tokens can be loaded on a node running postgres 10_10`() {
		runTokensOnNodeRunningDatabase(DeploymentMachineProvider.DatabaseType.PG_10_10)
	}

	@Test
	fun `tokens can be loaded on a node running postgres 11_5`() {
		runTokensOnNodeRunningDatabase(DeploymentMachineProvider.DatabaseType.PG_11_5)
	}

	@Test
	fun `tokens can be loaded on a node running ms_sql`() {
		runTokensOnNodeRunningDatabase(DeploymentMachineProvider.DatabaseType.MS_SQL)
	}

	@Test
	@OracleTest
	fun `tokens can be loaded on a node running oracle 12 r2`() {
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
				.withCordapp(stressTesterCordapp)
				.withCordapp(tokenContracts)
				.withCordapp(tokenWorkflows)
				.withCordapp(tokenSelection)
				.withCordapp(modernCiV1)
				.withDatabase(machineProvider.requestDatabase(db))
		).withVersion("4.3")
			.deploy(deploymentContext)

		val nodeMachine = deploymentResult.getOrThrow().nodeMachines.single()
		val ourIdentity = nodeMachine.rpc { nodeInfo().legalIdentities.first() }


		val issuedTokenType = IssuedTokenType(ourIdentity, TokenType("StefCoin", 2))
		val amount = Amount(100_000_000, issuedTokenType)
		val tokenToIssue1 = FungibleToken(amount, ourIdentity)
		val tokenToIssue2 = FungibleToken(amount, ourIdentity)

		val issueTx = nodeMachine.rpc {
			startFlow(
				::IssueTokens,
				listOf(tokenToIssue1, tokenToIssue2), emptyList()
			).returnValue.getOrThrow(Duration.ofMinutes(1))
		}
		println("Successfully issued tokens: ${issueTx.coreTransaction.outputs}")
	}

}

