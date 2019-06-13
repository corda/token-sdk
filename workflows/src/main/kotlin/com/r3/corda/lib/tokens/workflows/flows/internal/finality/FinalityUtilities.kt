package com.r3.corda.lib.tokens.workflows.flows.internal.finality

import net.corda.core.serialization.CordaSerializable

@CordaSerializable
enum class TransactionRole { PARTICIPANT, OBSERVER }