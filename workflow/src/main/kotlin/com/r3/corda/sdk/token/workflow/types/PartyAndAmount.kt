package com.r3.corda.sdk.token.workflow.types

import com.r3.corda.sdk.token.contracts.types.TokenType
import net.corda.core.contracts.Amount
import net.corda.core.identity.AbstractParty
import net.corda.core.serialization.CordaSerializable

/**
 * A simple holder for a (possibly anonymous) [AbstractParty] and a quantity of tokens.
 * Used in [generateMove] to define what [amount] of token [T] [party] should receive.
 */
@CordaSerializable
data class PartyAndAmount<T : TokenType>(val party: AbstractParty, val amount: Amount<T>)
