package com.r3.corda.lib.tokens.selection.memory.config

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.lib.tokens.selection.api.StateSelectionConfig
import com.r3.corda.lib.tokens.selection.memory.selector.LocalTokenSelector
import com.r3.corda.lib.tokens.selection.memory.services.VaultWatcherService
import net.corda.core.cordapp.CordappConfig
import net.corda.core.cordapp.CordappConfigException
import net.corda.core.node.ServiceHub
import org.slf4j.LoggerFactory

const val CACHE_SIZE_DEFAULT = 1024 // TODO Return good default, for now it's not wired, it will be done in separate PR.

data class InMemorySelectionConfig(val indexingStrategy: VaultWatcherService.IndexingType, val cacheSize: Int = CACHE_SIZE_DEFAULT) : StateSelectionConfig {
    companion object {
        val logger = LoggerFactory.getLogger("inMemoryConfigSelectionLogger")
        fun parse(config: CordappConfig): InMemorySelectionConfig {
            val cacheSize = config.getIntOrNull("stateSelection.inMemory.cacheSize")
                    ?: CACHE_SIZE_DEFAULT
            val indexingType = try {
                VaultWatcherService.IndexingType.valueOf(config.get("stateSelection.inMemory.indexingStrategy").toString().toUpperCase())
            } catch (e: CordappConfigException) {
                throw RuntimeException()
            }
            logger.info("Found in memory token selection configuration with values indexing strategy: $indexingType, cacheSize: $cacheSize")
            return InMemorySelectionConfig(indexingType, cacheSize)
        }
    }

    @Suspendable
    override fun toSelector(services: ServiceHub): LocalTokenSelector {
        return try {
            val vaultObserver = services.cordaService(VaultWatcherService::class.java)
            LocalTokenSelector(services, vaultObserver, state = null)
        } catch (e: IllegalArgumentException) {
            throw IllegalArgumentException("Couldn't find VaultWatcherService in CordaServices, please make sure that it was installed in node.")
        }
    }
}

// Helpers for configuration parsing.

fun CordappConfig.getIntOrNull(path: String): Int? {
    return try {
        getInt(path)
    } catch (e: CordappConfigException) {
        if (exists(path)) {
            throw IllegalArgumentException("Provide correct database selection configuration for config path: $path")
        } else {
            null
        }
    }
}
