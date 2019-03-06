package com.r3.corda.sdk.token.contracts.types

import net.corda.core.contracts.Amount
import net.corda.core.identity.Party
import net.corda.core.serialization.CordaSerializable
import java.math.BigDecimal

/**
 * A type to wrap a [TokenType] with an issuing [Party]. The [TokenType] might be a ledger native asset such as an
 * equity which is issued directly on to the ledger, in which case the issuing [Party] IS the securities issuer. In
 * other cases, the [TokenType] would represent a depository receipt. In this case the issuing [Party] would be the
 * custodian or securities firm which has issued the [TokenType] on the ledger and holds the underlying security as an
 * asset on their balance sheet. The [Amount] of a [IssuedTokenType] they had issued would be the corresponding
 * liability. The issuer of the underlying security held in custody would be implied via some of information contained
 * within the token type state. E.g. a stock symbol or ISIN. Note: Possible name confusion with corda core
 * "net.corda.corda.contracts.Issued" which is not used by the Token SDK.
 *
 * @property issuer the [Party] which has issued (some amount of) this [TokenType] on ledger. Note that, in the case of
 * the [TokenType] being a depositary receipt, the issuer is NOT the party with the ultimate liability, instead it is
 * always the party which issued the [TokenType] on ledger.
 * @property tokenType the [TokenType] to be associated with an issuing [Party].
 * @param T the [TokenType].
 */
@CordaSerializable
data class IssuedTokenType<out T : TokenType>(val issuer: Party, val tokenType: T) : TokenType by tokenType {
    override fun toString(): String = "$tokenType issued by ${issuer.name.organisation}"

    /**
     * This is required by [Amount] to determine the default fraction digits when adding or subtracting amounts of
     * [IssuedTokenType].
     */
    override val displayTokenSize: BigDecimal get() = tokenType.displayTokenSize
}