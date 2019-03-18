package com.r3.corda.sdk.token.workflow.selection

import com.r3.corda.sdk.token.contracts.states.FungibleToken
import net.corda.core.node.AppServiceHub
import net.corda.core.node.services.CordaService
import net.corda.core.node.services.vault.DEFAULT_PAGE_NUM
import net.corda.core.node.services.vault.PageSpecification
import java.security.PublicKey
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap
import java.util.concurrent.Executors

@CordaService
class VaultWatcherService(val appServiceHub: AppServiceHub) {


    private val unlocker = Executors.newSingleThreadScheduledExecutor()
    private val cache: ConcurrentMap<PublicKey, ConcurrentMap<String, List<FungibleToken<*>>>> = ConcurrentHashMap()

    init {

        val pageSize = 1000;
        var currentPage = DEFAULT_PAGE_NUM;
        var (existingStates, observable) = appServiceHub.vaultService.trackBy(FungibleToken::class.java, PageSpecification(pageNumber = currentPage, pageSize = pageSize))
        val listOfThings = mutableListOf<FungibleToken<*>>()
        while (existingStates.states.isNotEmpty()) {
            listOfThings.addAll(existingStates.states.map { it.state.data })
            existingStates = appServiceHub.vaultService.queryBy(FungibleToken::class.java, PageSpecification(pageNumber = ++currentPage, pageSize = pageSize))
        }

        listOfThings.forEach {
            val owner = it.holder.owningKey
            val typeId = it.amount.token.tokenType.tokenClass + it.amount.token.tokenType.tokenIdentifier
            val ownerCache = cache.computeIfAbsent(owner) {
                ConcurrentHashMap()
            }

        }


    }
}