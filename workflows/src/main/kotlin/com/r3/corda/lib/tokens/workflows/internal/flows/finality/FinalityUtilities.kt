package com.r3.corda.lib.tokens.workflows.internal.flows.finality

import net.corda.core.serialization.CordaSerializable

@CordaSerializable
enum class TransactionRole { PARTICIPANT, OBSERVER }