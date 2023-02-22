package com.r3.corda.lib.tokens.workflows.internal.flows.finality

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.crypto.SecureHash
import net.corda.core.flows.FlowException
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.flows.ReceiveTransactionFlow
import net.corda.core.identity.Party
import net.corda.core.node.StatesToRecord
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.unwrap

class ObserverAwareFinalityFlowHandler @JvmOverloads constructor(val otherSession: FlowSession, val expectedTxId: SecureHash? = null) : FlowLogic<SignedTransaction?>() {
	@Suspendable
	override fun call(): SignedTransaction? {
		val role = otherSession.receive<TransactionRole>().unwrap { it }
		val statesToRecord = role.toStatesToRecord()

		return if (serviceHub.myInfo.isLegalIdentity(otherSession.counterparty)) null else {
			subFlow(object : ReceiveTransactionFlow(otherSession, true, statesToRecord) {
				override fun checkBeforeRecording(stx: SignedTransaction) {
					val ledgerTransaction = stx.toLedgerTransaction(serviceHub, false)
					val wellKnownParticipantsAndIssuers: Set<Party> = ledgerTransaction.getParticipantsAndIssuersExcludingUnknown(serviceHub)
					val weAreNotAParticipant = ourIdentity !in wellKnownParticipantsAndIssuers

					if (weAreNotAParticipant && role == TransactionRole.PARTICIPANT) {
						throw FlowException("Our identity is not a transaction participant, but we were sent the PARTICIPANT role.")
					}

					require(expectedTxId == null || expectedTxId == stx.id) {
						"We expected to receive transaction with ID $expectedTxId but instead got ${stx.id}. Transaction was" +
								"not recorded and nor its states sent to the vault."
					}
				}
			})
		}
	}

	@Suspendable
	fun TransactionRole.toStatesToRecord(): StatesToRecord = when (this) {
		TransactionRole.PARTICIPANT -> StatesToRecord.ONLY_RELEVANT
		TransactionRole.OBSERVER -> StatesToRecord.ALL_VISIBLE
	}
}
