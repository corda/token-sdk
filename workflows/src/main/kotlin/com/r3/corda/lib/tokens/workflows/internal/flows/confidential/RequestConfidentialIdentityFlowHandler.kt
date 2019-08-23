package com.r3.corda.lib.tokens.workflows.internal.flows.confidential

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.lib.ci.ProvideKeyFlow
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.identity.AnonymousParty
import net.corda.core.utilities.unwrap
import java.security.PublicKey

class RequestConfidentialIdentityFlowHandler(val otherSession: FlowSession) : FlowLogic<AnonymousParty>() {
    @Suspendable
    override fun call(): AnonymousParty {
        //TODO get ProvideKeyFlow to return the AnonymousParty to avoid this hogwash
        subFlow(ProvideKeyFlow(otherSession))
        val key = otherSession.receive<PublicKey>().unwrap { it }
        return AnonymousParty(key)
    }
}