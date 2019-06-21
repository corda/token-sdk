package com.r3.corda.lib.tokens.money

import com.r3.corda.lib.tokens.contracts.types.TokenType

/**
 * Interface for all things [Money]. Unfortunately, the Java Currency type doesn't cater for digital assets,
 * therefore we must create our own for digital currency. The Jackson annotations have been added to aid deserialization
 * within the node rpc. Money implements [TokenType] for now as although sometimes currency properties _do_ change, it
 * doesn't happen very often. The idea here is that we will distribute these classes with the Token SDK, so anyone with
 * the SDK can issue [Money] tokens. Going forward, some networks might want to create their own currency definitions as
 * [EvolvableTokenType]s, and of course, they are welcome to do that.
 */
interface Money : TokenType {
    val description: String
}



