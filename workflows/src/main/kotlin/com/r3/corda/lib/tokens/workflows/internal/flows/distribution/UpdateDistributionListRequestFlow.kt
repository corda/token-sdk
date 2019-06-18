package com.r3.corda.lib.tokens.workflows.internal.flows.distribution

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.lib.tokens.contracts.states.EvolvableTokenType
import com.r3.corda.lib.tokens.contracts.types.TokenPointer
import net.corda.core.flows.FlowLogic
import net.corda.core.identity.Party

class UpdateDistributionListRequestFlow<T : EvolvableTokenType>(
        val tokenPointer: TokenPointer<T>,
        val sender: Party,
        val receiver: Party
) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        val evolvableToken = tokenPointer.pointer.resolve(serviceHub).state.data
        val distributionListUpdate = DistributionListUpdate(sender, receiver, evolvableToken.linearId)
        val maintainers = evolvableToken.maintainers
        val maintainersSessions = maintainers.map(::initiateFlow)
        maintainersSessions.forEach {
            it.send(distributionListUpdate)
        }
    }
}