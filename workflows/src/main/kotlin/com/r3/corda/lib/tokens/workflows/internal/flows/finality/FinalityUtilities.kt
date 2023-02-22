package com.r3.corda.lib.tokens.workflows.internal.flows.finality

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.lib.tokens.contracts.commands.RedeemTokenCommand
import com.r3.corda.lib.tokens.workflows.utilities.participants
import com.r3.corda.lib.tokens.workflows.utilities.toWellKnownParties
import com.r3.corda.lib.tokens.workflows.utilities.toWellKnownPartiesExcludingUnknown
import net.corda.core.contracts.CommandWithParties
import net.corda.core.identity.Party
import net.corda.core.node.ServiceHub
import net.corda.core.serialization.CordaSerializable
import net.corda.core.transactions.LedgerTransaction

@CordaSerializable
enum class TransactionRole { PARTICIPANT, OBSERVER }

@Suspendable
internal fun LedgerTransaction.getParticipantsAndIssuers(serviceHub: ServiceHub): Set<Party> {

	val issuers: Set<Party> = commands
		.map(CommandWithParties<*>::value)
		.filterIsInstance<RedeemTokenCommand>()
		.map { it.token.issuer }
		.toSet()

	return participants.toWellKnownParties(serviceHub).toSet() + issuers
}

@Suspendable
internal fun LedgerTransaction.getParticipantsAndIssuersExcludingUnknown(serviceHub: ServiceHub): Set<Party> {

	val issuers: Set<Party> = commands
		.map(CommandWithParties<*>::value)
		.filterIsInstance<RedeemTokenCommand>()
		.map { it.token.issuer }
		.toSet()

	return participants.toWellKnownPartiesExcludingUnknown(serviceHub).toSet() + issuers
}