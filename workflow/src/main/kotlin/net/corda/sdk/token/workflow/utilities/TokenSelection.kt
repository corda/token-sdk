package net.corda.sdk.token.workflow.utilities

import net.corda.core.contracts.Amount
import net.corda.core.contracts.StateAndRef
import net.corda.core.node.services.Vault
import net.corda.core.node.services.VaultService
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.node.services.vault.Sort
import net.corda.core.node.services.vault.SortAttribute
import net.corda.core.utilities.toNonEmptySet
import net.corda.sdk.token.contracts.states.OwnedTokenAmount
import net.corda.sdk.token.contracts.types.EmbeddableToken
import java.util.*

// Sorts by state ref ascending.
private fun defaultSorter(): Sort {
    val sortAttribute = SortAttribute.Standard(Sort.CommonStateAttribute.STATE_REF)
    return Sort(setOf(Sort.SortColumn(sortAttribute, Sort.Direction.ASC)))
}

// Picks owned token amounts with the specified token to the specified amount.
fun <T : EmbeddableToken> VaultService.selectOwnedTokenAmountsForSpending(
        amount: Amount<T>,
        lockId: UUID,
        additionalCriteria: QueryCriteria = ownedTokenAmountCriteria(amount.token),
        sorter: Sort = defaultSorter()
): List<StateAndRef<OwnedTokenAmount<T>>> {

    if (amount.quantity == 0L) {
        return emptyList()
    }

    // Enrich QueryCriteria with additional default attributes (such as soft locks).
    // We only want to return RELEVANT states here.
    val baseCriteria = QueryCriteria.VaultQueryCriteria(
            contractStateTypes = setOf(OwnedTokenAmount::class.java),
            softLockingCondition = QueryCriteria.SoftLockingCondition(QueryCriteria.SoftLockingType.UNLOCKED_AND_SPECIFIED, listOf(lockId)),
            relevancyStatus = Vault.RelevancyStatus.RELEVANT,
            status = Vault.StateStatus.UNCONSUMED
    )

    val results = queryBy<OwnedTokenAmount<T>>(baseCriteria.and(additionalCriteria), sorter)

    var claimedAmount = 0L
    val claimedStates = mutableListOf<StateAndRef<OwnedTokenAmount<T>>>()
    for (state in results.states) {
        claimedStates += state
        claimedAmount += state.state.data.amount.quantity
        if (claimedAmount > amount.quantity) {
            break
        }
    }

    if (claimedStates.isEmpty()) {
        return emptyList()
    }

    // TODO: Create custom exception types.
    if (claimedAmount < amount.quantity) {
        throw IllegalStateException("Not enough tokens of the specified type.")
    }

    softLockReserve(lockId, claimedStates.map { it.ref }.toNonEmptySet())

    return claimedStates.toList()
}

//fun <T : EmbeddableToken> unconsumedCashStatesForSpending(
//        services: ServiceHub,
//        amount: Amount<T>,
//        lockId: UUID
//): List<StateAndRef<OwnedTokenAmount<T>>> {
//    val stateAndRefs = mutableListOf<OwnedTokenAmount<T>>()
//
//    for (retryCount in 1..maxRetries) {
//        if (!attemptSpend(services, amount, lockId, notary, onlyFromIssuerParties, withIssuerRefs, stateAndRefs)) {
//            log.warn("Coin selection failed on attempt $retryCount")
//            // TODO: revisit the back off strategy for contended spending.
//            if (retryCount != maxRetries) {
//                stateAndRefs.clear()
//                val durationMillis = (minOf(retrySleep.shl(retryCount), retryCap / 2) * (1.0 + Math.random())).toInt()
//                FlowLogic.sleep(durationMillis.millis)
//            } else {
//                log.warn("Insufficient spendable states identified for $amount")
//            }
//        } else {
//            break
//        }
//    }
//    return stateAndRefs
//}