package com.r3.corda.lib.tokens.workflows.internal.selection

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.cordapp.CordappConfig
import net.corda.core.cordapp.CordappConfigException
import net.corda.core.node.ServiceHub
import org.slf4j.LoggerFactory

const val MAX_RETRIES_DEFAULT = 8
const val RETRY_SLEEP_DEFAULT = 100
const val RETRY_CAP_DEFAULT = 2000
const val CACHE_SIZE_DEFAULT = 1024 // TODO Return good default, for now it's not wired, it will be done in separate PR.

/**
 * CorDapp config format:
 *
 * stateSelection {
 *      database {
 *          maxRetries: Int
 *          retrySleep: Int
 *          retryCap: Int
 *      }
 *      or
 *      in_memory {
 *          indexingStrategy: ["external_id"|"public_key"]
 *          cacheSize: Int
 *
 *      }
 * }
 *
 * Use ConfigSelection.getPreferredSelection to choose based on you cordapp config between database token selection and in memory one.
 * By default Move and Redeem methods use config to switch between them. If no config option is provided it will default to database
 * token selection.
 */
object ConfigSelection {
    val logger = LoggerFactory.getLogger("configSelectionLogger")
    @Suspendable
    fun getPreferredSelection(services: ServiceHub, config: CordappConfig = services.getAppContext().config): Selector {
        val hasSelection = config.exists("stateSelection")
        return if (!hasSelection) {
            logger.warn("No configuration for state selection, defaulting to database selection.")
            TokenSelectionConfig().toSelector(services) // Return default database selection
        } else {
            if (config.exists("stateSelection.database")) {
                TokenSelectionConfig.parse(config).toSelector(services)
            } else if (config.exists("stateSelection.inMemory")) {
                InMemorySelectionConfig.parse(config).toSelector(services)
            } else {
                throw IllegalArgumentException("Provide correct state-selection type string in the config, see kdocs for ConfigSelection.")
            }
        }
    }
}

// Private helpers for configuration parsing.

private fun CordappConfig.getIntOrNull(path: String): Int? {
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

private interface StateSelectionConfig {
    @Suspendable
    fun toSelector(services: ServiceHub): Selector
}

private data class TokenSelectionConfig(
        val maxRetries: Int = MAX_RETRIES_DEFAULT,
        val retrySleep: Int = RETRY_SLEEP_DEFAULT,
        val retryCap: Int = RETRY_CAP_DEFAULT
) : StateSelectionConfig {
    companion object {
        fun parse(config: CordappConfig): TokenSelectionConfig {
            val maxRetries = config.getIntOrNull("stateSelection.database.maxRetries") ?: MAX_RETRIES_DEFAULT
            val retrySleep = config.getIntOrNull("stateSelection.database.retrySleep") ?: RETRY_SLEEP_DEFAULT
            val retryCap = config.getIntOrNull("stateSelection.database.retryCap") ?: RETRY_CAP_DEFAULT
            ConfigSelection.logger.info("Found database token selection configuration with values maxRetries: $maxRetries, retrySleep: $retrySleep, retryCap: $retryCap")
            return TokenSelectionConfig(maxRetries, retrySleep, retryCap)
        }
    }

    @Suspendable
    override fun toSelector(services: ServiceHub): TokenSelection {
        return TokenSelection(services, maxRetries, retrySleep, retryCap)
    }
}

data class InMemorySelectionConfig(val indexingStrategy: VaultWatcherService.IndexingType, val cacheSize: Int = CACHE_SIZE_DEFAULT) : StateSelectionConfig {
    companion object {
        fun parse(config: CordappConfig): InMemorySelectionConfig {
            val cacheSize = config.getIntOrNull("stateSelection.inMemory.cacheSize")
                    ?: CACHE_SIZE_DEFAULT
            val indexingType = try {
                VaultWatcherService.IndexingType.valueOf(config.get("stateSelection.inMemory.indexingStrategy").toString().toUpperCase())
            } catch (e: CordappConfigException) {
                VaultWatcherService.IndexingType.PUBLIC_KEY // Default is public key.
            }
            ConfigSelection.logger.info("Found in memory token selection configuration with values indexing strategy: $indexingType, cacheSize: $cacheSize")
            return InMemorySelectionConfig(indexingType, cacheSize)
        }
    }

    @Suspendable
    override fun toSelector(services: ServiceHub): LocalTokenSelector {
        return try {
            val vaultObserver = services.cordaService(VaultWatcherService::class.java)
            LocalTokenSelector(services, vaultObserver, state = null)// TODO allowShortFall and autoUnlockDelay, should it be config or per flow?
        } catch (e: IllegalArgumentException) {
            // TODO Provide some docs reference after I finish the refactor, so it's clear what needs to be done
            throw IllegalArgumentException("Couldn't find VaultWatcherService in CordaServices, please make sure that it was installed in node.")
        }
    }
}
