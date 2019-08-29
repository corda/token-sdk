//package com.r3.corda.lib.tokens.workflows.internal.flows.confidential
//
//import co.paralleluniverse.fibers.Suspendable
//import com.r3.corda.lib.ci.ProvideKeyFlow
//import net.corda.core.flows.FlowLogic
//import net.corda.core.flows.FlowSession
//import net.corda.core.identity.AnonymousParty
//import net.corda.core.utilities.unwrap
//import java.security.PublicKey
//
//class RequestConfidentialIdentityFlowHandler(val otherSession: FlowSession) : FlowLogic<AnonymousParty>() {
//    @Suspendable
//    override fun call(): AnonymousParty {
//        return subFlow(ProvideKeyFlow(otherSession))
//    }
//}