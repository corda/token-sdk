package com.r3.corda.sdk.token.workflow.flows.confidential

import net.corda.core.serialization.CordaSerializable

@CordaSerializable
enum class ActionRequest { DO_NOTHING, CREATE_NEW_KEY }