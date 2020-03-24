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
import java.lang.Exception
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
    private lateinit var tokenType: TokenType
    private lateinit var issuer: Party
    private lateinit var holder: Party

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
    private fun getInitializedAmount() = listOfNotNull(longAmount, intAmount, doubleAmount, bigDecimalAmount).singleOrNull()

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
    fun withAmountValue(longAmount: Long) = initializeAmountOrThrow { this.longAmount = longAmount }

    /**
     * Initialize the [intAmount] member property of the builder. Throws an exception if another
     * possible amount has already been initialized.
     *
     * @param intAmount The [Int] we will use to set the [intAmount] member property.
     */
    fun withAmountValue(intAmount: Int) = initializeAmountOrThrow { this.intAmount = intAmount }

    /**
     * Initialize the [doubleAmount] member property of the builder. Throws an exception if another
     * possible amount has already been initialized.
     *
     * @param doubleAmount The [Double] we will use to set the [doubleAmount] member property.
     */
    fun withAmountValue(doubleAmount: Double) = initializeAmountOrThrow { this.doubleAmount = doubleAmount }

    /**
     * Initialize the [bigDecimalAmount] member property of the builder. Throws an exception if another
     * possible amount has already been initialized.
     *
     * @param bigDecimalAmount The [BigDecimal] we will use to set the [bigDecimalAmount] member property.
     */
    fun withAmountValue(bigDecimalAmount: BigDecimal) = initializeAmountOrThrow { this.bigDecimalAmount = bigDecimalAmount }

    /**
     * Replicates the Kotlin DSL [of] infix function. Supplies a [TokenType] to the builder
     * which will be used to build an [Amount] of a [TokenType].
     *
     * @param t The token type that will be used to build an [Amount] of a [TokenType]
     */
    fun <T: TokenType> of(t: T): TokenBuilder = this.apply {
        this.tokenType = t
    }

    /**
     * Replicates the Kotlin DSL [issuedBy] infix function. Supplies a [Party] to the builder
     * representing the identity of the issuer of an [Amount] of an [IssuedTokenType].
     *
     * @param party The issuing identity that will be used to build an [Amount] of an [IssuedTokenType]
     */
    fun issuedBy(party: Party): TokenBuilder = this.apply {
        this.issuer = party
    }

    /**
     * Replicates the Kotlin DSL [heldBy] infix function. Supplies a [Party] to the builder
     * representing the identity of the holder of a new fungible token.
     *
     * @param party The identity of the holder that will be used to build an [Amount] of an [IssuedTokenType].
     */
    fun heldBy(party: Party) = this.apply {
        this.holder = party
    }

    /**
     * Builds an [Amount] of a [TokenType]. This function will throw a [TokenBuilderException]
     * exception if the appropriate builder methods have not been called: [withAmountValue], [of].
     */
    fun buildAmountTokenType(): Amount<TokenType> = try {
        when (getInitializedAmount()) {
            is Long -> { amount(this.longAmount!!, tokenType) }
            is Int -> amount(this.intAmount!!, tokenType)
            is Double -> { amount(this.doubleAmount!!, tokenType) }
            is BigDecimal -> { amount(this.bigDecimalAmount!!, tokenType) }
            else -> { throw TokenBuilderException("The builder has failed, an appropriate amount type was not provided.") }
        }
    } catch (ex: UninitializedPropertyAccessException) {
        throw TokenBuilderException("An amount value has not been provided to the builder.")
    }

    /**
     * Builds an [Amount] of an [IssuedTokenType]. This function will throw a [TokenBuilderException]
     * if the appropriate builder methods have not been called: [withAmountValue], [of], [issuedBy].
     */
    fun buildAmountIssuedTokenType(): Amount<IssuedTokenType> = when {
        ::issuer.isInitialized -> { buildAmountTokenType() issuedBy issuer }
        else -> { throw TokenBuilderException("An token issuer has not been provided to the builder.") }
    }

    /**
     * Builds an [FungibleToken] state. This function will throw a [TokenBuilderException]
     * if the appropriate builder methods have not been called: [withAmountValue], [of], [issuedBy], [heldBy].
     */
    fun buildFungibleToken() = when {
        ::holder.isInitialized -> { buildAmountIssuedTokenType() heldBy holder }
        else -> { throw TokenBuilderException("A token holder has not been provided to the builder.") }
    }
}

