package com.r3.corda.lib.tokens.contracts.utilities

import com.r3.corda.lib.tokens.contracts.states.AbstractToken
import com.r3.corda.lib.tokens.contracts.states.EvolvableTokenType
import com.r3.corda.lib.tokens.contracts.states.FungibleToken
import com.r3.corda.lib.tokens.contracts.types.IssuedTokenType
import com.r3.corda.lib.tokens.contracts.types.TokenType
import com.r3.corda.lib.tokens.contracts.utilities.TokenUtilities.Companion.logger
import net.corda.core.contracts.Amount
import net.corda.core.contracts.TransactionState
import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.sha256
import net.corda.core.crypto.toStringShort
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.AnonymousParty
import net.corda.core.identity.Party
import net.corda.core.internal.location
import net.corda.core.utilities.contextLogger
import java.util.concurrent.ConcurrentHashMap

class TokenUtilities {
    companion object {
        val logger = contextLogger()

    }
}

// ------------------------------------------------------
// Creates a tokens from (amounts of) issued token types.
// ------------------------------------------------------

/**
 * Creates a [FungibleToken] from an an amount of [IssuedTokenType].
 * E.g. Amount<IssuedTokenType<TokenType>> -> FungibleToken<TokenType>.
 */
infix fun <T : TokenType> Amount<IssuedTokenType<T>>.heldBy(owner: AbstractParty): FungibleToken<T> = _heldBy(owner)

internal infix fun <T : TokenType> Amount<IssuedTokenType<T>>._heldBy(owner: AbstractParty): FungibleToken<T> {
    return FungibleToken(this, owner)
}

// --------------------------
// Add a a notary to a token.
// --------------------------

/** Adds a notary [Party] to an [AbstractToken], by wrapping it in a [TransactionState]. */
infix fun <T : AbstractToken<*>> T.withNotary(notary: Party): TransactionState<T> = _withNotary(notary)

internal infix fun <T : AbstractToken<*>> T._withNotary(notary: Party): TransactionState<T> {
    return TransactionState(data = this, notary = notary)
}

/** Adds a notary [Party] to an [EvolvableTokenType], by wrapping it in a [TransactionState]. */
infix fun <T : EvolvableTokenType> T.withNotary(notary: Party): TransactionState<T> = _withNotary(notary)

internal infix fun <T : EvolvableTokenType> T._withNotary(notary: Party): TransactionState<T> {
    return TransactionState(data = this, notary = notary)
}

/**
 * Converts [AbstractToken.holder] into a more friendly string. It uses only the x500 organisation for [Party] objects
 * and shortens the public key for [AnonymousParty]s to the first 16 characters.
 */
val AbstractToken<*>.holderString: String
    get() =
        (holder as? Party)?.name?.organisation ?: holder.owningKey.toStringShort().substring(0, 16)

inline infix fun <reified T : TokenType> AbstractToken<T>.withNewHolder(newHolder: AbstractParty) = withNewHolder(newHolder)

val attachmentCache = ConcurrentHashMap<Class<*>, SecureHash>()
fun TokenType.getAttachmentIdForGenericParam(): SecureHash {
    return attachmentCache.computeIfAbsent(this.javaClass) {
        //this is an external jar
        if (it.location != TokenType::class.java.location) {
            logger.info("LOOKING FOR JAR WHICH PROVIDES: ${this::class.java} FOUND AT: ${this::class.java.location}")
            it.location.readBytes().sha256()
        } else {
            TokenType::class.java.location.readBytes().sha256()
        }
    }
}

