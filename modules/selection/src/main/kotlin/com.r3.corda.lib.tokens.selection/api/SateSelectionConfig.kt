package com.r3.corda.lib.tokens.selection.api

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.lib.tokens.selection.database.config.DatabaseSelectionConfig
import com.r3.corda.lib.tokens.selection.memory.config.InMemorySelectionConfig
import net.corda.core.cordapp.CordappConfig
import net.corda.core.node.ServiceHub
import org.slf4j.LoggerFactory

interface StateSelectionConfig {
	@Suspendable
	fun toSelector(services: ServiceHub): Selector
}

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
 *          indexingStrategy: ["external_id"|"public_key"|"token_only"]
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