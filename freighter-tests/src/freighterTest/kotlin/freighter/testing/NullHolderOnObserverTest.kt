package freighter.testing

import com.r3.corda.lib.tokens.contracts.states.FungibleToken
import com.r3.corda.lib.tokens.contracts.types.IssuedTokenType
import com.r3.corda.lib.tokens.contracts.types.TokenType
import com.r3.corda.lib.tokens.workflows.flows.rpc.IssueTokens
import com.r3.corda.lib.tokens.workflows.flows.rpc.MoveFungibleTokens
import com.r3.corda.lib.tokens.workflows.utilities.heldTokenAmountCriteria
import com.stress.flows.CreateNewCIFlow
import freighter.deployments.DeploymentContext
import freighter.deployments.NodeBuilder
import freighter.deployments.SingleNodeDeployment
import freighter.deployments.UnitOfDeployment
import freighter.machine.DeploymentMachineProvider
import freighter.machine.generateRandomString
import net.corda.core.contracts.Amount
import net.corda.core.internal.sumByLong
import net.corda.core.messaging.startFlow
import net.corda.core.utilities.getOrThrow
import org.hamcrest.MatcherAssert
import org.hamcrest.Matchers.`is`
import org.hamcrest.Matchers.containsInAnyOrder
import org.junit.jupiter.api.Test
import utility.getOrThrow
import java.time.Duration
import java.time.Instant
import java.util.concurrent.TimeoutException

class NullHolderOnObserverTest : DockerRemoteMachineBasedTest() {

	val tokenCurrentContracts =
		NodeBuilder.DeployedCordapp.fromClassPath("tokens-contracts").signedWithFreighterKey()

	val tokenCurrentWorkflows =
		NodeBuilder.DeployedCordapp.fromClassPath("tokens-workflows")

	val modernCiV1 = NodeBuilder.DeployedCordapp.fromGradleArtifact(
		group = "com.r3.corda.lib.ci",
		artifact = "ci-workflows",
		version = "1.0"
	)

	val freighterHelperCordapp = NodeBuilder.DeployedCordapp.fromClassPath("freighter-cordapp-flows")

	@Test
	fun `tokens can be observed on node that does not know CI running postgres 9_6`() {
		runTokensOnNodeRunningDatabase(DeploymentMachineProvider.DatabaseType.PG_9_6)
	}

	@Test
	fun `tokens can be observed on node that does not know CI running H2`() {
		runTokensOnNodeRunningDatabase(DeploymentMachineProvider.DatabaseType.H2)
	}

	@Test
	fun `tokens can be observed on node that does not know CI running postgres 10_10`() {
		runTokensOnNodeRunningDatabase(DeploymentMachineProvider.DatabaseType.PG_10_10)
	}

	@Test
	fun `tokens can be observed on node that does not know CI running postgres 11_5`() {
		runTokensOnNodeRunningDatabase(DeploymentMachineProvider.DatabaseType.PG_11_5)
	}

	@Test
	fun `tokens can be observed on node that does not know CI running ms_sql`() {
		runTokensOnNodeRunningDatabase(DeploymentMachineProvider.DatabaseType.MS_SQL)
	}

	private fun runTokensOnNodeRunningDatabase(db: DeploymentMachineProvider.DatabaseType) {
		val randomString = generateRandomString()
		val deploymentContext = DeploymentContext(machineProvider, nms, artifactoryUsername, artifactoryPassword)
		val node1 = SingleNodeDeployment(
			NodeBuilder().withX500("O=PartyB, C=GB, L=LONDON, CN=$randomString")
				.withCordapp(tokenCurrentContracts)
				.withCordapp(tokenCurrentWorkflows)
				.withCordapp(modernCiV1)
				.withCordapp(freighterHelperCordapp)
				.withDatabase(machineProvider.requestDatabase(db))
		).withVersion(UnitOfDeployment.CORDA_4_7)
			.deploy(deploymentContext)

		val node2 = SingleNodeDeployment(
			NodeBuilder().withX500("O=PartyA, C=GB, L=LONDON, CN=$randomString")
				.withCordapp(tokenCurrentContracts)
				.withCordapp(tokenCurrentWorkflows)
				.withCordapp(modernCiV1)
				.withCordapp(freighterHelperCordapp)
				.withDatabase(machineProvider.requestDatabase(db))
		).withVersion(UnitOfDeployment.CORDA_4_7)
			.deploy(deploymentContext)

		val nodeMachine1 = node1.getOrThrow().nodeMachines.single()
		val nodeMachine2 = node2.getOrThrow().nodeMachines.single()

		val createdCi = nodeMachine1.rpc {
			startFlow(::CreateNewCIFlow).returnValue.getOrThrow().also {
				println("Successfully created CI: $it")
			}
		}

		val tokenType = TokenType("StefCoin", 2)
		val issuedTokenType = IssuedTokenType(nodeMachine1.identity(), tokenType)
		val amount = Amount(100_000_000, issuedTokenType)
		val tokenToIssue1 = FungibleToken(amount, createdCi)

		nodeMachine2.waitForNetworkMapToPopulate(nodeMachine1)
		nodeMachine1.waitForNetworkMapToPopulate(nodeMachine2)

		//issue tokens to the new CI on node1 - node2 does NOT know about this CI
		//node2 is an observer of this TX
		val issueTx = nodeMachine1.rpc {
			startFlow(
				::IssueTokens,
				listOf(tokenToIssue1), listOf(nodeMachine2.identity())
			).returnValue.getOrThrow(Duration.ofMinutes(1))
		}

		//check that the observer (node2) has recorded the issue correctly
		val maxLoopTime = Instant.now().plusSeconds(60)
		nodeMachine2.rpc {
			while (Instant.now().isBefore(maxLoopTime)) {
				val keyCriteria = heldTokenAmountCriteria(tokenType, createdCi)
				val actual = vaultQueryByCriteria(keyCriteria, FungibleToken::class.java).states.map { it.state.data }
				if (actual.isEmpty()) {
					Thread.sleep(10)
					continue
				}
				MatcherAssert.assertThat(actual.map { it.amount }.sumByLong { it.quantity }, `is`(100_000_000L))
				return@rpc
			}
			throw TimeoutException()
		}

	}


}