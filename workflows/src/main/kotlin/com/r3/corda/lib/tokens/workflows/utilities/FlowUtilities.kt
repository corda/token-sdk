@file:JvmName("FlowUtilities")

package com.r3.corda.lib.tokens.workflows.utilities

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.lib.tokens.contracts.states.AbstractToken
import com.r3.corda.lib.tokens.workflows.internal.flows.distribution.hasDistributionRecord
import com.r3.corda.lib.tokens.workflows.internal.schemas.DistributionRecord
import net.corda.core.contracts.CommandWithParties
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.StateAndRef
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.node.ServiceHub
import net.corda.core.node.services.IdentityService
import net.corda.core.transactions.LedgerTransaction
import net.corda.core.transactions.TransactionBuilder
import java.security.PublicKey

/**
 * Utility function to persist a new entity pertaining to a distribution record.
 * TODO: Add some error handling.
 */
@Suspendable
fun FlowLogic<*>.addPartyToDistributionList(party: Party, linearId: UniqueIdentifier) {
	// Create an persist a new entity.
	val hasRecord = hasDistributionRecord(serviceHub, linearId, party)
	if (!hasRecord) {
		val distributionRecord = DistributionRecord(linearId.id, party)
		serviceHub.withEntityManager { persist(distributionRecord) }
	} else {
		logger.info("Already stored a distribution record for $party and $linearId.")
	}

}

val LedgerTransaction.participants: List<AbstractParty>
	get() {
		val inputParticipants = inputStates.flatMap(ContractState::participants)
		val outputParticipants = outputStates.flatMap(ContractState::participants)
		return inputParticipants + outputParticipants
	}

@Suspendable
fun LedgerTransaction.ourSigningKeys(services: ServiceHub): List<PublicKey> {
	val signingKeys = commands.flatMap(CommandWithParties<*>::signers)
	return services.keyManagementService.filterMyKeys(signingKeys).toList()
}

@Suspendable
fun AbstractParty.toParty(services: ServiceHub) = services.identityService.requireKnownConfidentialIdentity(this)

@Suspendable
fun List<AbstractParty>.toWellKnownParties(services: ServiceHub): List<Party> {
	return map(services.identityService::requireKnownConfidentialIdentity)
}

// Needs to deal with confidential identities.
@Suspendable
fun requireSessionsForParticipants(participants: Collection<Party>, sessions: List<FlowSession>) {
	val sessionParties = sessions.map(FlowSession::counterparty)
	require(sessionParties.containsAll(participants)) {
		val missing = participants - sessionParties
		"There should be a flow session for all state participants. Sessions are missing for $missing."
	}
}

@Suspendable
fun FlowLogic<*>.sessionsForParticipants(states: List<ContractState>): List<FlowSession> {
	val stateParties = states.flatMap(ContractState::participants)
	return sessionsForParties(stateParties)
}

@Suspendable
fun FlowLogic<*>.sessionsForParties(parties: List<AbstractParty>): List<FlowSession> {
	val wellKnownParties = parties.toWellKnownParties(serviceHub)
	return wellKnownParties.map(::initiateFlow)
}

// Extension function that has nicer error message than the default one from [IdentityService::requireWellKnownPartyFromAnonymous].
@Suspendable
fun IdentityService.requireKnownConfidentialIdentity(party: AbstractParty): Party {
	return wellKnownPartyFromAnonymous(party)
		?: throw IllegalArgumentException(
			"Called flow with anonymous party that node doesn't know about. " +
					"Make sure that RequestConfidentialIdentity flow is called before."
		)
}

// Utilities for ensuring that the correct JAR which implements TokenType is added to the transaction.

fun addTokenTypeJar(tokens: List<AbstractToken>, transactionBuilder: TransactionBuilder) {
	tokens.forEach {
		// If there's no JAR hash then we don't need to do anything.
		val hash = it.tokenTypeJarHash ?: return
		if (!transactionBuilder.attachments().contains(hash)) {
			transactionBuilder.addAttachment(hash)
		}
	}
}

fun addTokenTypeJar(tokens: Iterable<StateAndRef<AbstractToken>>, transactionBuilder: TransactionBuilder) {
	addTokenTypeJar(tokens.map { it.state.data }, transactionBuilder)
}

fun addTokenTypeJar(changeOutput: AbstractToken, transactionBuilder: TransactionBuilder) {
	addTokenTypeJar(listOf(changeOutput), transactionBuilder)
}

fun addTokenTypeJar(input: StateAndRef<AbstractToken>, transactionBuilder: TransactionBuilder) {
	addTokenTypeJar(input.state.data, transactionBuilder)
}

