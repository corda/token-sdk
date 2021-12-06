package freighter.testing

import com.r3.corda.lib.tokens.contracts.states.FungibleToken
import com.r3.corda.lib.tokens.contracts.types.IssuedTokenType
import com.r3.corda.lib.tokens.contracts.types.TokenType
import com.r3.corda.lib.tokens.integration.workflows.GetSelectionPageSize
import com.r3.corda.lib.tokens.integration.workflows.GetSelectionSleepDuration
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
import net.corda.core.internal.stream
import net.corda.core.messaging.startFlow
import net.corda.core.utilities.getOrThrow
import org.hamcrest.MatcherAssert
import org.hamcrest.Matchers.`is`
import org.junit.jupiter.api.Test
import utility.getOrThrow
import java.time.Duration
import kotlin.streams.toList

class TokensLoadedOnRestartTest : DockerRemoteMachineBasedTest() {

	val tokenCurrentContracts =
		NodeBuilder.DeployedCordapp.fromClassPath("tokens-contracts").signedWithFreighterKey()

	val testFlows =
		NodeBuilder.DeployedCordapp.fromClassPath("workflows-integration-test").signedWithFreighterKey()

	val tokenCurrentWorkflows =
		NodeBuilder.DeployedCordapp.fromClassPath("tokens-workflows").withConfig(
			"""
				stateSelection.inMemory.enabled=true
				stateSelection.inMemory.indexingStrategies=[EXTERNAL_ID, PUBLIC_KEY]
				stateSelection.inMemory.pageSize=5
				stateSelection.inMemory.loadingSleepSeconds=600
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
		val amount = Amount(1, issuedTokenType)
		val tokenToIssue1 = (0..100).map { FungibleToken(amount, createdCi) }.toList()

		val loadingPageSize = nodeMachine1.rpc {
			startFlow(
				::GetSelectionPageSize
			).returnValue.getOrThrow()
		}

		MatcherAssert.assertThat(loadingPageSize, `is`(5))

		val loadingSleepDuration = nodeMachine1.rpc {
			startFlow(
				::GetSelectionSleepDuration
			).returnValue.getOrThrow()
		}

		MatcherAssert.assertThat(loadingSleepDuration, `is`(600))


		val issueTXs = (0..100).stream(true).mapToObj {
			val issueTx = nodeMachine1.rpc {
				startFlow(
					::IssueTokens,
					tokenToIssue1, listOf()
				).returnValue.getOrThrow(Duration.ofMinutes(1))
			}
			issueTx
		}.toList()


		print(issueTXs)

		nodeMachine1.stopNode()

		println()

		nodeMachine1.startNode()

		val tokenValueLoadedInCache = nodeMachine1.rpc {
			startFlow(
				::LockEverythingGetValue,
				tokenType
			).returnValue.getOrThrow(Duration.ofMinutes(1))
		}

		MatcherAssert.assertThat(tokenValueLoadedInCache, `is`(5L))
	}


}