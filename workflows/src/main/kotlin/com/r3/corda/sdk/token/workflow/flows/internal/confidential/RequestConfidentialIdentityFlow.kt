package com.r3.corda.sdk.token.workflow.flows.internal.confidential

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.identity.PartyAndCertificate
import net.corda.core.utilities.unwrap

class RequestConfidentialIdentityFlow(val session: FlowSession) : FlowLogic<PartyAndCertificate>() {
    @Suspendable
    override fun call(): PartyAndCertificate {
        return session.sendAndReceive<IdentityWithSignature>(ConfidentialIdentityRequest()).unwrap { theirIdentWithSig ->
            validateAndRegisterIdentity(serviceHub, session.counterparty, theirIdentWithSig.identity, theirIdentWithSig.signature)
        }
    }
}