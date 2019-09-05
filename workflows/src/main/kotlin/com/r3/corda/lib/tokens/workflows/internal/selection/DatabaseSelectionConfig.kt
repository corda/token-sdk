package com.r3.corda.lib.tokens.workflows.internal.selection

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.lib.tokens.selection.config.InMemorySelectionConfig
import com.r3.corda.lib.tokens.selection.config.StateSelectionConfig
import com.r3.corda.lib.tokens.selection.config.getIntOrNull
import com.r3.corda.lib.tokens.selection.selectors.Selector
import net.corda.core.cordapp.CordappConfig
import net.corda.core.node.ServiceHub
import org.slf4j.LoggerFactory

const val MAX_RETRIES_DEFAULT = 8
const val RETRY_SLEEP_DEFAULT = 100
const val RETRY_CAP_DEFAULT = 2000
const val PAGE_SIZE_DEFAULT = 200

/**
 * CorDapp config format:
 *
 * stateSelection {
 *      database {
 *          maxRetries: Int
 *          retrySleep: Int
 *          retryCap: Int
 *          pageSize: Int
 *      }
 *      or
 *      in_memory {
 *          indexingStrategy: ["external_id"|"public_key"|"token"]
 *          cacheSize: Int
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
            DatabaseSelectionConfig().toSelector(services) // Return default database selection
        } else {
            if (config.exists("stateSelection.database")) {
                DatabaseSelectionConfig.parse(config).toSelector(services)
            } else if (config.exists("stateSelection.inMemory")) {
                InMemorySelectionConfig.parse(config).toSelector(services)
            } else {
                throw IllegalArgumentException("Provide correct state-selection type string in the config, see kdocs for ConfigSelection.")
            }
        }
    }
}

private data class DatabaseSelectionConfig(
        val maxRetries: Int = MAX_RETRIES_DEFAULT,
        val retrySleep: Int = RETRY_SLEEP_DEFAULT,
        val retryCap: Int = RETRY_CAP_DEFAULT,
        val pageSize: Int = PAGE_SIZE_DEFAULT
) : StateSelectionConfig {
    companion object {
        fun parse(config: CordappConfig): DatabaseSelectionConfig {
            val maxRetries = config.getIntOrNull("stateSelection.database.maxRetries") ?: MAX_RETRIES_DEFAULT
            val retrySleep = config.getIntOrNull("stateSelection.database.retrySleep") ?: RETRY_SLEEP_DEFAULT
            val retryCap = config.getIntOrNull("stateSelection.database.retryCap") ?: RETRY_CAP_DEFAULT
            val pageSize = config.getIntOrNull("stateSelection.database.pageSize") ?: PAGE_SIZE_DEFAULT
            ConfigSelection.logger.info("Found database token selection configuration with values maxRetries: $maxRetries, retrySleep: $retrySleep, retryCap: $retryCap, pageSize: $pageSize")
            return DatabaseSelectionConfig(maxRetries, retrySleep, retryCap, pageSize)
        }
    }

    @Suspendable
    override fun toSelector(services: ServiceHub): DatabaseTokenSelection {
        return DatabaseTokenSelection(services, maxRetries, retrySleep, retryCap, pageSize)
    }
}
