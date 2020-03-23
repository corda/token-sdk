package com.r3.corda.lib.tokens.workflows.utilities

import com.r3.corda.lib.tokens.contracts.states.FungibleToken
import com.r3.corda.lib.tokens.contracts.types.IssuedTokenType
import com.r3.corda.lib.tokens.contracts.types.TokenType
import com.r3.corda.lib.tokens.contracts.utilities.amount
import com.r3.corda.lib.tokens.contracts.utilities.heldBy
import com.r3.corda.lib.tokens.contracts.utilities.issuedBy
import com.r3.corda.lib.tokens.contracts.utilities.of
import com.r3.corda.lib.tokens.workflows.TokenBuilderException
import net.corda.core.contracts.Amount
import net.corda.core.identity.Party
import java.math.BigDecimal

/**
 * A utility class designed for Java developers to more easily access Kotlin DSL
 * functions to build token types.
 *
 * This function vaguely follows a builder pattern, design choices were made
 * to emulate Kotlin syntax as closely as possible for an easily transferable
 * developer experience in Java.
 */
class TokenBuilder {
    private var longAmount: Long? = null
    private var intAmount: Int? = null
    private var doubleAmount: Double? = null
    private var bigDecimalAmount: BigDecimal? = null
    private lateinit var amountTokenType: Amount<TokenType>
    private lateinit var amountIssuedTokenType: Amount<IssuedTokenType>
    private lateinit var fungibleToken: FungibleToken

    /**
     * This is a helper function that will set one of the viable amount types if none has been
     * previously provided to the builder. If another amount type has been previously
     * provided, it will throw an exception.
     *
     * @param callbackSetter A function that sets a particular member property
     */
    private fun initializeAmountOrThrow(callbackSetter: () -> Unit): TokenBuilder {
        if (listOf(longAmount, intAmount, doubleAmount, bigDecimalAmount).all { it == null }) {
            callbackSetter()
        } else {
            throw TokenBuilderException("The token amount has already been initialized")
        }
        return this
    }

    /**
     * Gets the amount value that has been initialized.
     */
    private fun getInitializedAmount() = listOfNotNull(longAmount, intAmount, doubleAmount, bigDecimalAmount).single()

    /**
     * Allow the developer to add an amount in any of the following classes to the TokenBuilder
     * [Long], [Int], [Double], [BigDecimal]. The types of the amount values must be preserved
     * as their eventual conversion to [Long] requires information from the [TokenType] which
     * may or may not be initialized yet.
     */

    /**
     * Initialize the [longAmount] member property of the builder. Throws an exception if another
     * possible amount has already been initialized.
     *
     * @param longAmount The [Long] we will use to set the [longAmount] member property.
     */
    fun withAmount(longAmount: Long) = initializeAmountOrThrow { this.longAmount = longAmount }

    /**
     * Initialize the [intAmount] member property of the builder. Throws an exception if another
     * possible amount has already been initialized.
     *
     * @param intAmount The [Int] we will use to set the [intAmount] member property.
     */
    fun withAmount(intAmount: Int) = initializeAmountOrThrow { this.intAmount = intAmount }

    /**
     * Initialize the [doubleAmount] member property of the builder. Throws an exception if another
     * possible amount has already been initialized.
     *
     * @param doubleAmount The [Double] we will use to set the [doubleAmount] member property.
     */
    fun withAmount(doubleAmount: Double) = initializeAmountOrThrow { this.doubleAmount = doubleAmount }

    /**
     * Initialize the [bigDecimalAmount] member property of the builder. Throws an exception if another
     * possible amount has already been initialized.
     *
     * @param bigDecimalAmount The [BigDecimal] we will use to set the [bigDecimalAmount] member property.
     */
    fun withAmount(bigDecimalAmount: BigDecimal) = initializeAmountOrThrow { this.bigDecimalAmount = bigDecimalAmount }

    /**
     * Replicates the Kotlin DSL [of] infix function. After supplying a [TokenType] to the builder
     * the developer will be able to call [resolveAmountTokenType] to access the [amountTokenType]
     * member property.
     *
     * @param t The token type that will be used to initialize the [amountTokenType] member property.
     */
    fun <T: TokenType> of(t: T): TokenBuilder = when (getInitializedAmount()) {
            is Long -> {
                this.amountTokenType = amount(this.longAmount!!, t)
                this
            }
            is Int -> {
                this.amountTokenType = amount(this.intAmount!!, t)
                this
            }
            is Double -> {
                this.amountTokenType = amount(this.doubleAmount!!, t)
                this
            }
            is BigDecimal -> {
                this.amountTokenType = amount(this.bigDecimalAmount!!, t)
                this
            }
            else -> { throw TokenBuilderException("The builder has failed, an appropriate amount type was not provided.") }
    }

    /**
     * Replicates the Kotlin DSL [issuedBy] infix function. After supplying a [Party] to the builder
     * representing the identity of the issuer the developer will be able to call [resolveAmountTokenType]
     * to access the [amountIssuedTokenType] member property.
     *
     * @param party The issuing identity that will be used to initialize the [amountIssuedTokenType] member property.
     */
    fun issuedBy(party: Party): TokenBuilder {
        this.amountIssuedTokenType = when {
            ::amountTokenType.isInitialized -> { this.amountTokenType.issuedBy(party) }
            else -> { throw TokenBuilderException("An amountTokenType has not been initialized") }
        }
        return this
    }

    /**
     * Replicates the Kotlin DSL [heldBy] infix function. After supplying a [Party] to the builder
     * representing the identity of the holder of a new fungible token, the developer will be able
     * to call [resolveAmountIssuedTokenType] to access the [fungibleToken] member property.
     *
     * @param party The identity of the holder that will be used to initialize the [amountIssuedTokenType]
     * member property.
     */
    fun heldBy(party: Party): TokenBuilder {
        this.fungibleToken = when {
            ::amountIssuedTokenType.isInitialized -> { this.amountIssuedTokenType heldBy party }
            else -> { throw TokenBuilderException("Neither amountTokenType or amount IssuedTokenType is initialized") }
        }
        return this
    }

    /**
     * A simple get function that resolves to the [amountTokenType] value.
     * This function will throw a not initialized exception if the appropriate
     * builder methods have not been called: [withAmount], [of].
     */
    fun resolveAmountTokenType() = this.amountTokenType

    /**
     * A simple get function that resolves to the [amountIssuedTokenType] value.
     * This function will throw a not initialized exception if the appropriate
     * builder methods have not been called: [withAmount], [of], [issuedBy].
     */
    fun resolveAmountIssuedTokenType() = this.amountIssuedTokenType

    /**
     * A simple get function that resolves to the [amountIssuedTokenType] value.
     * This function will throw a not initialized exception if the appropriate
     * builder methods have not been called: [withAmount], [of], [issuedBy], [heldBy].
     */
    fun resolveFungibleToken() = this.fungibleToken
}

