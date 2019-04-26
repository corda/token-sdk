package com.r3.corda.sdk.token.workflow.utilities

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.cordapp.CordappConfig
import net.corda.core.cordapp.CordappConfigException
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.internal.randomOrNull
import net.corda.core.node.ServiceHub
import net.corda.core.transactions.TransactionBuilder

// TODO getPreferredNotary should be loaded on start?
/**
 * Gets the preferred notary from the CorDapp config file. Otherwise, the list of notaries from the network map cache
 * is returned. From this list the CorDapp developer can employ their own strategy to choose a notary. for now, the
 * strategies will be to either choose the first notary or a random notary from the list.
 *
 * @param services a [ServiceHub] instance.
 * @param backupSelector a function which selects a notary when the notary property is not set in the CorDapp config.
 * @return the selected notary [Party] object.
 */
// TODO no suspendable
// TODO if suspendable then not lambda
@Suspendable
fun getPreferredNotary(services: ServiceHub, backupSelector: (ServiceHub) -> Party = firstNotary()): Party {
    val notaryString = try {
        val config: CordappConfig = services.getAppContext().config
        config.getString("notary")
    } catch(e: CordappConfigException) {
        ""
    }
    return if (notaryString.isBlank()) {
        backupSelector(services)
    } else {
        val notaryX500Name = CordaX500Name.parse(notaryString)
        val notaryParty = services.networkMapCache.getNotary(notaryX500Name)
                ?: throw IllegalStateException("Notary with name \"$notaryX500Name\" cannot be found in the network " +
                        "map cache. Either the notary does not exist, or there is an error in the config.")
        notaryParty
    }
}

/** Choose the first notary in the list. */
@Suspendable
fun firstNotary() = { services: ServiceHub ->
    services.networkMapCache.notaryIdentities.first()
}

/** Choose a random notary from the list. */
@Suspendable
fun randomNotary() = { services: ServiceHub ->
    services.networkMapCache.notaryIdentities.randomOrNull()
            ?: throw IllegalArgumentException("No available notaries.")
}

/** Choose a random non validating notary. */
@Suspendable
fun randomNonValidatingNotary() = { services: ServiceHub ->
    services.networkMapCache.notaryIdentities.filter { notary ->
        services.networkMapCache.isValidatingNotary(notary).not()
    }.randomOrNull()
}

/** Choose a random validating notary. */
@Suspendable
fun randomValidatingNotary() = { services: ServiceHub ->
    services.networkMapCache.notaryIdentities.filter { notary ->
        services.networkMapCache.isValidatingNotary(notary)
    }.randomOrNull()
}

/** Adds a notary to a new [TransactionBuilder]. If the notary is already set then this  */
@Suspendable
fun addNotary(services: ServiceHub, txb: TransactionBuilder): TransactionBuilder {
    return txb.apply { notary = getPreferredNotary(services) }
}

/**
 * Adds notary if not set. Otherwise checks if it's the same as the one in TransactionBuilder.
 */
// TODO Internal, because for now useful only when selecting tokens and passing builder around.
internal fun addNotaryWithCheck(txb: TransactionBuilder, notary: Party): TransactionBuilder {
    if (txb.notary == null) {
        txb.notary = notary
    }
    check(txb.notary == notary) {
        "Notary passed to transaction builder (${txb.notary}) should be the same as the one used by input states ($notary)."
    }
    return txb
}