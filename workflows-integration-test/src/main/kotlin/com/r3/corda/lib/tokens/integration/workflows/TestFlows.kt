package com.r3.corda.lib.tokens.integration.workflows

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.lib.ci.workflows.SyncKeyMappingFlow
import com.r3.corda.lib.ci.workflows.SyncKeyMappingFlowHandler
import com.r3.corda.lib.tokens.contracts.states.FungibleToken
import com.r3.corda.lib.tokens.contracts.types.TokenPointer
import com.r3.corda.lib.tokens.contracts.types.TokenType
import com.r3.corda.lib.tokens.selection.InsufficientBalanceException
import com.r3.corda.lib.tokens.selection.database.selector.DatabaseTokenSelection
import com.r3.corda.lib.tokens.selection.memory.internal.Holder
import com.r3.corda.lib.tokens.selection.memory.selector.LocalTokenSelector
import com.r3.corda.lib.tokens.selection.memory.services.VaultWatcherService
import com.r3.corda.lib.tokens.testing.states.House
import com.r3.corda.lib.tokens.workflows.flows.move.addMoveNonFungibleTokens
import com.r3.corda.lib.tokens.workflows.flows.move.addMoveTokens
import com.r3.corda.lib.tokens.workflows.flows.rpc.RedeemFungibleTokens
import com.r3.corda.lib.tokens.workflows.flows.rpc.RedeemNonFungibleTokens
import com.r3.corda.lib.tokens.workflows.internal.flows.distribution.UpdateDistributionListFlow
import com.r3.corda.lib.tokens.workflows.internal.flows.distribution.getDistributionList
import com.r3.corda.lib.tokens.workflows.internal.flows.finality.ObserverAwareFinalityFlow
import com.r3.corda.lib.tokens.workflows.internal.flows.finality.ObserverAwareFinalityFlowHandler
import com.r3.corda.lib.tokens.workflows.internal.schemas.DistributionRecord
import com.r3.corda.lib.tokens.workflows.utilities.getPreferredNotary
import com.r3.corda.lib.tokens.workflows.utilities.ourSigningKeys
import net.corda.core.contracts.Amount
import net.corda.core.contracts.StateAndRef
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.serialization.CordaSerializable
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.seconds
import net.corda.core.utilities.unwrap
import java.time.Duration
import java.time.temporal.ChronoUnit
import java.util.*

// This is very simple test flow for DvP.
@CordaSerializable
private class DvPNotification(val amount: Amount<TokenType>)

@StartableByRPC
@InitiatingFlow
class DvPFlow(val house: House, val newOwner: Party) : FlowLogic<SignedTransaction>() {
	@Suspendable
	override fun call(): SignedTransaction {
		val txBuilder = TransactionBuilder(notary = getPreferredNotary(serviceHub))
		addMoveNonFungibleTokens(txBuilder, serviceHub, house.toPointer<House>(), newOwner)
		val session = initiateFlow(newOwner)
		// Ask for input stateAndRefs - send notification with the amount to exchange.
		session.send(DvPNotification(house.valuation))
		// TODO add some checks for inputs and outputs
		val inputs = subFlow(ReceiveStateAndRefFlow<FungibleToken>(session))
		// Receive outputs (this is just quick and dirty, we could calculate them on our side of the flow).
		val outputs = session.receive<List<FungibleToken>>().unwrap { it }
		addMoveTokens(txBuilder, inputs, outputs)
		// Synchronise any confidential identities
		subFlow(SyncKeyMappingFlow(session, txBuilder.toWireTransaction(serviceHub)))
		val ourSigningKeys = txBuilder.toLedgerTransaction(serviceHub).ourSigningKeys(serviceHub)
		val initialStx = serviceHub.signInitialTransaction(txBuilder, signingPubKeys = ourSigningKeys)
		val stx = subFlow(CollectSignaturesFlow(initialStx, listOf(session), ourSigningKeys))
		// Update distribution list.
		subFlow(UpdateDistributionListFlow(stx))
		return subFlow(ObserverAwareFinalityFlow(stx, listOf(session)))
	}
}

@InitiatedBy(DvPFlow::class)
class DvPFlowHandler(val otherSession: FlowSession) : FlowLogic<Unit>() {
	@Suspendable
	override fun call() {
		// Receive notification with house price.
		val dvPNotification = otherSession.receive<DvPNotification>().unwrap { it }
		// Chose state and refs to send back.
		// TODO This is API pain, we assumed that we could just modify TransactionBuilder, but... it cannot be sent over the wire, because non-serializable
		// We need custom serializer and some custom flows to do checks.
		val changeHolder = serviceHub.keyManagementService.freshKeyAndCert(ourIdentityAndCert, false).party.anonymise()
		val (inputs, outputs) = DatabaseTokenSelection(serviceHub).generateMove(
			lockId = runId.uuid,
			partiesAndAmounts = listOf(Pair(otherSession.counterparty, dvPNotification.amount)),
			changeHolder = changeHolder
		)
		subFlow(SendStateAndRefFlow(otherSession, inputs))
		otherSession.send(outputs)
		subFlow(SyncKeyMappingFlowHandler(otherSession))
		subFlow(object : SignTransactionFlow(otherSession) {
			override fun checkTransaction(stx: SignedTransaction) {}
		}
		)
		subFlow(ObserverAwareFinalityFlowHandler(otherSession))
	}
}

@StartableByRPC
class GetDistributionList(val housePtr: TokenPointer<House>) : FlowLogic<List<DistributionRecord>>() {
	@Suspendable
	override fun call(): List<DistributionRecord> {
		return getDistributionList(serviceHub, housePtr.pointer.pointer)
	}
}

@StartableByRPC
class CheckTokenPointer(val housePtr: TokenPointer<House>) : FlowLogic<House>() {
	@Suspendable
	override fun call(): House {
		return housePtr.pointer.resolve(serviceHub).state.data
	}
}

// TODO This is hack that will be removed after fix in Corda 5. startFlowDynamic doesn't handle type parameters properly.
@StartableByRPC
class RedeemNonFungibleHouse(
	val housePtr: TokenPointer<House>,
	val issuerParty: Party
) : FlowLogic<SignedTransaction>() {
	@Suspendable
	override fun call(): SignedTransaction {
		return subFlow(RedeemNonFungibleTokens(housePtr, issuerParty, emptyList()))
	}
}

@StartableByRPC
class RedeemFungibleGBP(
	val amount: Amount<TokenType>,
	val issuerParty: Party
) : FlowLogic<SignedTransaction>() {
	@Suspendable
	override fun call(): SignedTransaction {
		return subFlow(RedeemFungibleTokens(amount, issuerParty, emptyList(), null))
	}
}

// Helper flow for selection testing
@StartableByRPC
class SelectAndLockFlow(val amount: Amount<TokenType>, val delay: Duration = 1.seconds) : FlowLogic<Unit>() {
	@Suspendable
	override fun call() {
		val selector = LocalTokenSelector(serviceHub)
		selector.selectTokens(amount)
		sleep(delay)
	}
}

// Helper flow for selection testing
@StartableByRPC
class JustLocalSelect(val amount: Amount<TokenType>, val timeBetweenSelects: Duration = Duration.of(10, ChronoUnit.SECONDS), val maxSelectAttempts: Int = 5) :
	FlowLogic<List<StateAndRef<FungibleToken>>>() {
	@Suspendable
	override fun call(): List<StateAndRef<FungibleToken>> {
		val selector = LocalTokenSelector(serviceHub)
		var selectionAttempts = 0
		while (selectionAttempts < maxSelectAttempts) {
			try {
				return selector.selectTokens(amount)
			} catch (e: InsufficientBalanceException) {
				logger.error("failed to select", e)
				sleep(timeBetweenSelects, true)
				selectionAttempts++
			}
		}
		throw InsufficientBalanceException("Could not select: ${amount}")
	}
}

@StartableByRPC
class GetSelectionPageSize : FlowLogic<Int>() {
	@Suspendable
	override fun call(): Int {
		val vaultWatcherService = serviceHub.cordaService(VaultWatcherService::class.java)
		return vaultWatcherService.providedConfig.pageSize
	}
}

@StartableByRPC
class GetSelectionSleepDuration : FlowLogic<Int>() {
	@Suspendable
	override fun call(): Int {
		val vaultWatcherService = serviceHub.cordaService(VaultWatcherService::class.java)
		return vaultWatcherService.providedConfig.sleep
	}
}

@StartableByRPC
class LockEverythingGetValue(val tokenType: TokenType) : FlowLogic<Long>() {
	@Suspendable
	override fun call(): Long {
		val vaultWatcherService = serviceHub.cordaService(VaultWatcherService::class.java)
		val amount = Amount(Long.MAX_VALUE, tokenType)
		val selectionId = UUID.randomUUID().toString()
		var tokens: List<StateAndRef<FungibleToken>>? = vaultWatcherService.selectTokens(
			Holder.TokenOnly(), amount,
			allowShortfall = true,
			selectionId = selectionId
		)

		val value = tokens?.map { it.state.data.amount.quantity }?.sum()

		tokens?.forEach {
			vaultWatcherService.unlockToken(
				it, selectionId
			)
		}

		// just to make sure that tokens is not checkpointed anywhere
		tokens = null

		return value!!
	}
}