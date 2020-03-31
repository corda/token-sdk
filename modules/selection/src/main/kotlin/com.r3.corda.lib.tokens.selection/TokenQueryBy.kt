package com.r3.corda.lib.tokens.selection

import com.r3.corda.lib.tokens.contracts.states.FungibleToken
import net.corda.core.contracts.StateAndRef
import net.corda.core.identity.Party
import net.corda.core.node.services.vault.QueryCriteria

//TODO: After 2.0 we should get rid of queryCriteria, because it was a mistake to expose it in the
data class TokenQueryBy @JvmOverloads constructor(
        val issuer: Party? = null, 
        val predicate: (StateAndRef<FungibleToken>) -> Boolean = { true }, val queryCriteria: QueryCriteria? = null)

internal fun TokenQueryBy.issuerAndPredicate(): (StateAndRef<FungibleToken>) -> Boolean {
    return if (issuer != null) {
        { stateAndRef -> stateAndRef.state.data.amount.token.issuer == issuer && predicate(stateAndRef) }
    } else predicate
}