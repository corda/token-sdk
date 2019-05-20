package com.r3.corda.sdk.token.workflow.flows.finality

import net.corda.core.serialization.CordaSerializable

@CordaSerializable
enum class TransactionRole { PARTICIPANT, OBSERVER }