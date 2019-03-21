package com.r3.corda.sdk.token.workflow.selection

import co.paralleluniverse.common.util.ConcurrentSet
import com.r3.corda.sdk.token.contracts.states.FungibleToken
import net.corda.core.contracts.StateAndRef
import net.corda.core.node.AppServiceHub
import net.corda.core.node.services.CordaService
import net.corda.core.node.services.vault.DEFAULT_PAGE_NUM
import net.corda.core.node.services.vault.PageSpecification
import java.security.PublicKey
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.ConcurrentMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicMarkableReference

@CordaService
class VaultWatcherService(val appServiceHub: AppServiceHub) {


    private val unlocker = Executors.newSingleThreadScheduledExecutor()


    //owner -> tokenClass -> tokenIdentifier -> List
    private val cache: TokenCache = ConcurrentHashMap()

    //will be used to stop adding and removing AtomicMarkableReference for every single consume / add event.
    private val freeSpaceQueue: FreeSlotQueue = ConcurrentHashMap()

    init {

        val pageSize = 1000;
        var currentPage = DEFAULT_PAGE_NUM;
        var (existingStates, observable) = appServiceHub.vaultService.trackBy(FungibleToken::class.java, PageSpecification(pageNumber = currentPage, pageSize = pageSize))
        val listOfThings = mutableListOf<StateAndRef<FungibleToken<*>>>()
        while (existingStates.states.isNotEmpty()) {
            listOfThings.addAll(existingStates.states)
            existingStates = appServiceHub.vaultService.queryBy(FungibleToken::class.java, PageSpecification(pageNumber = ++currentPage, pageSize = pageSize))
        }

        listOfThings.forEach {
            val token = it.state.data
            val owner = token.holder.owningKey
            val type = token.amount.token.tokenType.tokenClass
            val typeId = token.amount.token.tokenType.tokenIdentifier
            val tokensForTypeInfo = getTokenSet(owner, type, typeId)
            tokensForTypeInfo.add(AtomicMarkableReference(it, false))
        }


    }

    private fun getTokenSet(
        owner: PublicKey,
        type: Class<*>,
        typeId: String
    ): ConcurrentSet<AtomicMarkableReference<StateAndRef<FungibleToken<*>>>> {
        val tokensForTypeInfo = cache.computeIfAbsent(owner) {
            ConcurrentHashMap()
        }.computeIfAbsent(type) {
            ConcurrentHashMap()
        }.computeIfAbsent(typeId) {
            ConcurrentSet(ConcurrentHashMap())
        }
        return tokensForTypeInfo
    }

    private fun getNextFreeSlot(
        owner: PublicKey,
        type: Class<*>,
        typeId: String
    ): AtomicMarkableReference<StateAndRef<FungibleToken<*>>>? {
        return freeSpaceQueue.get(owner)?.get(type)?.get(typeId)?.peek()
    }
}

typealias TokenCache = ConcurrentMap<PublicKey, ConcurrentMap<Class<*>, ConcurrentMap<String, ConcurrentSet<AtomicMarkableReference<StateAndRef<FungibleToken<*>>>>>>>
typealias FreeSlotQueue = ConcurrentMap<PublicKey, ConcurrentMap<Class<*>, ConcurrentMap<String, ConcurrentLinkedQueue<AtomicMarkableReference<StateAndRef<FungibleToken<*>>>>>>>