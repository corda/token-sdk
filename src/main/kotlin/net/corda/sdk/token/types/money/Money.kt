package net.corda.sdk.token.types.money

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import net.corda.sdk.token.types.FixedToken

/**
 * Interface for all things money. Unfortunately, the Java Currency type doesn't cater for digital assets,
 * therefore we must create our own for cryptos. The Jackson annotations have been added to aid deserialization within
 * the node shell. Money is a [FixedToken] for now as although sometimes currency properties _do_ change, it doesn't
 * happen very often.
 *
 * So the idea here is that we will distribute these classes with the Token SDK, so anyone with the SDK can issue
 * [Money] tokens. going forward, some networks might want to create their own currency definitions as evolvable token
 * definitions, and of course, they can do that.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes(
        JsonSubTypes.Type(value = FiatCurrency::class, name = "fiat"),
        JsonSubTypes.Type(value = DigitalCurrency::class, name = "digital")
)
abstract class Money : FixedToken() {
    abstract val description: String
}



