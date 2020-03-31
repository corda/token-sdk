package com.r3.corda.lib.tokens.selection.memory.selector

import com.r3.corda.lib.tokens.contracts.states.FungibleToken
import com.r3.corda.lib.tokens.contracts.types.IssuedTokenType
import com.r3.corda.lib.tokens.contracts.types.TokenType
import com.r3.corda.lib.tokens.selection.TokenQueryBy
import com.r3.corda.lib.tokens.selection.api.Selector
import com.r3.corda.lib.tokens.selection.issuerAndPredicate
import com.r3.corda.lib.tokens.selection.memory.internal.Holder
import com.r3.corda.lib.tokens.selection.memory.services.VaultWatcherService
import net.corda.core.contracts.Amount
import net.corda.core.contracts.StateAndRef
import net.corda.core.node.ServiceHub
import net.corda.core.serialization.SerializationToken
import net.corda.core.serialization.SerializeAsToken
import net.corda.core.serialization.SerializeAsTokenContext
import java.time.Duration
import java.util.*
import java.util.concurrent.atomic.AtomicReference

/**
 * Selector that should be used from your flow when you want to do in memory token selection opposed to [DatabaseTokenSelection].
 * This is experimental feature for now. It was designed to remove potential performance bottleneck and remove the requirement
 * for database specific SQL to be provided for each backend.
 * To use it, you need to have `VaultWatcherService` installed as a `CordaService` on node startup. Indexing
 * strategy could be specified via cordapp configuration, see [ConfigSelection]. You can index either by PublicKey or by ExternalId if using accounts feature.
 *
 * @property services ServiceHub available from the flow
 * @property vaultObserver corda service that watches and caches new states
 * @property autoUnlockDelay Time after which the tokens that are not spent will be automatically released. Defaults to Duration.ofMinutes(5).
 */
class LocalTokenSelector @JvmOverloads constructor(
	override val services: ServiceHub,
	private val vaultObserver: VaultWatcherService,
	private val autoUnlockDelay: Duration = Duration.ofMinutes(5),
	state: Pair<List<StateAndRef<FungibleToken>>, String>? = null // Used for deserializing
) : SerializeAsToken, Selector() {
	constructor(services: ServiceHub) : this(services, getVaultObserver(services))

	companion object {
		@JvmStatic
		private fun getVaultObserver(services: ServiceHub): VaultWatcherService {
			return services.cordaService(VaultWatcherService::class.java)
		}
	}

	private val mostRecentlyLocked = AtomicReference<Pair<List<StateAndRef<FungibleToken>>, String>>(state)

	override protected fun selectTokens(
		holder: Holder,
		lockId: UUID,
		requiredAmount: Amount<TokenType>,
		queryBy: TokenQueryBy
	): List<StateAndRef<FungibleToken>> {
		synchronized(mostRecentlyLocked) {
			if (mostRecentlyLocked.get() == null) {
				val additionalPredicate = queryBy.issuerAndPredicate()
				return vaultObserver.selectTokens(
					holder, requiredAmount, additionalPredicate, false, autoUnlockDelay, lockId.toString()
				).also { mostRecentlyLocked.set(it to lockId.toString()) }
			} else {
				throw IllegalStateException("Each instance can only used to select tokens once")
			}
		}
	}

	// For manual rollback
	fun rollback() {
		val lockedStates = mostRecentlyLocked.get()
		lockedStates?.first?.forEach {
			vaultObserver.unlockToken(it, lockedStates.second)
		}
		mostRecentlyLocked.set(null)
	}

	override fun toToken(context: SerializeAsTokenContext): SerializationToken {
		val lockedStateAndRefs = mostRecentlyLocked.get() ?: listOf<StateAndRef<FungibleToken>>() to ""
		return SerialToken(lockedStateAndRefs.first, lockedStateAndRefs.second, autoUnlockDelay)
	}

	private class SerialToken(val lockedStateAndRefs: List<StateAndRef<FungibleToken>>, val selectionId: String, val autoUnlockDelay: Duration) :
		SerializationToken {
		override fun fromToken(context: SerializeAsTokenContext): LocalTokenSelector {
			val watcherService = context.serviceHub.cordaService(VaultWatcherService::class.java)
			watcherService.lockTokensExternal(lockedStateAndRefs, knownSelectionId = selectionId)
			return LocalTokenSelector(context.serviceHub, watcherService, state = lockedStateAndRefs to selectionId, autoUnlockDelay = autoUnlockDelay)
		}
	}
}
