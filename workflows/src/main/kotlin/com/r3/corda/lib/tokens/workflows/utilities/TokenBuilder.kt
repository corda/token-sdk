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
    private var amount: BigDecimal? = null
    private lateinit var tokenType: TokenType
    private lateinit var issuer: Party
    private lateinit var holder: Party

    /**
     * Set the [amount] member property of the builder using a provided [Long].
     *
     * @param longAmount The [Long] that will be converted to set the [amount] member property
     */
    fun withAmount(longAmount: Long) = this.apply { amount = BigDecimal.valueOf(longAmount) }

    /**
     * Set the [amount] member property of the builder using a provided [Int].
     *
     * @param intAmount The [Int] that will be converted to set the [amount] member property
     */
    fun withAmount(intAmount: Int) = this.apply { amount = BigDecimal(intAmount) }

    /**
     * Set the [amount] member property of the builder using a provided [Double].
     *
     * @param doubleAmount The [Double] that will be converted to set the [amount] member property
     */
    fun withAmount(doubleAmount: Double) = this.apply { amount = BigDecimal.valueOf(doubleAmount) }

    /**
     * Set the [amount] member property of the builder using a provided [BigDecimal].
     *
     * @param bigDecimal The [BigDecimal] that will be used to set the [amount] member property
     */
    fun withAmount(bigDecimalAmount: BigDecimal) = this.apply { amount = bigDecimalAmount }

    /**
     * Replicates the Kotlin DSL [of] infix function. Supplies a [TokenType] to the builder
     * which will be used to build an [Amount] of a [TokenType].
     *
     * @param t The token type that will be used to build an [Amount] of a [TokenType]
     */
    fun <T: TokenType> of(t: T): TokenBuilder = this.apply { this.tokenType = t }

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
     * exception if the appropriate builder methods have not been called: [withAmount], [of].
     */
    @Throws(TokenBuilderException::class)
    fun buildAmountTokenType(): Amount<TokenType> = when {
        !::tokenType.isInitialized -> { throw TokenBuilderException("A Token Type has not been provided to the builder.") }
        amount == null -> { throw TokenBuilderException("An amount value has not been provided to the builder.") }
        else -> { amount!! of tokenType }
    }

    /**
     * Builds an [Amount] of an [IssuedTokenType]. This function will throw a [TokenBuilderException]
     * if the appropriate builder methods have not been called: [withAmount], [of], [issuedBy].
     */
    @Throws(TokenBuilderException::class)
    fun buildAmountIssuedTokenType(): Amount<IssuedTokenType> = when {
        ::issuer.isInitialized -> { buildAmountTokenType() issuedBy issuer }
        else -> { throw TokenBuilderException("An token issuer has not been provided to the builder.") }
    }

    /**
     * Builds an [FungibleToken] state. This function will throw a [TokenBuilderException]
     * if the appropriate builder methods have not been called: [withAmount], [of], [issuedBy], [heldBy].
     */
    @Throws(TokenBuilderException::class)
    fun buildFungibleToken() = when {
        ::holder.isInitialized -> { buildAmountIssuedTokenType() heldBy holder }
        else -> { throw TokenBuilderException("A token holder has not been provided to the builder.") }
    }
}
