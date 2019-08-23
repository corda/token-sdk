//package com.r3.corda.lib.tokens.workflows.internal.flows.confidential
//
//import co.paralleluniverse.fibers.Suspendable
//import net.corda.core.flows.FlowLogic
//import net.corda.core.flows.FlowSession
//import net.corda.core.identity.PartyAndCertificate
//import net.corda.core.utilities.unwrap
//
//class RequestConfidentialIdentityFlow(val session: FlowSession) : FlowLogic<PartyAndCertificate>() {
//    @Suspendable
//    override fun call(): PartyAndCertificate {
//        return session.sendAndReceive<IdentityWithSignature>(ConfidentialIdentityRequest()).unwrap { theirIdentWithSig ->
//            validateAndRegisterIdentity(
//                    serviceHub = serviceHub,
//                    otherSide = session.counterparty,
//                    theirAnonymousIdentity = theirIdentWithSig.identity,
//                    signature = theirIdentWithSig.signature
//            )
//        }
//    }
//}