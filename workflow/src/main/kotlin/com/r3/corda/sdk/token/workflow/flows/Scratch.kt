package com.r3.corda.sdk.token.workflow.flows

import com.r3.corda.sdk.token.contracts.commands.IssueTokenCommand
import com.r3.corda.sdk.token.contracts.states.AbstractToken
import com.r3.corda.sdk.token.contracts.states.FungibleToken
import com.r3.corda.sdk.token.contracts.states.NonFungibleToken
import com.r3.corda.sdk.token.contracts.types.IssuedTokenType
import com.r3.corda.sdk.token.contracts.types.TokenType
import com.r3.corda.sdk.token.contracts.utilities.heldBy
import com.r3.corda.sdk.token.contracts.utilities.issuedBy
import com.r3.corda.sdk.token.contracts.utilities.of
import net.corda.core.cordapp.CordappConfig
import net.corda.core.flows.FinalityFlow
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.flows.ReceiveFinalityFlow
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.internal.randomOrNull
import net.corda.core.node.ServiceHub
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder

/**
 * Gets the preferred notary from the CorDapp config file. Otherwise, the list of notaries from the network map cache
 * is returned. From this list the CorDapp developer can employ their own strategy to choose a notary. for now, the
 * strategies will be to either choose the first notary or a random notary from the list.
 *
 * @param services a [ServiceHub] instance.
 * @param backupSelector a function which selects a notary when the notary property is not set in the CorDapp config.
 * @return the selected notary [Party] object.
 */
fun getPreferredNotary(services: ServiceHub, backupSelector: (ServiceHub) -> Party = firstNotary()): Party {
    val config: CordappConfig = services.getAppContext().config
    val notaryString: String = config.getString("notary")
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
fun firstNotary() = { services: ServiceHub ->
    services.networkMapCache.notaryIdentities.first()
}

/** Choose a random notary from the list. */
fun randomNotary() = { services: ServiceHub ->
    services.networkMapCache.notaryIdentities.randomOrNull()
            ?: throw IllegalArgumentException("No available notaries.")
}

/** Choose a random non validating notary. */
fun randomNonValidatingNotary() = { services: ServiceHub ->
    services.networkMapCache.notaryIdentities.filter { notary ->
        services.networkMapCache.isValidatingNotary(notary).not()
    }.randomOrNull()
}

/** Choose a random validating notary. */
fun randomValidatingNotary() = { services: ServiceHub ->
    services.networkMapCache.notaryIdentities.filter { notary ->
        services.networkMapCache.isValidatingNotary(notary)
    }.randomOrNull()
}

/** Adds a notary to a new [TransactionBuilder]. If the notary is already set then this  */
fun addNotary(services: ServiceHub, txb: TransactionBuilder): TransactionBuilder {
    return txb.apply { notary = getPreferredNotary(services) }
}

/**
 * A function that adds a list of output [AbstractToken] states to a [TransactionBuilder]. It automatically adds
 * [IssueTokenCommand] commands for each [IssuedTokenType]. A notary [Party] must be added to the [TransactionBuilder]
 * before this function can be called.
 */
fun addIssueTokens(outputs: Set<AbstractToken>, txb: TransactionBuilder): TransactionBuilder {
    val outputGroups: Map<IssuedTokenType<TokenType>, List<AbstractToken>> = outputs.groupBy { it.issuedTokenType }
    return txb.apply {
        outputGroups.forEach { issuedTokenType: IssuedTokenType<TokenType>, states: List<AbstractToken> ->
            // All tokens must have the same issuer.
            val issuer = states.map { it.issuer }.toSet().single()
            addCommand(IssueTokenCommand(issuedTokenType), issuer.owningKey)
            states.forEach { state -> addOutputState(state) }
        }
    }
}

fun addIssueTokens(vararg outputs: AbstractToken, transactionBuilder: TransactionBuilder): TransactionBuilder {
    return addIssueTokens(outputs.toSet(), transactionBuilder)
}

fun addIssueTokens(output: AbstractToken, transactionBuilder: TransactionBuilder): TransactionBuilder {
    return addIssueTokens(setOf(output), transactionBuilder)
}

/**
 * Creates a [TransactionBuilder] with the preferred notary, the requested set of tokens as outputs and adds
 * [IssueTokenCommand]s for each group of states (grouped by [IssuedTokenType].
 */
fun createIssueTokensTransaction(services: ServiceHub, tokens: Set<AbstractToken>): TransactionBuilder {
    val transactionBuilder = TransactionBuilder(getPreferredNotary(services))
    return addIssueTokens(tokens, transactionBuilder)
}

open class IssueTokens(
        val tokens: Set<AbstractToken>,
        val sessions: Set<FlowSession>
) : FlowLogic<SignedTransaction>() {
    override fun call(): SignedTransaction {
        val transactionBuilder = createIssueTokensTransaction(serviceHub, tokens)
        val signedTransaction = serviceHub.signInitialTransaction(transactionBuilder)
        return subFlow(FinalityFlow(signedTransaction, sessions))
    }
}

class IssueTokensHandler(val otherSession: FlowSession) : FlowLogic<SignedTransaction>() {
    override fun call(): SignedTransaction {
        return subFlow(ReceiveFinalityFlow(otherSideSession = otherSession))
    }
}

class IssueFungibleTokens<T : TokenType>(
        tokens: Set<FungibleToken<T>>,
        sessions: Set<FlowSession>
) : IssueTokens(tokens, sessions) {

    constructor(tokens: FungibleToken<T>, session: FlowSession)
            : this(setOf(tokens), setOf(session))

    constructor(tokens: FungibleToken<T>, sessions: Set<FlowSession>)
            : this(setOf(tokens), sessions)

    constructor(tokenType: T, amount: Long, issuer: Party, holder: AbstractParty, sessions: Set<FlowSession>)
            : this(setOf(amount of tokenType issuedBy issuer heldBy holder), sessions)

    constructor(issuedTokenType: IssuedTokenType<T>, amount: Long, holder: AbstractParty, sessions: Set<FlowSession>)
            : this(setOf(amount of issuedTokenType heldBy holder), sessions)

    constructor(issuedTokenType: IssuedTokenType<T>, amount: Long, sessions: Set<FlowSession>)
            : this(setOf(amount of issuedTokenType heldBy issuedTokenType.issuer), sessions)

    constructor(tokenType: T, amount: Long, issuer: Party, sessions: Set<FlowSession>)
            : this(setOf(amount of tokenType issuedBy issuer heldBy issuer), sessions)

}

class IssueNonFungibleTokens<T : TokenType>(tokens: Set<NonFungibleToken<T>>, sessions: Set<FlowSession>) : IssueTokens(tokens, sessions) {

    constructor(token: NonFungibleToken<T>, session: FlowSession)
            : this(setOf(token), setOf(session))

    constructor(token: NonFungibleToken<T>, sessions: Set<FlowSession>)
            : this(setOf(token), sessions)

    constructor(tokenType: T, issuer: Party, holder: AbstractParty, sessions: Set<FlowSession>)
            : this(setOf(tokenType issuedBy issuer heldBy holder), sessions)

    constructor(issuedTokenType: IssuedTokenType<T>, holder: AbstractParty, sessions: Set<FlowSession>)
            : this(setOf(issuedTokenType heldBy holder), sessions)

    constructor(issuedTokenType: IssuedTokenType<T>, sessions: Set<FlowSession>)
            : this(setOf(issuedTokenType heldBy issuedTokenType.issuer), sessions)

    constructor(tokenType: T, issuer: Party, sessions: Set<FlowSession>)
            : this(setOf(tokenType issuedBy issuer heldBy issuer), sessions)

}


/*

Add notary initially if no inputs present already.

Add outputs: all must have the same notary.
Add inputs

Add inputs and outputs

How are the inputs and outputs linked together?

Add move
Add issue

Check if all inputs have the same notary. if they don't then
Set notary. Which notary? Specify or gets the first one. or uses the currnet one.
Don't need to set contracts as they are linked via the annotation.


 */