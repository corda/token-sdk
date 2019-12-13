package com.r3.corda.lib.tokens.workflows.flows.redeem

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.lib.tokens.contracts.types.TokenType
import net.corda.core.contracts.Amount
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.AnonymousParty
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.transactions.SignedTransaction

/**
 * Version of [RedeemFungibleTokensFlow] using confidential identity for a change owner.
 * There is no [NonFungibleToken] version of this flow, because there is no output paid.
 * Identities are synchronised during normal redeem call.
 *
 * @param amount amount of token to redeem
 * @param issuerSession session with the issuer tokens should be redeemed with
 * @param observerSessions optional sessions with the observer nodes, to witch the transaction will be broadcasted
 * @param additionalQueryCriteria additional criteria for token selection
 * @param changeHolder optional change key, if using accounts you should generate the change key prior to calling this
 *                     flow then pass it in to the flow via this parameter
 */
class ConfidentialRedeemFungibleTokensFlow
@JvmOverloads
constructor(
        val amount: Amount<TokenType>,
        val issuerSession: FlowSession,
        val observerSessions: List<FlowSession> = emptyList(),
        val additionalQueryCriteria: QueryCriteria? = null,
        val changeHolder: AbstractParty? = null
) : FlowLogic<SignedTransaction>() {
    @Suspendable
    override fun call(): SignedTransaction {
        // If a change holder key is not specified then one will be created for you. NB. If you want to use accounts
        // with tokens, then you must generate and allocate the key to an account up-front and pass the key in as the
        // "changeHolder".
        val confidentialHolder = changeHolder ?: let {
            val key = serviceHub.keyManagementService.freshKey()
            AnonymousParty(key)
        }
        return subFlow(RedeemFungibleTokensFlow(
                amount = amount,
                issuerSession = issuerSession,
                changeHolder = confidentialHolder,  // This will never be null.
                observerSessions = observerSessions,
                additionalQueryCriteria = additionalQueryCriteria
        ))
    }
}