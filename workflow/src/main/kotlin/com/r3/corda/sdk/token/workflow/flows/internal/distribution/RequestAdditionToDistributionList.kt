package com.r3.corda.sdk.token.workflow.flows.internal.distribution

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.sdk.token.contracts.states.EvolvableTokenType
import com.r3.corda.sdk.token.workflow.utilities.addPartyToDistributionList
import net.corda.core.contracts.StateAndRef
import net.corda.core.flows.*
import net.corda.core.utilities.unwrap

// TODO: REMOVE THIS?

/**
 * Simple set of flows for a party to request updates for a particular evolvable token. These flows don't do much
 * checking, the responder always adds a requesting party to the distribution list.
 *
 * As this flow requires a [StateAndRef] in the constructor, it is only intended to be called by tokenHolders that have _at
 * least_ one version of the evolvable token. This is probably acceptable as when the issuer issues some of the token
 * for the first time to a party, then they will also send along the most current version of the evolvable token as
 * well. This also simplifies the workflow a bit; when tokensToIssue are issued, it is expected that the issuer sends along the
 * evolvable token as well and likewise when some amount of token is transferred from one party to another. Once a party
 * has at least one version of the evolvable token, they can request to be automatically updated using this flow going
 * forward.
 *
 * When data distribution groups are available then these flows can be retired. However, they are sufficient for the
 * time being.
 */
object RequestAdditionToDistributionList {

    sealed class FlowResult {
        object Success : FlowResult()
        // No failure for now!
    }

    @StartableByRPC
    @InitiatingFlow
    class Initiator(val stateAndRef: StateAndRef<EvolvableTokenType>) : FlowLogic<Unit>() {
        @Suspendable
        override fun call() {
            val state = stateAndRef.state.data
            // Pick the first maintainer.
            // TODO: Try each maintainer.
            val maintainer = state.maintainers.first()
            val session = initiateFlow(maintainer)
            logger.info("Requesting addition to $maintainer distribution list for ${state.linearId}.")
            val result = session.sendAndReceive<FlowResult>(stateAndRef).unwrap { it }
            // Don't do anything with the flow result for now.
            return when (result) {
                FlowResult.Success -> Unit
            }
        }
    }

    @InitiatedBy(Initiator::class)
    class Responder(val otherSession: FlowSession) : FlowLogic<Unit>() {
        @Suspendable
        override fun call() {
            // Receive the stateAndRef that the requesting party wants updates for.
            val stateAndRef = otherSession.receive<StateAndRef<EvolvableTokenType>>().unwrap { it }
            val linearId = stateAndRef.state.data.linearId
            logger.info("Receiving request from ${otherSession.counterparty} to be added to the distribution list for $linearId.")
            addPartyToDistributionList(serviceHub, otherSession.counterparty, linearId)
            otherSession.send(FlowResult.Success)
        }
    }

}