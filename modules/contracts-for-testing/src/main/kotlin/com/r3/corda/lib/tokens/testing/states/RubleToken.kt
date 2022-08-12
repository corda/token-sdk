@file:JvmName("Rubles")

package com.r3.corda.lib.tokens.testing.states

import com.r3.corda.lib.tokens.contracts.FungibleTokenContract
import com.r3.corda.lib.tokens.contracts.states.FungibleToken
import com.r3.corda.lib.tokens.contracts.types.IssuedTokenType
import net.corda.core.contracts.Amount
import net.corda.core.contracts.BelongsToContract
import net.corda.core.identity.AbstractParty

/**
 * Test class only used to test that tokens cannot change class during a move
 */
@BelongsToContract(FungibleTokenContract::class)
open class RubleToken(amount: Amount<IssuedTokenType>, holder: AbstractParty) : FungibleToken(amount, holder)
