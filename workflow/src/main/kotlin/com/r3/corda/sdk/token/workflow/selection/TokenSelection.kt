package com.r3.corda.sdk.token.workflow.selection

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.sdk.token.contracts.commands.MoveTokenCommand
import com.r3.corda.sdk.token.contracts.commands.RedeemTokenCommand
import com.r3.corda.sdk.token.contracts.states.FungibleToken
import com.r3.corda.sdk.token.contracts.types.IssuedTokenType
import com.r3.corda.sdk.token.contracts.types.TokenType
import com.r3.corda.sdk.token.contracts.utilities.issuedBy
import com.r3.corda.sdk.token.contracts.utilities.sumTokenStateAndRefs
import com.r3.corda.sdk.token.contracts.utilities.withNotary
import com.r3.corda.sdk.token.workflow.types.PartyAndAmount
import com.r3.corda.sdk.token.workflow.utilities.ownedTokenAmountCriteria
import com.r3.corda.sdk.token.workflow.utilities.sortByStateRefAscending
import net.corda.core.contracts.Amount
import net.corda.core.contracts.Amount.Companion.sumOrThrow
import net.corda.core.contracts.StateAndRef
import net.corda.core.contracts.TransactionState
import net.corda.core.flows.FlowLogic
import net.corda.core.identity.AbstractParty
import net.corda.core.node.ServiceHub
import net.corda.core.node.services.Vault
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.node.services.vault.Sort
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.contextLogger
import net.corda.core.utilities.millis
import net.corda.core.utilities.toNonEmptySet
import java.security.PublicKey
import java.util.*

/**
 * TokenType selection using Hibernate. It uses roughly the same logic that the coin selection algorithm used in
 * AbstractCoinSelection within the finance module. The only difference is that now there are not specific database
 * implementations, instead hibernate is used for an agnostic approach.
 *
 * When calling [attemptSpend] there is the option to pass in a custom [QueryCriteria] and [Sort]. The default behaviour
 * is to order all states by [StateRef] and query for a specific token type. The default behaviour is probably not very
 * efficient but the behaviour can be customised if necessary.
 *
 * This is only really here as a stopgap solution until in-memory token selection is implemented.
 *
 * @param services for performing vault queries.
 */
class TokenSelection(val services: ServiceHub, private val maxRetries: Int = 8, private val retrySleep: Int = 100, private val retryCap: Int = 2000) {

    companion object {
        val logger = contextLogger()
    }

    /** Queries for owned token amounts with the specified token to the specified requiredAmount. */
    private fun <T : TokenType> executeQuery(
            requiredAmount: Amount<T>,
            lockId: UUID,
            additionalCriteria: QueryCriteria,
            sorter: Sort,
            stateAndRefs: MutableList<StateAndRef<FungibleToken<T>>>
    ): Boolean {
        // Didn't need to select any tokens.
        if (requiredAmount.quantity == 0L) {
            return false
        }

        // Enrich QueryCriteria with additional default attributes (such as soft locks).
        // We only want to return RELEVANT states here.
        val baseCriteria = QueryCriteria.VaultQueryCriteria(
                contractStateTypes = setOf(FungibleToken::class.java),
                softLockingCondition = QueryCriteria.SoftLockingCondition(QueryCriteria.SoftLockingType.UNLOCKED_AND_SPECIFIED, listOf(lockId)),
                relevancyStatus = Vault.RelevancyStatus.RELEVANT,
                status = Vault.StateStatus.UNCONSUMED
        )

        // TODO: Add paging in case there are not enough states in one page to fill the required requiredAmount. E.g. Dust.
        val results: Vault.Page<FungibleToken<T>> = services.vaultService.queryBy(baseCriteria.and(additionalCriteria), sorter)

        var claimedAmount = 0L
        for (state in results.states) {
            stateAndRefs += state
            claimedAmount += state.state.data.amount.quantity
            if (claimedAmount >= requiredAmount.quantity) {
                break
            }
        }

        val claimedAmountWithToken = Amount(claimedAmount, requiredAmount.token)
        // No tokens available.
        if (stateAndRefs.isEmpty()) return false
        // There were not enough tokens available.
        if (claimedAmountWithToken < requiredAmount) {
            logger.trace("TokenType selection requested $requiredAmount but retrieved $claimedAmountWithToken with state refs: ${stateAndRefs.map { it.ref }}")
            return false
        }

        // We picked enough tokens, so softlock and go.
        logger.trace("TokenType selection for $requiredAmount retrieved ${stateAndRefs.count()} states totalling $claimedAmountWithToken: $stateAndRefs")
        services.vaultService.softLockReserve(lockId, stateAndRefs.map { it.ref }.toNonEmptySet())
        return true
    }


    /**
     * Attempt spend of [requiredAmount] of [FungibleToken] T. Returns states that cover given amount. Notice that this function
     * doesn't calculate change. If query criteria is not specified then only owned token amounts are used.
     * Use [QueryUtilities.tokenAmountWithIssuerCriteria] to specify issuer.
     *
     * @return List of [FungibleToken]s that satisfy the amount to spend, empty list if none found.
     */
    @Suspendable
    fun <T : TokenType> attemptSpend(
            requiredAmount: Amount<T>,
            lockId: UUID,
            additionalCriteria: QueryCriteria = ownedTokenAmountCriteria(requiredAmount.token),
            sorter: Sort = sortByStateRefAscending()
    ): List<StateAndRef<FungibleToken<T>>> {
        val stateAndRefs = mutableListOf<StateAndRef<FungibleToken<T>>>()
        for (retryCount in 1..maxRetries) {
            // TODO: Need to specify exactly why it fails. Locked states or literally _no_ states!
            // No point in retrying if there will never be enough...
            if (!executeQuery(requiredAmount, lockId, additionalCriteria, sorter, stateAndRefs)) {
                logger.warn("TokenType selection failed on attempt $retryCount.")
                // TODO: revisit the back off strategy for contended spending.
                if (retryCount != maxRetries) {
                    stateAndRefs.clear()
                    val durationMillis = (minOf(retrySleep.shl(retryCount), retryCap / 2) * (1.0 + Math.random())).toInt()
                    FlowLogic.sleep(durationMillis.millis)
                } else {
                    logger.warn("Insufficient spendable states identified for $requiredAmount.")
                    // TODO: Create new exception type.
                    throw IllegalStateException("Insufficient spendable states identified for $requiredAmount.")
                }
            } else {
                break
            }
        }
        return stateAndRefs.toList()
    }

    /**
     * Generate move of certain amount of [FungibleToken] T to the [recipient]. This function mutates [builder] provided as parameter.
     * If [changeOwner] is provided, then the change outputs (if present) will be returned back to this identity. If not,
     * then fresh confidential identity for the caller will be created.
     *
     * @return [TransactionBuilder] and list of all owner keys used in the input states that are going to be moved.
     */
    @Suspendable
    fun <T : TokenType> generateMove(
            builder: TransactionBuilder,
            amount: Amount<T>,
            recipient: AbstractParty,
            queryCriteria: QueryCriteria? = null,
            changeOwner: AbstractParty? = null
    ): Pair<TransactionBuilder, List<PublicKey>> {
        return generateMove(builder, listOf(PartyAndAmount(recipient, amount)), queryCriteria, changeOwner)
    }

    /**
     * Generate move of [FungibleToken] T to parties specified in [PartyAndAmount]. Each party will receive amount defined
     * by [partyAndAmounts]. If query criteria is not specified then only owned token amounts are used. Use [QueryUtilities.tokenAmountWithIssuerCriteria]
     * to specify issuer. This function mutates [builder] provided as parameter.
     * If [changeOwner] is provided, then the change outputs (if present) will be returned back to this identity. If not,
     * then fresh confidential identity for the caller will be created.
     *
     * @return [TransactionBuilder] and list of all owner keys used in the input states that are going to be moved.
     */
    @Suspendable
    fun <T : TokenType> generateMove(
            builder: TransactionBuilder,
            partyAndAmounts: List<PartyAndAmount<T>>,
            queryCriteria: QueryCriteria? = null,
            changeOwner: AbstractParty? = null
    ): Pair<TransactionBuilder, List<PublicKey>> {
        // Grab some tokens from the vault and soft-lock.
        // Only supports moves of the same token instance currently.
        // TODO Support spends for different token types, different instances of the same type.
        // The way to do this will be to perform a query for each token type. If there are multiple token types then
        // just do all the below however many times is necessary.
        val totalRequired = partyAndAmounts.map { it.amount }.sumOrThrow()
        val additionalCriteria = queryCriteria ?: ownedTokenAmountCriteria(totalRequired.token)
        val acceptableStates = attemptSpend(totalRequired, builder.lockId, additionalCriteria)
        require(acceptableStates.isNotEmpty()) {
            "No states matching given criteria to generate move."
        }
        // Provide a key to send any change to. Currently token selection is done only for the node operator.
        // TODO: Generalise this so it can be done for any "account".
        val nodeIdentity = services.myInfo.legalIdentitiesAndCerts.first()
        val changeParty = if (changeOwner == null) {
            // TODO This approach is clearly taken from core. Revisit it.
            services.keyManagementService.freshKeyAndCert(nodeIdentity, revocationEnabled = false).party.anonymise()
        } else {
            // Check that this identity belongs to the node that called generateMove.
            val ownerId = services.identityService.wellKnownPartyFromAnonymous(changeOwner)
            check(ownerId != null && services.myInfo.isLegalIdentity(ownerId)) {
                "Owner of the change: $changeOwner is not the identity that belongs to the node."
            }
            changeOwner
        }

        // Set the transaction notary. Currently, the assumption is that all states are on the same notary.
        // TODO Generalise this to deal with multiple notaries in the future.
        val notary = acceptableStates.map { it.state.notary }.toSet().single()
        builder.notary = notary

        // Now calculate the output states. This is complicated by the fact that a single payment may require
        // multiple output states, due to the need to keep states separated by issuer. We start by figuring out
        // how much we've gathered for each issuer: this map will keep track of how much we've used from each
        // as we work our way through the payments.
        val tokensGroupedByIssuer: Map<IssuedTokenType<T>, List<StateAndRef<FungibleToken<T>>>> = acceptableStates.groupBy { it.state.data.amount.token }
        val remainingTokensFromEachIssuer = tokensGroupedByIssuer.mapValues { (_, value) ->
            value.map { (state) -> state.data.amount }.sumOrThrow()
        }.toList().toMutableList()

        // Calculate the list of output states making sure that the
        val outputStates = mutableListOf<TransactionState<FungibleToken<T>>>()
        for ((party, paymentAmount) in partyAndAmounts) {
            var remainingToPay = paymentAmount.quantity
            while (remainingToPay > 0) {
                val (token, remainingFromCurrentIssuer) = remainingTokensFromEachIssuer.last()
                val delta = remainingFromCurrentIssuer.quantity - remainingToPay
                when {
                    delta > 0 -> {
                        // The states from the current issuer more than covers this payment.
                        outputStates += FungibleToken(Amount(remainingToPay, token), party) withNotary notary
                        remainingTokensFromEachIssuer[remainingTokensFromEachIssuer.lastIndex] = Pair(token, Amount(delta, token))
                        remainingToPay = 0
                    }
                    delta == 0L -> {
                        // The states from the current issuer exactly covers this payment.
                        outputStates += FungibleToken(Amount(remainingToPay, token), party) withNotary notary
                        remainingTokensFromEachIssuer.removeAt(remainingTokensFromEachIssuer.lastIndex)
                        remainingToPay = 0
                    }
                    delta < 0 -> {
                        // The states from the current issuer don't cover this payment, so we'll have to use >1 output
                        // state to cover this payment.
                        outputStates += FungibleToken(remainingFromCurrentIssuer, party) withNotary notary
                        remainingTokensFromEachIssuer.removeAt(remainingTokensFromEachIssuer.lastIndex)
                        remainingToPay -= remainingFromCurrentIssuer.quantity
                    }
                }
            }
        }

        // Generate the change states.
        remainingTokensFromEachIssuer.forEach { (_, amount) ->
            outputStates += FungibleToken(amount, changeParty) withNotary notary
        }

        // Create a move command for each group.
        tokensGroupedByIssuer.map { (key, value) ->
            val keys = value.map { it.state.data.holder.owningKey }
            builder.addCommand(MoveTokenCommand(key), keys)
        }

        for (state in acceptableStates) builder.addInputState(state)
        for (state in outputStates) builder.addOutputState(state)

        // What if we already have a move command with the right keys? Filter it out here or in platform code?

        val allKeysUsed = acceptableStates.map { it.state.data.holder.owningKey }
        return Pair(builder, allKeysUsed)
    }

    // Modifies builder in place. All checks for exit states should have been done before.
    // For example we assume that existStates have same issuer.
    @Suspendable
    fun <T : TokenType> generateExit(
            builder: TransactionBuilder,
            exitStates: List<StateAndRef<FungibleToken<T>>>,
            amount: Amount<T>,
            changeOwner: AbstractParty
    ) {
        val firstState = exitStates.first().state.data
        // Choose states to cover amount - return ones used, and change output
        val changeOutput = change(exitStates, amount, changeOwner)
        val moveKey = firstState.holder.owningKey
        val issuerKey = firstState.amount.token.issuer.owningKey
        val redeemCommand = RedeemTokenCommand(firstState.amount.token)
        builder.apply {
            exitStates.forEach { addInputState(it) }
            if (changeOutput != null) addOutputState(changeOutput)
            addCommand(redeemCommand, issuerKey, moveKey)
        }
    }

    private fun <T : TokenType> change(exitStates: List<StateAndRef<FungibleToken<T>>>, amount: Amount<T>, changeOwner: AbstractParty): FungibleToken<T>? {
        val assetsSum = exitStates.sumTokenStateAndRefs()
        val difference = assetsSum - amount.issuedBy(exitStates.first().state.data.amount.token.issuer)
        check(difference.quantity >= 0) {
            "Sum of exit states should be qual or greater than the amount to exit."
        }
        return if (difference.quantity == 0L) {
            null
        } else {
            FungibleToken(difference, changeOwner)
        }
    }
}