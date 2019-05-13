package com.r3.corda.sdk.token.workflow.flows.issue

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.sdk.token.contracts.commands.IssueTokenCommand
import com.r3.corda.sdk.token.contracts.states.AbstractToken
import com.r3.corda.sdk.token.contracts.states.FungibleToken
import com.r3.corda.sdk.token.contracts.states.NonFungibleToken
import com.r3.corda.sdk.token.contracts.types.IssuedTokenType
import com.r3.corda.sdk.token.contracts.types.TokenType
import com.r3.corda.sdk.token.workflow.flows.finality.ObserverAwareFinalityFlow
import com.r3.corda.sdk.token.workflow.flows.internal.distribution.UpdateDistributionListFlow
import com.r3.corda.sdk.token.workflow.utilities.getPreferredNotary
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder

/**
 * Use this flow to issue fungible or non-fungible tokens. It should be called as an in-line sub-flow, therefore you
 * must have flow participantSessions set up prior to calling this flow. Tokens are gneerally constructed before calling this flow.
 * However, the flow does offer constructors for issuing single tokens, if given the token type and amount, etc. The
 * flow works as follows:
 *
 * 1. Creates a [TransactionBuilder] with the preferred notary, which is set in the token SDK config file.
 * 2. Adds the requested set of tokensToIssue as outputs to the transaction builder and adds [IssueTokenCommand]s for
 * each group of states (grouped by [IssuedTokenType].
 * 3. Finalises the transaction and updates the evolvable token distribution list, if necessary.
 *
 * Further points to note:
 *
 * 1. If you are issuing to self, there is no need to pass in a flow session.
 * 2. There is an assumption that this flow can only be used by one issuer at a time.
 * 3. Tokens can be issued to well known identities or confidential identities. To issue tokens with confidential keys
 * then use the [ConfidentialIssueTokensFlow].
 * 4. This flow supports issuing many tokens to a single or multiple parties, of the same or different types.
 * 5. Transaction observers can be specified.
 * 6. Observers can also be specified.
 * 7. This flow supports the issuance of fungible and non fungible tokens in the same transaction.
 * 8. The notary is selected from a config file or picked at random if no notary preference is available.
 * 9. This is not an initiating flow. There will also be an initiating version which is startable from the shell.
 *
 * @property tokensToIssue a list of tokens to issue. May be fungible or non-fungible.
 * @property participantSessions a list of flow participantSessions for the transaction participants.
 * @property observerSessions a list of flow participantSessions for the transaction observers.
 */
class IssueTokensFlow<T : TokenType>(
        val tokensToIssue: List<AbstractToken<T>>,
        val participantSessions: List<FlowSession>,
        val observerSessions: List<FlowSession>
) : FlowLogic<SignedTransaction>() {


    /** Issue a single [FungibleToken]. */
    constructor(
            token: FungibleToken<T>,
            participantSessions: List<FlowSession>,
            observerSessions: List<FlowSession>
    ) : this(listOf(token), participantSessions, observerSessions)

    /** Issue a single [NonFungibleToken]. */
    constructor(
            token: NonFungibleToken<T>,
            participantSessions: List<FlowSession>,
            observerSessions: List<FlowSession>
    ) : this(listOf(token), participantSessions, observerSessions)

//    /* Non-fungible token constructors. */
//
//    constructor(
//            tokenType: T,
//            issuer: Party,
//            holder: AbstractParty,
//            participantSessions: Set<FlowSession>
//    )
//            : this(listOf(tokenType issuedBy issuer heldBy holder), participantSessions)
//
//    constructor(issuedTokenType: IssuedTokenType<T>, holder: AbstractParty, participantSessions: Set<FlowSession>)
//            : this(listOf(issuedTokenType heldBy holder), participantSessions)
//
//    constructor(issuedTokenType: IssuedTokenType<T>, participantSessions: Set<FlowSession>)
//            : this(listOf(issuedTokenType heldBy issuedTokenType.issuer), participantSessions)
//
//    constructor(tokenType: T, issuer: Party, participantSessions: Set<FlowSession>)
//            : this(listOf(tokenType issuedBy issuer heldBy issuer), participantSessions)
//
//    /* Fungible token constructors. */
//
//    constructor(tokenType: T, amount: Long, issuer: Party, holder: AbstractParty, participantSessions: Set<FlowSession>)
//            : this(listOf(amount of tokenType issuedBy issuer heldBy holder), participantSessions)
//
//    constructor(issuedTokenType: IssuedTokenType<T>, amount: Long, holder: AbstractParty, participantSessions: Set<FlowSession>)
//            : this(listOf(amount of issuedTokenType heldBy holder), participantSessions)
//
//    constructor(issuedTokenType: IssuedTokenType<T>, amount: Long, participantSessions: Set<FlowSession>)
//            : this(listOf(amount of issuedTokenType heldBy issuedTokenType.issuer), participantSessions)
//
//    constructor(tokenType: T, amount: Long, issuer: Party, participantSessions: Set<FlowSession>)
//            : this(listOf(amount of tokenType issuedBy issuer heldBy issuer), participantSessions)
//
//    /* Standard constructors. */

    @Suspendable
    override fun call(): SignedTransaction {
        // Initialise the transaction builder with a preferred notary or choose a random notary.
        val transactionBuilder = TransactionBuilder(notary = getPreferredNotary(serviceHub))
        // Add all the specified tokensToIssue to the transaction. The correct commands and signing keys are also added.
        addIssueTokens(tokensToIssue, transactionBuilder)
        // Create new participantSessions if this is started as a top level flow.
        val signedTransaction = subFlow(ObserverAwareFinalityFlow(transactionBuilder, participantSessions + observerSessions))
        // Update the distribution list.
        subFlow(UpdateDistributionListFlow(signedTransaction))
        // Return the newly created transaction.
        return signedTransaction
    }
}
