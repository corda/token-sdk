package com.r3.corda.lib.tokens.workflows.flows.issue

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.lib.tokens.contracts.commands.IssueTokenCommand
import com.r3.corda.lib.tokens.contracts.states.AbstractToken
import com.r3.corda.lib.tokens.contracts.states.FungibleToken
import com.r3.corda.lib.tokens.contracts.states.NonFungibleToken
import com.r3.corda.lib.tokens.contracts.types.IssuedTokenType
import com.r3.corda.lib.tokens.contracts.types.TokenType
import com.r3.corda.lib.tokens.workflows.internal.flows.distribution.UpdateDistributionListFlow
import com.r3.corda.lib.tokens.workflows.internal.flows.finality.ObserverAwareFinalityFlow
import com.r3.corda.lib.tokens.workflows.utilities.getPreferredNotary
import net.corda.core.contracts.StateAndRef
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder

/**
 * Use this flow to issue fungible or non-fungible tokens. It should be called as an in-line sub-flow, therefore you
 * must have flow [participantSessions] set up prior to calling this flow. Tokens are usually constructed before
 * calling this flow. This flow is to be used in conjunction with the [IssueTokensFlowHandler].
 *
 * 1. Creates a [TransactionBuilder] with the preferred notary, which is set in the token SDK config file.
 * 2. Adds the requested set of tokensToIssue as outputs to the transaction builder and adds [IssueTokenCommand]s for
 * each group of states (grouped by [IssuedTokenType].
 * 3. Finalises the transaction and updates the evolvable token distribution list, if necessary.
 *
 * Further points to note:
 *
 * 1. If you are issuing to self, there is no need to pass in a flow session. Instead, pass in an emptyList for
 * [participantSessions] or use one of the overloads that doesn't require sessions.
 * 2. There is an assumption that this flow can only be used by one issuer at a time.
 * 3. Tokens can be issued to well known identities or confidential identities. To issue tokens with confidential keys
 * then use the [ConfidentialIssueTokensFlow].
 * 4. This flow supports issuing many tokens to a single or multiple parties, of the same or different types of tokens.
 * 5. Transaction observers can be specified.
 * 6. Observers can also be specified.
 * 7. This flow supports the issuance of fungible and non fungible tokens in the same transaction.
 * 8. The notary is selected from a config file or picked at random if no notary preference is available.
 * 9. This is not an initiating flow. There will also be an initiating version which is startable from the rpc.
 *
 * @property tokensToIssue a list of tokens to issue. May be fungible or non-fungible.
 * @property participantSessions a list of flow participantSessions for the transaction participants.
 * @property observerSessions a list of flow participantSessions for the transaction observers.
 */
class IssueTokensFlow<T : TokenType>
@JvmOverloads
constructor(
        val tokensToIssue: List<AbstractToken<T>>,
        val participantSessions: List<FlowSession>,
        val observerSessions: List<FlowSession> = emptyList()
) : FlowLogic<SignedTransaction>() {

    /** Issue a single [FungibleToken]. */
    @JvmOverloads
    constructor(
            token: FungibleToken<T>,
            participantSessions: List<FlowSession>,
            observerSessions: List<FlowSession> = emptyList()
    ) : this(listOf(token), participantSessions, observerSessions)

    /** Issue a single [FungibleToken] to self with no observers. */
    constructor(token: FungibleToken<T>) : this(listOf(token), emptyList(), emptyList())

    /** Issue a single [NonFungibleToken]. */
    @JvmOverloads
    constructor(
            token: NonFungibleToken<T>,
            participantSessions: List<FlowSession>,
            observerSessions: List<FlowSession> = emptyList()
    ) : this(listOf(token), participantSessions, observerSessions)

    /** Issue a single [NonFungibleToken] to self with no observers. */
    constructor(token: NonFungibleToken<T>) : this(listOf(token), emptyList(), emptyList())

    @Suspendable
    override fun call(): SignedTransaction {
        // Initialise the transaction builder with a preferred notary or choose a random notary.
        val transactionBuilder = TransactionBuilder(notary = getPreferredNotary(serviceHub))
        // Add all the specified tokensToIssue to the transaction. The correct commands and signing keys are also added.
        addIssueTokens(transactionBuilder, tokensToIssue)
        addTransactionDependencies(tokensToIssue, transactionBuilder)
        // Create new participantSessions if this is started as a top level flow.
        val signedTransaction = subFlow(
                ObserverAwareFinalityFlow(
                        transactionBuilder = transactionBuilder,
                        allSessions = participantSessions + observerSessions
                )
        )
        // Update the distribution list.
        subFlow(UpdateDistributionListFlow(signedTransaction))
        // Return the newly created transaction.
        return signedTransaction
    }
}

fun addTransactionDependencies(tokens: List<AbstractToken<*>>, transactionBuilder: TransactionBuilder) {
    tokens.forEach {
        val hash = it.tokenTypeJarHash()
        if (!transactionBuilder.attachments().contains(hash)) {
            transactionBuilder.addAttachment(hash)
        }
    }
}

fun addTransactionDependencies(tokens: Iterable<StateAndRef<AbstractToken<*>>>, transactionBuilder: TransactionBuilder) {
    addTransactionDependencies(tokens.map { it.state.data }, transactionBuilder)
}

fun addTransactionDependencies(changeOutput: AbstractToken<*>, transactionBuilder: TransactionBuilder) {
    addTransactionDependencies(listOf(changeOutput), transactionBuilder)
}

fun addTransactionDependencies(input: StateAndRef<AbstractToken<*>>, transactionBuilder: TransactionBuilder) {
    addTransactionDependencies(input.state.data, transactionBuilder)
}