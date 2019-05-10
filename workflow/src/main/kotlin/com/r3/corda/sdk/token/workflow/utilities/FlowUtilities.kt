package com.r3.corda.sdk.token.workflow.utilities

import com.r3.corda.sdk.token.contracts.states.AbstractToken
import com.r3.corda.sdk.token.contracts.types.TokenPointer
import com.r3.corda.sdk.token.contracts.types.TokenType
import com.r3.corda.sdk.token.workflow.flows.distribution.UpdateDistributionList
import com.r3.corda.sdk.token.workflow.schemas.DistributionRecord
import net.corda.core.contracts.CommandWithParties
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.node.ServiceHub
import net.corda.core.node.services.IdentityService
import net.corda.core.transactions.LedgerTransaction
import java.security.PublicKey

/**
 * Utility function to persist a new entity pertaining to a distribution record.
 * TODO: Add some error handling.
 */
fun addPartyToDistributionList(services: ServiceHub, party: Party, linearId: UniqueIdentifier) {
    // Create an persist a new entity.
    val distributionRecord = DistributionRecord(linearId.id, party)
    services.withEntityManager { persist(distributionRecord) }
}

val LedgerTransaction.participants: List<AbstractParty>
    get() {
        val inputParticipants = inputStates.flatMap(ContractState::participants)
        val outputParticipants = outputStates.flatMap(ContractState::participants)
        return inputParticipants + outputParticipants
    }

fun LedgerTransaction.ourSigningKeys(services: ServiceHub): List<PublicKey> {
    val signingKeys = commands.flatMap(CommandWithParties<*>::signers)
    return services.keyManagementService.filterMyKeys(signingKeys).toList()
}

fun AbstractParty.toParty(services: ServiceHub) = services.identityService.requireKnownConfidentialIdentity(this)

fun List<AbstractParty>.toWellKnownParties(services: ServiceHub): List<Party> {
    return map(services.identityService::requireKnownConfidentialIdentity)
}

// Needs to deal with confidential identities.
fun requireSessionsForParticipants(participants: Collection<Party>, sessions: Set<FlowSession>) {
    val sessionParties = sessions.map(FlowSession::counterparty)
    require(sessionParties.containsAll(participants)) {
        val missing = participants - sessionParties
        "There should be a flow session for all state participants. Sessions are missing for $missing."
    }
}

fun FlowLogic<*>.sessionsForParicipants(states: List<ContractState>, otherParties: Iterable<Party>): Set<FlowSession> {
    val stateParties = states.flatMap(ContractState::participants)
    val wellKnownStateParties = stateParties.toWellKnownParties(serviceHub)
    val allParties = wellKnownStateParties + otherParties
    return allParties.map(::initiateFlow).toSet()
}

fun <T : TokenType> FlowLogic<*>.addToDistributionList(tokens: List<AbstractToken<T>>) {
    tokens.forEach { token ->
        val tokenType = token.tokenType
        if (tokenType is TokenPointer<*>) {
            addPartyToDistributionList(serviceHub, token.holder.toParty(serviceHub), tokenType.pointer.pointer)
        }
    }
}

fun <T : TokenType> FlowLogic<*>.updateDistributionList(tokens: List<AbstractToken<T>>) {
    for (token in tokens) {
        val tokenType = token.tokenType
        val holderParty = serviceHub.identityService.requireKnownConfidentialIdentity(token.holder)
        if (tokenType is TokenPointer<*>) {
            subFlow(UpdateDistributionList.Initiator(tokenType, ourIdentity, holderParty))
        }
    }
}

// Extension function that has nicer error message than the default one from [IdentityService::requireWellKnownPartyFromAnonymous].
fun IdentityService.requireKnownConfidentialIdentity(party: AbstractParty): Party {
    return wellKnownPartyFromAnonymous(party)
            ?: throw IllegalArgumentException("Called flow with anonymous party that node doesn't know about. " +
                    "Make sure that RequestConfidentialIdentity flow is called before.")
}

