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

data class InMemorySelectionConfig @JvmOverloads constructor(val enabled: Boolean,
                                   val indexingStrategies: List<VaultWatcherService.IndexingType>,
                                   val cacheSize: Int = CACHE_SIZE_DEFAULT) : StateSelectionConfig {
    companion object {
        private val logger = LoggerFactory.getLogger("inMemoryConfigSelectionLogger")

        @JvmStatic
        fun parse(config: CordappConfig): InMemorySelectionConfig {
            val enabled = if (!config.exists("stateSelection.inMemory.enabled")) {
                logger.warn("Did not detect a configuration for InMemory selection - enabling memory usage for token indexing. Please set stateSelection.inMemory.enabled to \"false\" to disable this")
                true
            } else {
                config.getBoolean("stateSelection.inMemory.enabled")
            }
            val cacheSize = config.getIntOrNull("stateSelection.inMemory.cacheSize")
                    ?: CACHE_SIZE_DEFAULT
            val indexingType = try {
                (config.get("stateSelection.inMemory.indexingStrategies") as List<*>).map { VaultWatcherService.IndexingType.valueOf(it.toString()) }
            } catch (e: CordappConfigException) {
                logger.warn("No indexing method specified. Indexes will be created at run-time for each invocation of selectTokens")
                emptyList<VaultWatcherService.IndexingType>()
            } catch (e: ClassCastException) {
                logger.warn("No indexing method specified. Indexes will be created at run-time for each invocation of selectTokens")
                emptyList<VaultWatcherService.IndexingType>()
            }
            logger.info("Found in memory token selection configuration with values indexing strategy: $indexingType, cacheSize: $cacheSize")
            return InMemorySelectionConfig(enabled, indexingType, cacheSize)
        }

        fun defaultConfig(): InMemorySelectionConfig {
            return InMemorySelectionConfig(true, emptyList())
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
