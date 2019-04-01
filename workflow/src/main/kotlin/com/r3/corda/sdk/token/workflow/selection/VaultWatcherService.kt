package com.r3.corda.sdk.token.workflow.selection

import com.r3.corda.sdk.token.contracts.states.FungibleToken
import com.r3.corda.sdk.token.contracts.types.IssuedTokenType
import com.r3.corda.sdk.token.contracts.types.TokenType
import net.corda.core.contracts.Amount
import net.corda.core.contracts.StateAndRef
import net.corda.core.node.AppServiceHub
import net.corda.core.node.services.CordaService
import net.corda.core.node.services.Vault
import net.corda.core.node.services.vault.DEFAULT_PAGE_NUM
import net.corda.core.node.services.vault.PageSpecification
import net.corda.core.utilities.contextLogger
import java.security.PublicKey
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap
import java.util.concurrent.Executors

@CordaService
class VaultWatcherService(val appServiceHub: AppServiceHub) {

    companion object {
        val LOG = contextLogger()
    }

    private val unlocker = Executors.newSingleThreadScheduledExecutor()

    //owner -> tokenClass -> tokenIdentifier -> List
    private val cache: ConcurrentMap<PublicKey, ConcurrentMap<Class<*>, ConcurrentMap<String, ConcurrentMap<StateAndRef<FungibleToken<TokenType>>, Boolean>>>> = ConcurrentHashMap()

    //will be used to stop adding and removing AtomicMarkableReference for every single consume / add event.

    init {
        val pageSize = 1000
        var currentPage = DEFAULT_PAGE_NUM;
        var (existingStates, observable) = appServiceHub.vaultService.trackBy(FungibleToken::class.java, PageSpecification(pageNumber = currentPage, pageSize = pageSize))
        val listOfThings = mutableListOf<StateAndRef<FungibleToken<TokenType>>>()
        while (existingStates.states.isNotEmpty()) {
            listOfThings.addAll(existingStates.states as Iterable<StateAndRef<FungibleToken<TokenType>>>)
            existingStates = appServiceHub.vaultService.queryBy(FungibleToken::class.java, PageSpecification(pageNumber = ++currentPage, pageSize = pageSize))
        }

        listOfThings.forEach(::addTokenToCache)
        observable.doOnNext(::onVaultUpdate)
    }

    fun onVaultUpdate(t: Vault.Update<FungibleToken<*>>) {
        t.consumed.forEach(::removeTokenFromCache)
        t.produced.forEach(::addTokenToCache)
    }

    fun removeTokenFromCache(it: StateAndRef<FungibleToken<*>>) {
        val (owner, type, typeId) = processToken(it.state.data)
        val tokenSet = getTokenSet(owner, type, typeId)
        if (tokenSet.remove(it) != true) {
            LOG.warn("Received a consumption event for a token ${it.ref} which was either not present in the token cache, or was not locked")
        }
    }

    fun addTokenToCache(stateAndRef: StateAndRef<FungibleToken<*>>) {
        val token = stateAndRef.state.data
        val (owner, type, typeId) = processToken(token)
        val tokensForTypeInfo = getTokenSet(owner, type, typeId)
        val existingMark = tokensForTypeInfo.putIfAbsent(stateAndRef as StateAndRef<FungibleToken<TokenType>>, false)
        existingMark?.let {
            LOG.warn("Attempted to overwrite existing token ${stateAndRef.ref} during cache initialization, this suggests incorrect vault behaviours")
        }
    }

    fun selectTokens(
            owner: PublicKey,
            amountRequested: Amount<IssuedTokenType<*>>
    ) {
        val set = getTokenSet(owner, amountRequested.token.tokenType.tokenClass, amountRequested.token.tokenType.tokenIdentifier)
        val lockedTokens = mutableListOf<StateAndRef<FungibleToken<*>>>()
        var amountLocked = amountRequested.copy(quantity = 0)
        for (tokenStateAndRef in set.keys) {
            val token = tokenStateAndRef.state.data
            val existingMark = set.computeIfPresent(tokenStateAndRef) { _, _ ->
                true
            }
            if (existingMark == false) {
                //TOKEN was unlocked, but now is locked
                lockedTokens.add(tokenStateAndRef)
            } else if (existingMark == null) {
                //TOKEN was removed before we could lock it
            }
            amountLocked += token.amount
            if (amountLocked >= amountRequested) {
                break
            }
        }

    }

    private fun processToken(token: FungibleToken<*>): Triple<PublicKey, Class<*>, String> {
        val owner = token.holder.owningKey
        val type = token.amount.token.tokenType.tokenClass
        val typeId = token.amount.token.tokenType.tokenIdentifier
        return Triple(owner, type, typeId)
    }

    private fun getTokenSet(
            owner: PublicKey,
            type: Class<*>,
            typeId: String
    ): ConcurrentMap<StateAndRef<FungibleToken<TokenType>>, Boolean> {
        return cache.computeIfAbsent(owner) {
            ConcurrentHashMap()
        }.computeIfAbsent(type) {
            ConcurrentHashMap()
        }.computeIfAbsent(typeId) {
            ConcurrentHashMap()
        }
    }
}

