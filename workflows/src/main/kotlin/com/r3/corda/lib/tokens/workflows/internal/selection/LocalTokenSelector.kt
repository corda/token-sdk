package com.r3.corda.lib.tokens.workflows.internal.selection

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.lib.tokens.contracts.states.FungibleToken
import com.r3.corda.lib.tokens.contracts.types.IssuedTokenType
import net.corda.core.contracts.Amount
import net.corda.core.contracts.StateAndRef
import net.corda.core.flows.FlowLogic
import net.corda.core.serialization.SerializationToken
import net.corda.core.serialization.SerializeAsToken
import net.corda.core.serialization.SerializeAsTokenContext
import java.time.Duration
import java.util.*
import java.util.concurrent.atomic.AtomicReference

class LocalTokenSelector(private val vaultObserver: VaultWatcherService, state: Pair<List<StateAndRef<FungibleToken>>, String>? = null) : SerializeAsToken {

    private val mostRecentlyLocked = AtomicReference<Pair<List<StateAndRef<FungibleToken>>, String>>(state)

    @Suspendable
    fun selectTokens(
            owner: Any,
            amountRequested: Amount<IssuedTokenType>,
            predicate: ((StateAndRef<FungibleToken>) -> Boolean) = { true },
            allowShortfall: Boolean = false,
            autoUnlockDelay: Duration = Duration.ofMinutes(5),
            selectionId: String = FlowLogic.currentTopLevel?.runId?.uuid?.toString() ?: UUID.randomUUID().toString()
    ): List<StateAndRef<FungibleToken>> {
        synchronized(mostRecentlyLocked) {
            if (mostRecentlyLocked.get() == null) {
                return vaultObserver.selectTokens(owner, amountRequested, predicate, allowShortfall, autoUnlockDelay, selectionId).also { mostRecentlyLocked.set(it to selectionId) }
            } else {
                throw IllegalStateException("Each instance can only used to select tokens once")
            }
        }
    }

    @Suspendable
    fun rollback() {
        val lockedStates = mostRecentlyLocked.get()
        lockedStates?.first?.forEach {
            vaultObserver.unlockToken(it, lockedStates.second)
        }
        mostRecentlyLocked.set(null)
    }


    override fun toToken(context: SerializeAsTokenContext): SerializationToken {
        val lockedStateAndRefs = mostRecentlyLocked.get() ?: listOf<StateAndRef<FungibleToken>>() to ""
        return Token(lockedStateAndRefs.first, lockedStateAndRefs.second)
    }

    private class Token(val lockedStateAndRefs: List<StateAndRef<FungibleToken>>, val selectionId: String) : SerializationToken {
        override fun fromToken(context: SerializeAsTokenContext): LocalTokenSelector {
            val watcherService = context.serviceHub.cordaService(VaultWatcherService::class.java)
            watcherService.lockTokensExternal(lockedStateAndRefs, knownSelectionId = selectionId)
            return LocalTokenSelector(watcherService, lockedStateAndRefs to selectionId)
        }
    }
}