package com.r3.corda.sdk.token.workflow.flows.confidential

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.crypto.DigitalSignature
import net.corda.core.crypto.verify
import net.corda.core.flows.FlowException
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.identity.PartyAndCertificate
import net.corda.core.node.ServiceHub
import net.corda.core.serialization.CordaSerializable
import net.corda.core.serialization.serialize
import net.corda.core.utilities.unwrap
import java.security.PublicKey
import java.security.SignatureException

/**
 * Basic set of flows whereby one party can request another to generate a confidential identity.
 */
class RequestConfidentialIdentityFlow(val session: FlowSession) : FlowLogic<PartyAndCertificate>() {
    @Suspendable
    override fun call(): PartyAndCertificate {
        return session.sendAndReceive<IdentityWithSignature>(ConfidentialIdentityRequest()).unwrap { theirIdentWithSig ->
            validateAndRegisterIdentity(serviceHub, session.counterparty, theirIdentWithSig.identity, theirIdentWithSig.signature)
        }
    }
}

class RequestConfidentialIdentityFlowHandler(val otherSession: FlowSession) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        otherSession.receive<ConfidentialIdentityRequest>().unwrap { it }
        val ourAnonymousIdentity: PartyAndCertificate = serviceHub.keyManagementService.freshKeyAndCert(ourIdentityAndCert, false)
        val data: ByteArray = buildDataToSign(ourAnonymousIdentity)
        val signature: DigitalSignature = serviceHub.keyManagementService.sign(data, ourAnonymousIdentity.owningKey).withoutKey()
        val ourIdentityWithSig = IdentityWithSignature(ourAnonymousIdentity, signature)
        otherSession.send(ourIdentityWithSig)
    }
}

@CordaSerializable
class ConfidentialIdentityRequest

@CordaSerializable
open class Exception(message: String, cause: Throwable? = null) : FlowException(message, cause)

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
    // Validate then store their identity so that we can prove the key in the transaction is owned by the counterparty.
    serviceHub.identityService.verifyAndRegisterIdentity(theirAnonymousIdentity)
    return theirAnonymousIdentity
}

internal fun buildDataToSign(identity: PartyAndCertificate): ByteArray {
    return CertificateOwnershipAssertion(identity.name, identity.owningKey).serialize().bytes
}

/**
 * Data class used only in the context of asserting that the owner of the private key for the listed key wants to use it
 * to represent the named entity. This is paired with an X.509 certificate (which asserts the signing identity says
 * the key represents the named entity) and protects against a malicious party incorrectly claiming others'
 * keys.
 */
@CordaSerializable
internal data class CertificateOwnershipAssertion(val name: CordaX500Name, val owningKey: PublicKey)

@CordaSerializable
internal data class IdentityWithSignature(val identity: PartyAndCertificate, val signature: DigitalSignature)
