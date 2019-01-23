package net.corda.sdk.token.contracts.types

import net.corda.core.contracts.TokenizableAssetInfo
import net.corda.core.identity.Party
import net.corda.core.serialization.CordaSerializable
import java.math.BigDecimal

/**
 * Note: Name clashes with corda core Issued.
 * A type to wrap a token amount that is issued on the ledger. This might be a ledger native asset such as
 * an equity which is issued directly on to the ledger, in which case the issuer _IS_ the securities issuer. In other
 * cases, the token would represent a depository receipt. In this case the issuer would be the custodian or securities
 * firm which has issued the token on the ledger and holds the underlying security on their balance sheet. The issuer
 * of the security would be implied via some of information contained within the token type state. E.g. a stock symbol
 * or a party object of the securities issuer, if they had a node on the network.
 */

@CordaSerializable
data class Issued<out T : EmbeddableToken>(val issuer: Party, val product: T) : TokenizableAssetInfo {
    override fun toString(): String = "$product issued by ${issuer.name.organisation}"
    override val displayTokenSize: BigDecimal get() = product.displayTokenSize
}