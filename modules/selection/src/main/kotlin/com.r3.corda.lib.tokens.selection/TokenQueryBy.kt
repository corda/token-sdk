package com.r3.corda.lib.tokens.selection

import com.r3.corda.lib.tokens.contracts.states.FungibleToken
import net.corda.core.contracts.StateAndRef
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.node.services.vault.QueryCriteria
import java.util.*

// TODO I don't like Any in owner to be honest, it should be sealed class for either external id or public key/Abstract party
data class TokenQueryBy(val holder: Any? = null, val issuer: Party? = null, val predicate: (StateAndRef<FungibleToken>) -> Boolean = { true }, val queryCriteria: QueryCriteria? = null) {
    fun issuerAndPredicate(): (StateAndRef<FungibleToken>) -> Boolean {
        return if (issuer != null) {
            { stateAndRef -> stateAndRef.state.data.amount.token.issuer == issuer && predicate(stateAndRef) }
        } else predicate
    }
}
