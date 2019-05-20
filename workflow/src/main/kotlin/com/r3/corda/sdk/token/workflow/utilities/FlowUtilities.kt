package com.r3.corda.sdk.token.workflow.utilities

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.sdk.token.contracts.states.AbstractToken
import com.r3.corda.sdk.token.contracts.types.TokenPointer
import com.r3.corda.sdk.token.workflow.flows.internal.distribution.UpdateDistributionListRequestFlow
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
@Suspendable
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

@Suspendable
fun FlowLogic<*>.addToDistributionList(tokens: List<AbstractToken<TokenPointer<*>>>) {
    tokens.forEach { token ->
        val tokenType = token.tokenType
        addPartyToDistributionList(serviceHub, token.holder.toParty(serviceHub), tokenType.pointer.pointer)
    }
}

@Suspendable
fun FlowLogic<*>.updateDistributionList(tokens: List<AbstractToken<TokenPointer<*>>>) {
    for (token in tokens) {
        val tokenType = token.tokenType
        val holderParty = serviceHub.identityService.requireKnownConfidentialIdentity(token.holder)
        subFlow(UpdateDistributionListRequestFlow(tokenType, ourIdentity, holderParty))
    }
}

// Extension function that has nicer error message than the default one from [IdentityService::requireWellKnownPartyFromAnonymous].
@Suspendable
fun IdentityService.requireKnownConfidentialIdentity(party: AbstractParty): Party {
    return wellKnownPartyFromAnonymous(party)
            ?: throw IllegalArgumentException("Called flow with anonymous party that node doesn't know about. " +
                    "Make sure that RequestConfidentialIdentity flow is called before.")
}

