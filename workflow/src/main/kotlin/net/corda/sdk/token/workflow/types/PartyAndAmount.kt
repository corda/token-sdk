package net.corda.sdk.token.workflow.types

import net.corda.core.contracts.Amount
import net.corda.core.identity.AbstractParty
import net.corda.sdk.token.contracts.types.EmbeddableToken

/** A simple holder for a (possibly anonymous) [AbstractParty] and a quantity of tokens */
data class PartyAndAmount<T : EmbeddableToken>(val party: AbstractParty, val amount: Amount<T>)