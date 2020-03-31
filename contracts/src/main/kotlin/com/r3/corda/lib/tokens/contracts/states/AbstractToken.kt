package com.r3.corda.lib.tokens.contracts.states

import com.r3.corda.lib.tokens.contracts.types.IssuedTokenType
import com.r3.corda.lib.tokens.contracts.types.TokenType
import net.corda.core.contracts.ContractState
import net.corda.core.crypto.SecureHash
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party

/** Contains common token properties and functionality. */
interface AbstractToken : ContractState {
    /** The [AbstractParty] which is currently holding (some amount of) tokens. */
    val holder: AbstractParty

    /**
     * The default participant is the current [holder]. However, this can be overridden if required. The standard
     * [FungibleToken] and [NonFungibleToken] states assume that the [holder] is the only participant but they can be
     * sub-classed so an observers list or "CC" list can be added.
     *
     * It is likely that this approach will need to be revisited at the Corda core level, at some point in the near
     * future, as there are some issues with how the participants list interacts with other Corda features, for example
     * notary change transactions and contract upgrade transactions.
     */
    override val participants: List<AbstractParty> get() = listOf(holder)

    /** The [TokenType] this [AbstractToken] is in respect of. */
    val tokenType: TokenType get() = issuedTokenType.tokenType

    /** The [IssuedTokenType]. */
    val issuedTokenType: IssuedTokenType

    /** The issuer [Party]. */
    val issuer: Party get() = issuedTokenType.issuer

    /** For creating a copy of an existing [AbstractToken] with a new holder. */
    fun withNewHolder(newHolder: AbstractParty): AbstractToken

    /** The hash of a CorDapp JAR which implements the [TokenType] specified by the type parameter [T]. */
    val tokenTypeJarHash: SecureHash?
}