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
    private var intAmount: Int? = null
    private var longAmount: Long? = null
    private var doubleAmount: Double? = null
    private var bigDecimalAmount: BigDecimal? = null
    private lateinit var amountTokenType: Amount<TokenType>
    private lateinit var amountIssuedTokenType: Amount<IssuedTokenType>
    private lateinit var fungibleToken: FungibleToken

    private fun getInitializedAmount() = if (!amountInitialized()) {
        listOfNotNull(intAmount, longAmount, doubleAmount, bigDecimalAmount).single()
    } else {
        throw IllegalArgumentException("The token amount hasn't been initialized")
    }

    private fun amountInitialized() = listOf(intAmount, longAmount, doubleAmount, bigDecimalAmount).all { it != null }
    private fun initializeAmountOrThrow(callbackSetter: () -> Unit): TokenBuilder {
        if (!amountInitialized()) {
            callbackSetter()
        } else {
            throw IllegalArgumentException("The token amount has already been initialized")
        }
        return this
    }

    fun amount(intAmount: Int) = initializeAmountOrThrow { this.intAmount = intAmount }
    fun amount(longAmount: Long) = initializeAmountOrThrow { this.longAmount = longAmount}
    fun amount(doubleAmount: Double) = initializeAmountOrThrow { this.doubleAmount = doubleAmount }
    fun amount(bigDecimalAmount: BigDecimal) = initializeAmountOrThrow { this.bigDecimalAmount = bigDecimalAmount }

    fun <T: TokenType> of(t: T): TokenBuilder = when(getInitializedAmount()) {
        Int -> {
            this.amountTokenType = this.intAmount!!.of(t)
            this
        }
        Long -> {
            this.amountTokenType = this.longAmount!!.of(t)
            this
        }
        Double -> {
            this.amountTokenType = this.doubleAmount!!.of(t)
            this
        }
        getInitializedAmount() is BigDecimal -> {
            this.amountTokenType = this.doubleAmount!!.of(t)
            this
        }
        else -> { throw IllegalArgumentException("This should never happen") }
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
}
