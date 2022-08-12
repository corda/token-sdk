package com.r3.corda.lib.tokens.testing.tokentypes

import com.r3.corda.lib.tokens.contracts.types.TokenType

data class Apartment(val id: String = "Foo") : TokenType(id, 0)