package com.r3.corda.lib.tokens.workflows.internal.selection

import com.r3.corda.lib.tokens.contracts.states.FungibleToken
import net.corda.core.contracts.StateAndRef
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.node.services.vault.QueryCriteria
import java.util.*

//// TODO This approach or the one below?
//interface QueryBy {
//    /**
//     * TODO
//     */
//    val predicate: ((StateAndRef<FungibleToken>) -> Boolean)
//
//    /**
//     * TODO
//     */
//    val queryCriteria: QueryCriteria
//}
//
//class QueryByHolder(val token: TokenType, val holder: AbstractParty) : QueryBy {
//    override val predicate: (StateAndRef<FungibleToken>) -> Boolean
//        get() = { stateAndRef ->
//            val data = stateAndRef.state.data
//            data.holder == holder && data.tokenType == token
//        }
//    override val queryCriteria: QueryCriteria
//        get() = heldTokenAmountCriteria(token, holder)
//}
//
//class QueryByIssuer(val token: TokenType, val issuer: Party) : QueryBy {
//    override val predicate: (StateAndRef<FungibleToken>) -> Boolean
//        get() = { stateAndRef ->
//            val data = stateAndRef.state.data
//            data.amount.token.issuer == issuer && data.tokenType == token
//        }
//
//    override val queryCriteria: QueryCriteria
//        get() = tokenAmountWithIssuerCriteria(token, issuer)
//}
//
////class QueryByExternalId(val externalId: UUID) : QueryBy {
////    override val predicate: (StateAndRef<FungibleToken>) -> Boolean
////        get() = { stateAndRef ->
////            stateAndRef.state.data.
////        }
////
////    override val queryCriteria: QueryCriteria
////        get() = TODO("not implemented") //To change initializer of created properties use File | Settings | File Templates.
////}
//
//class QueryByNotary(val token: TokenType, val notary: Party): QueryBy {
//    override val predicate: (StateAndRef<FungibleToken>) -> Boolean
//        get() = { stateAndRef ->
////            val data = stateAndRef.state.data
//            stateAndRef.state.notary == notary && stateAndRef.state.data.tokenType == token
//        }
//    override val queryCriteria: QueryCriteria
//        get() {
////            val notaryCriteria = QueryCriteria.VaultCustomQueryCriteria(builder {
////                PersistentFungibleToken::
////            })
//            return tokenAmountCriteria(token) // TODO figure out how to extract the notary from it
//        }
//
//}
//
//// Combination of criteria.
//class QueryAnd(private val left: QueryBy, private val right: QueryBy): QueryBy {
//    override val predicate: (StateAndRef<FungibleToken>) -> Boolean
//        get() =  { it -> left.predicate(it) && right.predicate(it) }
//    override val queryCriteria: QueryCriteria
//        get() = left.queryCriteria.and(right.queryCriteria)
//}
//
//class QueryOr(private val left: QueryBy, private val right: QueryBy): QueryBy {
//    override val predicate: (StateAndRef<FungibleToken>) -> Boolean
//        get() = { it -> left.predicate(it) || right.predicate(it) }
//    override val queryCriteria: QueryCriteria
//        get() = left.queryCriteria.or(right.queryCriteria)
//}

// TODO POC for introducing some type safety
// + token type and identifier
sealed class Holder {
    class HolderParty(val holder: AbstractParty): Holder()
    class HolderExternalId(val externalId: UUID): Holder()
}

// TODO other approach, I don't like Any in owner to be honest, it should be either external id or public key/Abstract party
data class TokenQueryBy(val holder: Any? = null, val issuer: Party? = null, val predicate: (StateAndRef<FungibleToken>) -> Boolean = { true }, val queryCriteria: QueryCriteria? = null) {
    fun issuerAndPredicate(): (StateAndRef<FungibleToken>) -> Boolean {
        return if (issuer != null) {
            { it ->  issuerPredicate(issuer).invoke(it) && predicate(it) }
        } else predicate
    }
}

// then we could query by party/externalId + issuer + apply filtering on the results afterwards
class TokenQueryBy2(val holder: Holder? = null, val issuer: Party? = null, val predicate: (StateAndRef<FungibleToken>) -> Boolean = { true }, val queryCriteria: QueryCriteria? = null)