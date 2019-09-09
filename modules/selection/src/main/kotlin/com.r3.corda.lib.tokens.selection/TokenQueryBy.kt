package com.r3.corda.lib.tokens.selection

import com.r3.corda.lib.tokens.contracts.states.FungibleToken
import com.r3.corda.lib.tokens.selection.memory.internal.Holder
import net.corda.core.contracts.StateAndRef
import net.corda.core.identity.Party
import net.corda.core.node.services.vault.QueryCriteria

data class TokenQueryBy(val holder: Holder? = null, val issuer: Party? = null, val predicate: (StateAndRef<FungibleToken>) -> Boolean = { true }, val queryCriteria: QueryCriteria? = null) {
    fun issuerAndPredicate(): (StateAndRef<FungibleToken>) -> Boolean {
        return if (issuer != null) {
            { stateAndRef -> stateAndRef.state.data.amount.token.issuer == issuer && predicate(stateAndRef) }
        } else predicate
    }
}
