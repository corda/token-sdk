package com.r3.corda.lib.tokens.workflows.internal.selection

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.lib.tokens.contracts.states.FungibleToken
import com.r3.corda.lib.tokens.contracts.types.TokenType
import com.r3.corda.lib.tokens.contracts.utilities.heldBy
import com.r3.corda.lib.tokens.contracts.utilities.issuedBy
import com.r3.corda.lib.tokens.contracts.utilities.sumTokenStateAndRefs
import com.r3.corda.lib.tokens.workflows.internal.checkSameIssuer
import com.r3.corda.lib.tokens.workflows.types.PartyAndAmount
import net.corda.core.contracts.Amount
import net.corda.core.contracts.Amount.Companion.sumOrThrow
import net.corda.core.contracts.StateAndRef
import net.corda.core.flows.FlowLogic
import net.corda.core.identity.AbstractParty
import net.corda.core.node.ServiceHub
import net.corda.core.transactions.TransactionBuilder
import java.util.*

/**
 * Interface that provides fungible state selection methods in flow.
 */
interface Selector {
    val services: ServiceHub

    /**
     * Select [FungibleToken]s that cover [requiredAmount]. Notice that this
     * function doesn't calculate change. If query criteria is not specified then only held token amounts are used.
     *
     * Set [TokenQueryBy.issuer] to specify issuer.
     * Calling selectTokens multiple time with the same lockId will return next unlocked states.
     *
     * @param lockId id used to lock the states for spend, defaults to [FlowLogic] runID
     * @param requiredAmount amount that should be spent
     * @param queryBy narrows down tokens to spend, see [TokenQueryBy]
     * @return List of [FungibleToken]s that satisfy the amount to spend, empty list if none found.
     */
    @Suspendable
    fun selectTokens(
            lockId: UUID = FlowLogic.currentTopLevel?.runId?.uuid ?: UUID.randomUUID(),
            requiredAmount: Amount<TokenType>,
            queryBy: TokenQueryBy? = null
    ): List<StateAndRef<FungibleToken>>

    /**
     * Generate move of [FungibleToken] T to tokenHolders specified in [PartyAndAmount]. Each party will receive amount
     * defined by [partyAndAmounts]. If query criteria is not specified then only held token amounts are used. Set
     * [TokenQueryBy.issuer] to specify issuer.
     *
     * @return Pair of lists, one for [FungibleToken]s that satisfy the amount to spend, empty list if none found, second
     *  for output states with possible change.
     */
    @Suspendable
    fun generateMove(
            lockId: UUID = FlowLogic.currentTopLevel?.runId?.uuid ?: UUID.randomUUID(),
            partiesAndAmounts: List<PartyAndAmount<TokenType>>,
            changeHolder: AbstractParty,
            queryBy: TokenQueryBy? = null
    ): Pair<List<StateAndRef<FungibleToken>>, List<FungibleToken>> {
        // Grab some tokens from the vault and soft-lock.
        // Only supports moves of the same token instance currently.
        // TODO Support spends for different token types, different instances of the same type.
        // The way to do this will be to perform a query for each token type. If there are multiple token types then
        // just do all the below however many times is necessary.
        val totalRequired = partiesAndAmounts.map { it.amount }.sumOrThrow()
        val acceptableStates = selectTokens(lockId, totalRequired, queryBy)
        require(acceptableStates.isNotEmpty()) {
            "No states matching given criteria to generate move."
        }

        // Check that the change identity belongs to the node that called generateMove.
        val ownerId = services.identityService.wellKnownPartyFromAnonymous(changeHolder)
        check(ownerId != null && services.myInfo.isLegalIdentity(ownerId)) {
            "Owner of the change: $changeHolder is not the identity that belongs to the node."
        }

        // Now calculate the output states. This is complicated by the fact that a single payment may require
        // multiple output states, due to the need to keep states separated by issuer. We start by figuring out
        // how much we've gathered for each issuer: this map will keep track of how much we've used from each
        // as we work our way through the payments.
        val tokensGroupedByIssuer = acceptableStates.groupBy { it.state.data.amount.token }
        val remainingTokensFromEachIssuer = tokensGroupedByIssuer.mapValues { (_, value) ->
            value.map { (state) -> state.data.amount }.sumOrThrow()
        }.toList().toMutableList()

        // TODO: This assumes there is only ever ONE notary. In the future we need to deal with notary change.
        check(acceptableStates.map { it.state.notary }.toSet().size == 1) {
            "States selected have different notaries. For now we don't support notary change, it should be performed beforehand."
        }

        // Calculate the list of output states making sure that the
        val outputStates = mutableListOf<FungibleToken>()
        for ((party, paymentAmount) in partiesAndAmounts) {
            var remainingToPay = paymentAmount.quantity
            while (remainingToPay > 0) {
                val (token, remainingFromCurrentIssuer) = remainingTokensFromEachIssuer.last()
                val delta = remainingFromCurrentIssuer.quantity - remainingToPay
                when {
                    delta > 0 -> {
                        // The states from the current issuer more than covers this payment.
                        outputStates += FungibleToken(Amount(remainingToPay, token), party)
                        remainingTokensFromEachIssuer[remainingTokensFromEachIssuer.lastIndex] = Pair(token, Amount(delta, token))
                        remainingToPay = 0
                    }
                    delta == 0L -> {
                        // The states from the current issuer exactly covers this payment.
                        outputStates += FungibleToken(Amount(remainingToPay, token), party)
                        remainingTokensFromEachIssuer.removeAt(remainingTokensFromEachIssuer.lastIndex)
                        remainingToPay = 0
                    }
                    delta < 0 -> {
                        // The states from the current issuer don't cover this payment, so we'll have to use >1 output
                        // state to cover this payment.
                        outputStates += FungibleToken(remainingFromCurrentIssuer, party)
                        remainingTokensFromEachIssuer.removeAt(remainingTokensFromEachIssuer.lastIndex)
                        remainingToPay -= remainingFromCurrentIssuer.quantity
                    }
                }
            }
        }

        // Generate the change states.
        remainingTokensFromEachIssuer.forEach { (_, amount) ->
            outputStates += FungibleToken(amount, changeHolder)
        }

        return Pair(acceptableStates, outputStates)
    }

    /**
     * Generate exit of [FungibleToken]s specified by [exitStates] up to given [amount]. Possible change will be paid to
     * [changeHolder]. All checks for exit states should have been done before. For example we assume that existStates have same issuer.
     *
     * @return Pair of list of [FungibleToken] inputs that satisfy the amount to exit and change output.
     */
    @Suspendable
    fun generateExit(
            exitStates: List<StateAndRef<FungibleToken>>,
            amount: Amount<TokenType>,
            changeHolder: AbstractParty
    ): Pair<List<StateAndRef<FungibleToken>>, FungibleToken?> {
        checkSameIssuer(exitStates)
        // Choose states to cover amount - return ones used, and change output
        val changeOutput = change(exitStates, amount, changeHolder)
        return Pair(exitStates, changeOutput)
    }

    private fun change(
            exitStates: List<StateAndRef<FungibleToken>>,
            amount: Amount<TokenType>,
            changeOwner: AbstractParty
    ): FungibleToken? {
        val assetsSum = exitStates.sumTokenStateAndRefs()
        val difference = assetsSum - amount.issuedBy(exitStates.first().state.data.amount.token.issuer)
        check(difference.quantity >= 0) {
            "Sum of exit states should be equal or greater than the amount to exit."
        }
        return if (difference.quantity == 0L) {
            null
        } else {
            difference heldBy changeOwner
        }
    }
}