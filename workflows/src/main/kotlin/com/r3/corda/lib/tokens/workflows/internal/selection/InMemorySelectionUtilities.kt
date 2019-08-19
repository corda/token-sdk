package com.r3.corda.lib.tokens.workflows.internal.selection

import com.r3.corda.lib.tokens.contracts.states.FungibleToken
import net.corda.core.contracts.StateAndRef
import net.corda.core.identity.Party

fun issuerPredicate(issuer: Party): ((StateAndRef<FungibleToken>) -> Boolean) = {
    stateAndRef -> stateAndRef.state.data.amount.token.issuer == issuer
}