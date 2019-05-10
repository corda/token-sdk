package com.r3.corda.sdk.token.workflow.flows.move

import com.r3.corda.sdk.token.contracts.types.TokenType
import net.corda.core.contracts.Amount
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.StartableByRPC
import net.corda.core.identity.AbstractParty

/**
 * TODO docs
 * This flow is supposed to be called from shell only.
 */
@InitiatingFlow
@StartableByRPC
class MakeMoveTokenFlow<T : TokenType>(
        partiesAndAmounts: Map<AbstractParty, List<Amount<T>>>,
        partiesAndTokens: Map<AbstractParty, List<T>> = emptyMap()
) : MoveTokensFlow<T>(partiesAndAmounts, partiesAndTokens) {
    constructor(token: T, holder: AbstractParty) : this(emptyMap(), mapOf(holder to listOf(token)))

    constructor(tokens: List<T>, holder: AbstractParty) : this(emptyMap(), mapOf(holder to tokens))

    constructor(amount: Amount<T>, holder: AbstractParty) : this(mapOf(holder to listOf(amount)), emptyMap())

    constructor(amounts: Set<Amount<T>>, holder: AbstractParty) : this(mapOf(holder to amounts.toList()), emptyMap())
}