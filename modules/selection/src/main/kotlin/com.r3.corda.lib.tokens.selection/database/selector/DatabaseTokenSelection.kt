package com.r3.corda.lib.tokens.selection.database.selector

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.lib.tokens.contracts.states.FungibleToken
import com.r3.corda.lib.tokens.contracts.types.IssuedTokenType
import com.r3.corda.lib.tokens.contracts.types.TokenType
import com.r3.corda.lib.tokens.selection.*
import com.r3.corda.lib.tokens.selection.api.Selector
import com.r3.corda.lib.tokens.selection.database.config.MAX_RETRIES_DEFAULT
import com.r3.corda.lib.tokens.selection.database.config.PAGE_SIZE_DEFAULT
import com.r3.corda.lib.tokens.selection.database.config.RETRY_CAP_DEFAULT
import com.r3.corda.lib.tokens.selection.database.config.RETRY_SLEEP_DEFAULT
import com.r3.corda.lib.tokens.selection.memory.internal.Holder
import net.corda.core.contracts.Amount
import net.corda.core.contracts.StateAndRef
import net.corda.core.flows.FlowLogic
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.AnonymousParty
import net.corda.core.node.ServiceHub
import net.corda.core.node.services.Vault
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.DEFAULT_PAGE_NUM
import net.corda.core.node.services.vault.PageSpecification
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.node.services.vault.Sort
import net.corda.core.utilities.contextLogger
import net.corda.core.utilities.millis
import net.corda.core.utilities.toNonEmptySet
import java.util.*

/**
 * TokenType selection using Hibernate. It uses roughly the same logic that the coin selection algorithm used in
 * AbstractCoinSelection within the finance module. The only difference is that now there are not specific database
 * implementations, instead hibernate is used for an agnostic approach.
 *
 * When calling [selectTokens] there is the option to pass in a custom [QueryCriteria]. The default behaviour
 * is to order all states by [StateRef] and query for a specific token type. The default behaviour is probably not very
 * efficient but the behaviour can be customised if necessary.
 *
 * This is only really here as a stopgap solution until in-memory token selection is implemented.
 *
 * @param services for performing vault queries.
 */
class DatabaseTokenSelection @JvmOverloads constructor(
        override val services: ServiceHub,
        private val maxRetries: Int = MAX_RETRIES_DEFAULT,
        private val retrySleep: Int = RETRY_SLEEP_DEFAULT,
        private val retryCap: Int = RETRY_CAP_DEFAULT,
        private val pageSize: Int = PAGE_SIZE_DEFAULT
) : Selector() {

    companion object {
        val logger = contextLogger()
    }

    /**
     * Queries for held token amounts with the specified token to the specified requiredAmount.
     *
     * @return the amount of claimed tokens (effectively the sum of values of the states in [stateAndRefs]
     * */
    private fun executeQuery(
            requiredAmount: Amount<TokenType>,
            lockId: UUID,
            additionalCriteria: QueryCriteria,
            sorter: Sort,
            stateAndRefs: MutableList<StateAndRef<FungibleToken>>,
            includeSoftLocked: Boolean,
            softLockingType: QueryCriteria.SoftLockingType = QueryCriteria.SoftLockingType.UNLOCKED_ONLY
    ): Amount<TokenType> {
        // Didn't need to select any tokens.
        if (requiredAmount.quantity == 0L) {
            return Amount(0, requiredAmount.token)
        }

        // Enrich QueryCriteria with additional default attributes (such as soft locks).
        // We only want to return RELEVANT states here.
        val baseCriteria = if (!includeSoftLocked) {
            QueryCriteria.VaultQueryCriteria(
                contractStateTypes = setOf(FungibleToken::class.java),
                softLockingCondition = QueryCriteria.SoftLockingCondition(softLockingType, listOf(lockId)),
                relevancyStatus = Vault.RelevancyStatus.RELEVANT,
                status = Vault.StateStatus.UNCONSUMED
            )
        } else {
            QueryCriteria.VaultQueryCriteria(
                contractStateTypes = setOf(FungibleToken::class.java),
                relevancyStatus = Vault.RelevancyStatus.RELEVANT,
                status = Vault.StateStatus.UNCONSUMED
            )
        }

        var pageNumber = DEFAULT_PAGE_NUM
        var claimedAmount = 0L

        do {
            val pageSpec = PageSpecification(pageNumber = pageNumber, pageSize = pageSize)
            val results: Vault.Page<FungibleToken> = services.vaultService.queryBy(baseCriteria.and(additionalCriteria), pageSpec, sorter)

            for (state in results.states) {
                stateAndRefs += state
                claimedAmount += state.state.data.amount.quantity
                if (claimedAmount >= requiredAmount.quantity) {
                    break
                }
            }

            pageNumber++
        } while (claimedAmount < requiredAmount.quantity && (pageSpec.pageSize * (pageNumber - 1)) <= results.totalStatesAvailable)

        val claimedAmountWithToken = Amount(claimedAmount, requiredAmount.token)
        // No tokens available.
        if (stateAndRefs.isEmpty()) return Amount(0, requiredAmount.token)

       return claimedAmountWithToken
    }

    /**
     * Queries for held token amounts with the specified token to the specified requiredAmount
     * AND tries to soft lock the selected tokens.
     */
    private fun executeQueryAndReserve(
        requiredAmount: Amount<TokenType>,
        lockId: UUID,
        additionalCriteria: QueryCriteria,
        sorter: Sort,
        stateAndRefs: MutableList<StateAndRef<FungibleToken>>,
        softLockingType: QueryCriteria.SoftLockingType = QueryCriteria.SoftLockingType.UNLOCKED_ONLY
    ): Boolean {
        // not including soft locked tokens
        val claimedAmount = executeQuery(requiredAmount, lockId, additionalCriteria, sorter, stateAndRefs, false, softLockingType)
        return if (claimedAmount >= requiredAmount) {
            // We picked enough tokensToIssue, so softlock and go.
            logger.trace("TokenType selection for $requiredAmount retrieved ${stateAndRefs.count()} states totalling $claimedAmount: $stateAndRefs")
            services.vaultService.softLockReserve(lockId, stateAndRefs.map { it.ref }.toNonEmptySet())
            true
        } else {
            logger.trace("TokenType selection requested $requiredAmount but retrieved $claimedAmount with state refs: ${stateAndRefs.map { it.ref }}")
            false
        }
    }


    @Suspendable
    override fun selectTokens(
            holder: Holder,
            lockId: UUID,
            requiredAmount: Amount<TokenType>,
            queryBy: TokenQueryBy
    ): List<StateAndRef<FungibleToken>> {
        val criteria = constructQueryCriteria(requiredAmount, holder, queryBy)
        val stateAndRefs = mutableListOf<StateAndRef<FungibleToken>>()
        for (retryCount in 1..maxRetries) {
            if (!executeQueryAndReserve(requiredAmount, lockId, criteria, sortByStateRefAscending(), stateAndRefs)) {
                // TODO: Need to specify exactly why it fails. Locked states or literally _no_ states!
                // No point in retrying if there will never be enough...
                logger.warn("TokenType selection failed on attempt $retryCount.")
                // TODO: revisit the back off strategy for contended spending.
                if (retryCount != maxRetries) {
                    stateAndRefs.clear()
                    val durationMillis = (minOf(retrySleep.shl(retryCount), retryCap / 2) * (1.0 + Math.random())).toInt()
                    FlowLogic.sleep(durationMillis.millis)
                } else {
                    // if there is enough soft locked tokens available to satisfy the amount then we need to throw
                    // [InsufficientNotLockedBalanceException] instead
                    val amountWithSoftLocked = executeQuery(requiredAmount, lockId, criteria, sortByStateRefAscending(), mutableListOf(), true)
                    if (amountWithSoftLocked < requiredAmount) {
                        logger.warn("Insufficient spendable states identified for $requiredAmount.")
                        throw InsufficientBalanceException("Insufficient spendable states identified for $requiredAmount.")
                    } else {
                        logger.warn("Insufficient not locked spendable states identified for $requiredAmount.")
                        throw InsufficientNotLockedBalanceException("Insufficient not locked spendable states identified for $requiredAmount.")
                    }
                }
            } else {
                break
            }
        }
        return if (queryBy.predicate != { true }) {
            stateAndRefs.toList().filter { stateAndRef ->
                queryBy.predicate.invoke(stateAndRef)
            }
        } else stateAndRefs
    }

    private fun constructQueryCriteria(requiredAmount: Amount<TokenType>, holder: Holder, queryBy: TokenQueryBy): QueryCriteria {
        // This is due to the fact, that user can pass Amount<IssuedTokenType>, this usually shouldn't happen, but just in case
        val amountToken = requiredAmount.token
        val (token, issuer) = when (amountToken) {
            is IssuedTokenType -> Pair(amountToken.tokenType, amountToken.issuer)
            else -> Pair(amountToken, queryBy.issuer)
        }
        val criteria = holderToCriteria(holder, token).run {
            if (issuer != null) {
                and(tokenAmountWithIssuerCriteria(token, issuer))
            } else this
        }.run {
            if (queryBy.queryCriteria != null) {
                and(queryBy.queryCriteria)
            } else this
        }
        return criteria
    }

    private fun holderToCriteria(holder: Holder, token: TokenType): QueryCriteria {
        return when (holder) {
            is Holder.KeyIdentity -> {
                // We want the AbstractParty that this key refers to, unfortunately, partyFromKey returns always well known party
                // for that key, so afterwards we need to construct AnonymousParty.
                val knownParty: AbstractParty = services.identityService.partyFromKey(holder.owningKey)
                        ?: AnonymousParty(holder.owningKey)
                val holderParty = if (knownParty.owningKey == holder.owningKey) knownParty else AnonymousParty(holder.owningKey)
                tokenAmountWithHolderCriteria(token, holderParty)
            }
            is Holder.MappedIdentity -> tokenAmountCriteria(token).and(QueryCriteria.VaultQueryCriteria(externalIds = listOf(holder.uuid)))
            // TODO After looking at VaultQueryCriteria implemenation of querying by external id we don't really support querying for keys not mapped to external id!
            is Holder.UnmappedIdentity -> tokenAmountCriteria(token)
            is Holder.TokenOnly -> tokenAmountCriteria(token)
        }
    }
}
