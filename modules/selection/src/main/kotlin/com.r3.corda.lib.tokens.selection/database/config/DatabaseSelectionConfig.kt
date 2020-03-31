package com.r3.corda.lib.tokens.selection.database.config

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.lib.tokens.selection.api.ConfigSelection
import com.r3.corda.lib.tokens.selection.api.Selector
import com.r3.corda.lib.tokens.selection.api.StateSelectionConfig
import com.r3.corda.lib.tokens.selection.database.selector.DatabaseTokenSelection
import com.r3.corda.lib.tokens.selection.memory.config.InMemorySelectionConfig
import com.r3.corda.lib.tokens.selection.memory.config.getIntOrNull
import net.corda.core.cordapp.CordappConfig
import net.corda.core.node.ServiceHub
import org.slf4j.LoggerFactory

const val MAX_RETRIES_DEFAULT = 8
const val RETRY_SLEEP_DEFAULT = 100
const val RETRY_CAP_DEFAULT = 2000
const val PAGE_SIZE_DEFAULT = 200

data class DatabaseSelectionConfig @JvmOverloads constructor(
        val maxRetries: Int = MAX_RETRIES_DEFAULT,
        val retrySleep: Int = RETRY_SLEEP_DEFAULT,
        val retryCap: Int = RETRY_CAP_DEFAULT,
        val pageSize: Int = PAGE_SIZE_DEFAULT
) : StateSelectionConfig {
    companion object {
        @JvmStatic
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
