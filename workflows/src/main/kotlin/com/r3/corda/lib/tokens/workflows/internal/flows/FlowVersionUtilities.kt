package com.r3.corda.lib.tokens.workflows.internal.flows

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.lib.ci.workflows.ProvideKeyFlow
import com.r3.corda.lib.ci.workflows.RequestKeyFlow
import com.r3.corda.lib.ci.workflows.SyncKeyMappingFlow
import com.r3.corda.lib.ci.workflows.SyncKeyMappingFlowHandler
import net.corda.confidential.IdentitySyncFlow
import net.corda.core.crypto.DigitalSignature
import net.corda.core.crypto.verify
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.AnonymousParty
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.identity.PartyAndCertificate
import net.corda.core.node.ServiceHub
import net.corda.core.serialization.CordaSerializable
import net.corda.core.serialization.serialize
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.unwrap
import java.security.PublicKey
import java.security.SignatureException

/**
 * Internal utilities to handle change of flow versions after introducing new confidential identities.
 * Corda doesn't expose nice versioning API for inlined flows nor for flows in the libraries. We introduced only versioning of
 * initiating flows from token-sdk. Inlined versions will fallback to new confidential identities CorDapp.
 */
internal const val INITIATING_TOKENS_FLOW = "com.r3.corda.lib.tokens.workflows.flows.rpc."

// Internal utilities after introducing new confidential identities. To handle different versions.
@Suspendable
internal fun FlowLogic<*>.provideKeyVersion(session: FlowSession): AbstractParty {
    val otherFlowVersion = session.getCounterpartyFlowInfo().flowVersion
    val topLevelName = FlowLogic.currentTopLevel?.let { it::class.java.canonicalName } ?: ""
    // This will work only for initiating flow
    return if (otherFlowVersion == 1) {
        if (!topLevelName.startsWith(INITIATING_TOKENS_FLOW)) {
            logger.warn("Your CorDapp is using new confidential identities, but other party flow has version 1. Falling back to old CI." +
                    "If this is not intended behaviour version your flows with version >= 2")
        }
        // Old Confidential Identities case
        subFlow(RequestConfidentialIdentityFlowHandler(session))
    } else {
        // New Confidential Identities
        subFlow(ProvideKeyFlow(session))
    }
}

@Suspendable
internal fun FlowLogic<*>.requestKeyVersion(session: FlowSession): AnonymousParty {
    val otherFlowVersion = session.getCounterpartyFlowInfo().flowVersion
    val topLevelName = FlowLogic.currentTopLevel?.let { it::class.java.canonicalName } ?: ""
    // This will work only for initiating flow
    return if (otherFlowVersion == 1) {
        if (!topLevelName.startsWith(INITIATING_TOKENS_FLOW)) {
            logger.warn("Your CorDapp is using new confidential identities, but other party flow has version 1. Falling back to old CI." +
                    "If this is not intended behaviour version your flows with version >= 2")
        }
        // Old Confidential Identities case
        val key = subFlow(RequestConfidentialIdentityFlow(session)).owningKey
        AnonymousParty(key)
    } else {
        // New Confidential Identities
        subFlow(RequestKeyFlow(session))
    }
}

@Suspendable
internal fun FlowLogic<*>.syncKeyVersionHandler(session: FlowSession) {
    val otherFlowVersion = session.getCounterpartyFlowInfo().flowVersion
    val topLevelName = FlowLogic.currentTopLevel?.let { it::class.java.canonicalName } ?: ""
    // This will work only for initiating flow
    if (otherFlowVersion == 1) {
        if (!topLevelName.startsWith(INITIATING_TOKENS_FLOW)) {
            logger.warn("Your CorDapp is using new confidential identities, but other party flow has version 1. Falling back to old CI." +
                    "If this is not intended behaviour version your flows with version >= 2")
        }
        // Old Confidential Identities case
        subFlow(IdentitySyncFlow.Receive(session))
    } else {
        // New Confidential Identities
        subFlow(SyncKeyMappingFlowHandler(session))
    }
}

@Suspendable
internal fun FlowLogic<*>.syncKeyVersion(session: FlowSession, txBuilder: TransactionBuilder) {
    val otherFlowVersion = session.getCounterpartyFlowInfo().flowVersion
    val topLevelName = FlowLogic.currentTopLevel?.let { it::class.java.canonicalName } ?: ""
    // This will work only for initiating flow
    if (otherFlowVersion == 1) {
        if (!topLevelName.startsWith(INITIATING_TOKENS_FLOW)) {
            logger.warn("Your CorDapp is using new confidential identities, but other party flow has version 1. Falling back to old CI." +
                    "If this is not intended behaviour version your flows with version >= 2")
        }
        // Old Confidential Identities case
        subFlow(IdentitySyncFlow.Send(session, txBuilder.toWireTransaction(serviceHub)))
    } else {
        // New Confidential Identities
        subFlow(SyncKeyMappingFlow(session, txBuilder.toWireTransaction(serviceHub)))
    }
}

@CordaSerializable
internal class ConfidentialIdentityRequest

@CordaSerializable
internal data class IdentityWithSignature(val identity: PartyAndCertificate, val signature: DigitalSignature)

/**
 * Data class used only in the context of asserting that the owner of the private key for the listed key wants to use it
 * to represent the named entity. This is paired with an X.509 certificate (which asserts the signing identity says
 * the key represents the named entity) and protects against a malicious party incorrectly claiming others'
 * keys.
 */
@CordaSerializable
internal data class CertificateOwnershipAssertion(val name: CordaX500Name, val owningKey: PublicKey)

internal class RequestConfidentialIdentityFlow(val session: FlowSession) : FlowLogic<PartyAndCertificate>() {
    @Suspendable
    override fun call(): PartyAndCertificate {
        return session.sendAndReceive<IdentityWithSignature>(ConfidentialIdentityRequest()).unwrap { theirIdentWithSig ->
            validateAndRegisterIdentity(
                    serviceHub = serviceHub,
                    otherSide = session.counterparty,
                    theirAnonymousIdentity = theirIdentWithSig.identity,
                    signature = theirIdentWithSig.signature
            )
        }
    }
}

internal class RequestConfidentialIdentityFlowHandler(val otherSession: FlowSession) : FlowLogic<AnonymousParty>() {
    @Suspendable
    override fun call(): AnonymousParty {
        otherSession.receive<ConfidentialIdentityRequest>().unwrap { it }
        val ourAnonymousIdentity: PartyAndCertificate = serviceHub.keyManagementService.freshKeyAndCert(
                identity = ourIdentityAndCert,
                revocationEnabled = false
        )
        val data: ByteArray = buildDataToSign(ourAnonymousIdentity)
        val signature: DigitalSignature = serviceHub.keyManagementService.sign(
                bytes = data,
                publicKey = ourAnonymousIdentity.owningKey
        ).withoutKey()
        val ourIdentityWithSig = IdentityWithSignature(ourAnonymousIdentity, signature)
        otherSession.send(ourIdentityWithSig)
        return ourAnonymousIdentity.party.anonymise()
    }
}

/**
 * Verifies the confidential identity cert chain and if vaild then stores the identity mapping in the [IdentityService].
 */
@Suspendable
internal fun validateAndRegisterIdentity(
        serviceHub: ServiceHub,
        otherSide: Party,
        theirAnonymousIdentity: PartyAndCertificate,
        signature: DigitalSignature
): PartyAndCertificate {
    if (theirAnonymousIdentity.name != otherSide.name) {
        throw Exception("Certificate subject must match counterparty's well known identity.")
    }
    try {
        theirAnonymousIdentity.owningKey.verify(buildDataToSign(theirAnonymousIdentity), signature)
    } catch (ex: SignatureException) {
        throw Exception("Signature does not match the expected identity ownership assertion.", ex)
    }
    // Validate then store their identity so that we can prove the key in the transaction is held by the counterparty.
    serviceHub.identityService.verifyAndRegisterIdentity(theirAnonymousIdentity)
    return theirAnonymousIdentity
}

internal fun buildDataToSign(identity: PartyAndCertificate): ByteArray {
    return CertificateOwnershipAssertion(identity.name, identity.owningKey).serialize().bytes
}
