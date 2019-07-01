package com.r3.corda.lib.tokens.testing.states

import com.r3.corda.lib.tokens.contracts.types.TokenType


open class Ruble : TokenType {
    override val tokenIdentifier: String
        get() = "рубль"
    override val fractionDigits: Int
        get() = 0

    override fun toString(): String {
        return "Ruble(tokenIdentifier: ${tokenIdentifier}, fractionDigits: ${fractionDigits})"
    }


}

object RUB : Ruble()


open class THING : TokenType {
    override val tokenIdentifier: String = "PTK"
    override val fractionDigits: Int = 0

    override fun toString(): String {
        return "THING(tokenIdentifier: ${tokenIdentifier}, fractionDigits: ${fractionDigits})"
    }

}

object PTK : THING()


data class Appartment(
        override val tokenIdentifier: String = "FOO"
) : TokenType {
    override val fractionDigits: Int = 0
}