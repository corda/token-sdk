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
import org.junit.jupiter.api.Timeout
import utility.getOrThrow
import java.time.Duration

class TokenSDKUpgrade46Compatibility : DockerRemoteMachineBasedTest() {

	//remove the prod key and sign with freighter key
	val tokensV1Contracts = NodeBuilder.DeployedCordapp.fromGradleArtifact(
		group = "com.r3.corda.lib.tokens",
		artifact = "tokens-contracts",
		version = "1.1"
	).signedWithFreighterKey()

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

	//we must sign with the same freighter key to allow contract upgrade from 1.1
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

	@Test @Timeout(300_000)
	fun `tokens can be upgraded on a node running postgres 9_6`() {
		runTokensOnNodeRunningDatabase(DeploymentMachineProvider.DatabaseType.PG_9_6)
	}

	@Test @Timeout(300_000)
	fun `tokens can be upgraded on a node running postgres H2`() {
		runTokensOnNodeRunningDatabase(DeploymentMachineProvider.DatabaseType.H2)
	}

	@Test @Timeout(300_000)
	fun `tokens can be upgraded on a node running postgres 10_10`() {
		runTokensOnNodeRunningDatabase(DeploymentMachineProvider.DatabaseType.PG_10_10)
	}

	@Test @Timeout(300_000)
	fun `tokens can be upgraded on a node running postgres 11_5`() {
		runTokensOnNodeRunningDatabase(DeploymentMachineProvider.DatabaseType.PG_11_5)
	}

	@Test @Timeout(300_000)
	fun `tokens can be upgraded on a node running ms_sql`() {
		runTokensOnNodeRunningDatabase(DeploymentMachineProvider.DatabaseType.MS_SQL)
	}

	@Test @Timeout(300_000)
	@OracleTest
	fun `tokens can be upgraded on a node running oracle 12 r2`() {
		runTokensOnNodeRunningDatabase(DeploymentMachineProvider.DatabaseType.ORACLE_12_R2)
	}

	private fun runTokensOnNodeRunningDatabase(db: DeploymentMachineProvider.DatabaseType) {
		val randomString = generateRandomString()
		val deploymentContext = DeploymentContext(machineProvider, nms, artifactoryUsername, artifactoryPassword)
		val deploymentResult = SingleNodeDeployment(
			NodeBuilder().withX500("O=PartyB, C=GB, L=LONDON, CN=$randomString")
				.withCordapp(tokensV1Contracts)
				.withCordapp(tokensV1Workflows)
				.withCordapp(tokensV1Selection)
				.withCordapp(modernCiV1)
				.withCordapp(freighterHelperCordapp)
				.withDatabase(machineProvider.requestDatabase(db))
		).withVersion(UnitOfDeployment.CORDA_4_6_SNAPSHOT)
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
		//selection should be fat-jarred into tokens-workflows
		nodeMachine.deleteCordapp(tokensV1Selection)
		nodeMachine.startNode()

		nodeMachine.rpc {
			val keyCriteria = heldTokenAmountCriteria(tokenType, ourIdentity)
			val actual = vaultQueryByCriteria(keyCriteria, FungibleToken::class.java).states.map { it.state.data }
			MatcherAssert.assertThat(actual, containsInAnyOrder(tokenToIssue1, tokenToIssue2))
		}

		val createdCi = nodeMachine.rpc {
			startFlow(::CreateNewCIFlow).returnValue.getOrThrow().also {
				println("Successfully created CI: $it")
			}
		}

		val moveToCi = nodeMachine.rpc {
			startFlow(
				::MoveFungibleTokens,
				Amount(50_000_000, tokenType),
				createdCi
			).returnValue.getOrThrow(Duration.ofMinutes(1))
		}

		nodeMachine.rpc {
			val keyCriteria = heldTokenAmountCriteria(tokenType, createdCi)
			val actual = vaultQueryByCriteria(keyCriteria, FungibleToken::class.java).states.map { it.state.data }
			MatcherAssert.assertThat(actual.map { it.amount }.sumByLong { it.quantity }, `is`(50_000_000L))
		}

	}


}
