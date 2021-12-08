package com.r3.corda.lib.tokens.selection.memory.services

import com.r3.corda.lib.tokens.contracts.states.FungibleToken
import com.r3.corda.lib.tokens.selection.memory.config.InMemorySelectionConfig
import com.r3.corda.lib.tokens.selection.sortByTimeStampAscending
import io.github.classgraph.ClassGraph
import net.corda.core.node.AppServiceHub
import net.corda.core.node.services.Vault
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.DEFAULT_PAGE_NUM
import net.corda.core.node.services.vault.PageSpecification
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.utilities.contextLogger
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class ServiceHubAsyncLoader(private val appServiceHub: AppServiceHub,
                            private val configOptions: InMemorySelectionConfig) : ((Vault.Update<FungibleToken>) -> Unit) -> Unit {


    override fun invoke(
            onVaultUpdate: (Vault.Update<FungibleToken>) -> Unit
    ) {
        LOG.info("Starting async token loading from vault")

        val classGraph = ClassGraph()
        classGraph.enableClassInfo()

        val scanResultFuture = CompletableFuture.supplyAsync {
            classGraph.scan()
        }

        scanResultFuture.thenApplyAsync { scanResult ->
            val subclasses: Set<Class<out FungibleToken>> = scanResult.getSubclasses(FungibleToken::class.java.canonicalName)
                    .map { it.name }
                    .map { Class.forName(it) as Class<out FungibleToken> }.toSet()

            val enrichedClasses = (subclasses - setOf(FungibleToken::class.java))
            LOG.info("Enriching token query with types: $enrichedClasses")

            val shouldLoop = AtomicBoolean(true)
            val pageNumber = AtomicInteger(DEFAULT_PAGE_NUM - 1)
            val loadingFutures: List<CompletableFuture<Void>> = 0.until(configOptions.loadingThreads).map {
                CompletableFuture.runAsync {
                    try {
                        while (shouldLoop.get()) {
                            LOG.info("loading page: ${pageNumber.get() + 1}, should loop: ${shouldLoop.get()}")
                            val newlyLoadedStates = appServiceHub.vaultService.queryBy<FungibleToken>(
                                    paging = PageSpecification(pageNumber = pageNumber.addAndGet(1), pageSize = configOptions.pageSize),
                                    criteria = QueryCriteria.VaultQueryCriteria(contractStateTypes = subclasses),
                                    sorting = sortByTimeStampAscending()
                            ).states.toSet()
                            onVaultUpdate(Vault.Update(emptySet(), newlyLoadedStates))
                            LOG.info("publishing ${newlyLoadedStates.size} to async state loading callback")
                            shouldLoop.compareAndSet(true, newlyLoadedStates.isNotEmpty())
                            LOG.debug("shouldLoop=${shouldLoop}")
                            if (configOptions.sleep > 0) {
                                Thread.sleep(configOptions.sleep.toLong() * 1000)
                            }
                        }
                    } catch (t: Throwable) {
                        LOG.error("Token Loading Failed due to: ", t)
                    }
                }
            }
            CompletableFuture.allOf(*loadingFutures.toTypedArray()).thenRunAsync {
                LOG.info("finished token loading")
            }
        }
    }

    companion object {
        val LOG = contextLogger()
    }
}