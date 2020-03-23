package com.r3.corda.lib.tokens.workflows.utilities

import com.r3.corda.lib.tokens.contracts.states.FungibleToken
import com.r3.corda.lib.tokens.contracts.types.IssuedTokenType
import com.r3.corda.lib.tokens.contracts.types.TokenType
import com.r3.corda.lib.tokens.contracts.utilities.heldBy
import com.r3.corda.lib.tokens.contracts.utilities.issuedBy
import com.r3.corda.lib.tokens.contracts.utilities.of
import net.corda.core.contracts.Amount
import net.corda.core.identity.Party
import java.lang.IllegalArgumentException
import java.math.BigDecimal

/**
 * This is a utility class designed for Java developers to more easily
 * access Kotlin infix functions to build token types.
 */
class TokenBuilder {
    private var longAmount: Long? = null
    private lateinit var amountTokenType: Amount<TokenType>
    private lateinit var amountIssuedTokenType: Amount<IssuedTokenType>
    private lateinit var fungibleToken: FungibleToken

    private fun initializeAmountOrThrow(callbackSetter: () -> Unit): TokenBuilder {
        if (longAmount == null) {
            callbackSetter()
        } else {
            throw IllegalArgumentException("The token amount has already been initialized")
        }
        return this
    }

    fun withAmount(intAmount: Int) = initializeAmountOrThrow { this.longAmount= intAmount.toLong() }
    fun withAmount(longAmount: Long) = initializeAmountOrThrow { this.longAmount = longAmount }
    fun withAmount(doubleAmount: Double) = initializeAmountOrThrow { this.longAmount = doubleAmount.toLong() }
    fun withAmount(bigDecimalAmount: BigDecimal) = initializeAmountOrThrow { this.longAmount = bigDecimalAmount.toLong() }

    fun <T: TokenType> of(t: T): TokenBuilder {
        this.amountTokenType = this.longAmount!!.of(t)
        return this
    }

    fun issuedBy(party: Party): TokenBuilder {
        this.amountIssuedTokenType = when {
            ::amountTokenType.isInitialized -> { this.amountTokenType.issuedBy(party) }
            else -> { throw IllegalArgumentException("An amountTokenType has not been initialized") }
        }
        return this
    }

    fun heldBy(party: Party): TokenBuilder {
        this.fungibleToken = when {
            ::amountIssuedTokenType.isInitialized -> this.amountIssuedTokenType heldBy party
            ::amountTokenType.isInitialized -> this.amountIssuedTokenType heldBy party
            else -> { throw IllegalArgumentException("Neither amountTokenType or amount IssuedTokenType is initialized") }
        }
        return this
    }

    fun resolveAmountTokenType() = this.amountTokenType
    fun resolveAmountIssuedTokenType() = this.amountIssuedTokenType
    fun resolveFungibleState() = this.fungibleToken
}

