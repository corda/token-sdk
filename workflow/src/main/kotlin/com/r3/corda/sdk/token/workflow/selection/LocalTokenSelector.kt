package com.r3.corda.sdk.token.workflow.selection

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.sdk.token.contracts.states.FungibleToken
import com.r3.corda.sdk.token.contracts.types.IssuedTokenType
import com.r3.corda.sdk.token.contracts.types.TokenType
import net.corda.core.contracts.Amount
import net.corda.core.contracts.StateAndRef
import net.corda.core.flows.FlowLogic
import net.corda.core.serialization.SerializationToken
import net.corda.core.serialization.SerializeAsToken
import net.corda.core.serialization.SerializeAsTokenContext
import java.lang.IllegalStateException
import java.time.Duration
import java.util.*
import java.util.concurrent.atomic.AtomicReference

class LocalTokenSelector(val vaultObserver: VaultWatcherService) : SerializeAsToken {

    private val mostRecentlyLocked = AtomicReference<Pair<List<StateAndRef<FungibleToken<TokenType>>>, String>>()

    @Suspendable
    fun <T : TokenType> selectTokens(
            owner: Any,
            amountRequested: Amount<IssuedTokenType<T>>,
            predicate: ((StateAndRef<FungibleToken<TokenType>>) -> Boolean) = { true },
            allowShortfall: Boolean = false,
            autoUnlockDelay: Duration = Duration.ofMinutes(5),
            selectionId: String = FlowLogic.currentTopLevel?.runId?.uuid?.toString() ?: UUID.randomUUID().toString()
    ): List<StateAndRef<FungibleToken<T>>> {
        synchronized(mostRecentlyLocked) {
            if (mostRecentlyLocked.get() == null) {
                return vaultObserver.selectTokens(owner, amountRequested, predicate, allowShortfall, autoUnlockDelay, selectionId).also { mostRecentlyLocked.set(it as List<StateAndRef<FungibleToken<TokenType>>> to selectionId) }
            } else {
                throw IllegalStateException("Each instance can only used to select tokens once")
            }
        }
    }


    override fun toToken(context: SerializeAsTokenContext): SerializationToken {
        val lockedStateAndRefs = mostRecentlyLocked.get() ?: listOf<StateAndRef<FungibleToken<TokenType>>>() to ""
        return Token(lockedStateAndRefs.first, lockedStateAndRefs.second)
    }

    private class Token(val lockedStateAndRefs: List<StateAndRef<FungibleToken<TokenType>>>, val selectionId: String) : SerializationToken {
        override fun fromToken(context: SerializeAsTokenContext): LocalTokenSelector {
            val watcherService = context.serviceHub.cordaService(VaultWatcherService::class.java)
            watcherService.lockTokensExternal(lockedStateAndRefs, knownSelectionId = selectionId)
            return LocalTokenSelector(watcherService)
        }
    }
}