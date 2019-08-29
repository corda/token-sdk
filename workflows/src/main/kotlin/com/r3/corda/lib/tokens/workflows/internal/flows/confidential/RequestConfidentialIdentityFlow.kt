//package com.r3.corda.lib.tokens.workflows.internal.flows.confidential
//
//import co.paralleluniverse.fibers.Suspendable
//import com.r3.corda.lib.ci.RequestKeyFlow
//import net.corda.core.flows.FlowLogic
//import net.corda.core.flows.FlowSession
//import net.corda.core.identity.AnonymousParty
//import net.corda.core.identity.PartyAndCertificate
//import net.corda.core.utilities.unwrap
//
//class RequestConfidentialIdentityFlow(val session: FlowSession) : FlowLogic<AnonymousParty>() {
//    @Suspendable
//    override fun call(): AnonymousParty {
//        return subFlow(RequestKeyFlow(session))
//    }
//}