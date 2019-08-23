package com.r3.corda.lib.tokens.workflows.internal.testflows

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.lib.ci.SyncKeyMappingFlow
import com.r3.corda.lib.ci.SyncKeyMappingFlowHandler
import com.r3.corda.lib.tokens.contracts.states.FungibleToken
import com.r3.corda.lib.tokens.contracts.types.TokenPointer
import com.r3.corda.lib.tokens.contracts.types.TokenType
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
import com.r3.corda.lib.tokens.workflows.internal.selection.TokenSelection
import com.r3.corda.lib.tokens.workflows.types.PartyAndAmount
import com.r3.corda.lib.tokens.workflows.utilities.getPreferredNotary
import com.r3.corda.lib.tokens.workflows.utilities.ourSigningKeys
import net.corda.core.contracts.Amount
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.serialization.CordaSerializable
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.unwrap

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
//        subFlow(IdentitySyncFlow.Send(session, txBuilder.toWireTransaction(serviceHub)))
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
        val (inputs, outputs) = TokenSelection(serviceHub).generateMove(
                lockId = runId.uuid,
                partyAndAmounts = listOf(PartyAndAmount(otherSession.counterparty, dvPNotification.amount)),
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