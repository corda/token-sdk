package com.r3.corda.sdk.token.money

import com.r3.corda.sdk.token.contracts.types.FixedTokenType

/**
 * Interface for all things [Money]. Unfortunately, the Java Currency type doesn't cater for digital assets,
 * therefore we must create our own for digital currency. The Jackson annotations have been added to aid deserialization
 * within the node shell. Money is a [FixedTokenType] for now as although sometimes currency properties _do_ change, it
 * doesn't happen very often. The idea here is that we will distribute these classes with the Token SDK, so anyone with
 * the SDK can issue [Money] tokens. Going forward, some networks might want to create their own currency definitions as
 * [EvolvableTokenType]s, and of course, they are welcome to do that.
 */
abstract class Money : FixedTokenType() {
    abstract val description: String
}



