//package com.r3.corda.lib.tokens.workflows.internal.flows.confidential
//
//import co.paralleluniverse.fibers.Suspendable
//import net.corda.core.crypto.DigitalSignature
//import net.corda.core.flows.FlowLogic
//import net.corda.core.flows.FlowSession
//import net.corda.core.identity.AnonymousParty
//import net.corda.core.identity.PartyAndCertificate
//import net.corda.core.utilities.unwrap
//
//class RequestConfidentialIdentityFlowHandler(val otherSession: FlowSession) : FlowLogic<AnonymousParty>() {
//    @Suspendable
//    override fun call(): AnonymousParty {
//        otherSession.receive<ConfidentialIdentityRequest>().unwrap { it }
//        val ourAnonymousIdentity: PartyAndCertificate = serviceHub.keyManagementService.freshKeyAndCert(
//                identity = ourIdentityAndCert,
//                revocationEnabled = false
//        )
//        val data: ByteArray = buildDataToSign(ourAnonymousIdentity)
//        val signature: DigitalSignature = serviceHub.keyManagementService.sign(
//                bytes = data,
//                publicKey = ourAnonymousIdentity.owningKey
//        ).withoutKey()
//        val ourIdentityWithSig = IdentityWithSignature(ourAnonymousIdentity, signature)
//        otherSession.send(ourIdentityWithSig)
//        return ourAnonymousIdentity.party.anonymise()
//    }
//}