package com.r3.corda.sdk.token.contracts.states

import com.r3.corda.sdk.token.contracts.types.IssuedTokenType
import com.r3.corda.sdk.token.contracts.types.TokenType
import net.corda.core.contracts.ContractState
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party

/** Contains common [NonFungibleToken] functionality. */
interface AbstractToken<T : TokenType> : ContractState {

    /** The [AbstractParty] which is currently holding (some amount of) tokens. */
    val holder: AbstractParty

    /**
     * The default participant is the current [holder]. However, this can be overridden if required. The standard
     * [FungibleToken] and [NonFungibleToken] states assume that the [holder] is the only participant but they can be
     * sub-classed so an observers list or "CC" list can be added.
     * TODO: We will need to revisit this in the future, regarding contract upgrades.
     */
    override val participants: List<AbstractParty> get() = listOf(holder)

    /** The [TokenType]. */
    val tokenType: T get() = issuedTokenType.tokenType

    /** The [IssuedTokenType]. */
    val issuedTokenType: IssuedTokenType<T>

    /** The issuer [Party]. */
    val issuer: Party get() = issuedTokenType.issuer

    /** For creating a copy of an existing [AbstractToken] with a new holder. */
    fun withNewHolder(newHolder: AbstractParty): AbstractToken<T>
}