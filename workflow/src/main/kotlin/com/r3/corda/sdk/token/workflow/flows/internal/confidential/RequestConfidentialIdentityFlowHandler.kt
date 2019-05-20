package com.r3.corda.sdk.token.workflow.flows.internal.confidential

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.crypto.DigitalSignature
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.identity.PartyAndCertificate
import net.corda.core.utilities.unwrap

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