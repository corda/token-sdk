package com.r3.corda.sdk.token.workflow.flows.internal.distribution

import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.Party
import net.corda.core.serialization.CordaSerializable

@CordaSerializable
data class DistributionListUpdate(val sender: Party, val receiver: Party, val linearId: UniqueIdentifier)
