package com.r3.corda.sdk.token.contracts.types

import net.corda.core.identity.Party
import net.corda.core.serialization.CordaSerializable
import java.math.BigDecimal

/**
 * A type to wrap a token amount that is issued on the ledger. This might be a ledger native asset such as
 * an equity which is issued directly on to the ledger, in which case the issuer _IS_ the securities issuer.
 *
 * In other cases, the token would represent a depository receipt. In this case the issuer would be the custodian or
 * securities firm which has issued the token on the ledger and holds the underlying security as an asset on their
 * balance sheet -  the amount of token they had issued would be the corresponding liability. The issuer of the
 * underlying security held in custody would be implied via some of information contained within the token type state.
 * E.g. a stock symbol or ISIN.
 *
 * Note: Possible name confusion with corda core "net.corda.corda.contracts.Issued" which is not used by the Token SDK.
 */
@CordaSerializable
data class IssuedToken<out T : EmbeddableToken>(val issuer: Party, val product: T) : Token by product {
    override fun toString(): String = "$product issued by ${issuer.name.organisation}"
    override val displayTokenSize: BigDecimal get() = product.displayTokenSize
}