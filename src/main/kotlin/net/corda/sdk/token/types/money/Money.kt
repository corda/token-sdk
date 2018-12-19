package net.corda.sdk.token.types.money

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import net.corda.sdk.token.types.token.Token

/**
 * Interface for all things money/money. Unfortunately, the Java Currency type doesn't cater for digital assets,
 * therefore we must create our own type. The Jackson annotations have been added to aid deserialization within the
 * node shell.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes(
        JsonSubTypes.Type(value = FiatCurrency::class, name = "fiat"),
        JsonSubTypes.Type(value = DigitalCurrency::class, name = "digital")
)
abstract class Money : Token.FixedDefinition() {
    abstract val symbol: String
    abstract val description: String
}



