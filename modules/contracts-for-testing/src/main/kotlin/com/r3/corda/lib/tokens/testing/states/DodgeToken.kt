package com.r3.corda.lib.tokens.testing.states

import com.r3.corda.lib.tokens.contracts.states.FungibleToken
import com.r3.corda.lib.tokens.contracts.types.IssuedTokenType
import com.r3.corda.lib.tokens.testing.contracts.DodgeTokenContract
import net.corda.core.contracts.Amount
import net.corda.core.contracts.BelongsToContract
import net.corda.core.identity.AbstractParty

/**
 * Test class only used to test that are grouped by Contract as well as TokenType
 */
@BelongsToContract(DodgeTokenContract::class)
open class DodgeToken(amount: Amount<IssuedTokenType>, holder: AbstractParty) : FungibleToken(amount, holder)