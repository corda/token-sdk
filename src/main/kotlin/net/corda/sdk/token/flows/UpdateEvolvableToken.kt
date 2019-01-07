package net.corda.sdk.token.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.StateAndRef
import net.corda.core.flows.*
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.sdk.token.commands.TokenCommand
import net.corda.sdk.token.schemas.DistributionRecord
import net.corda.sdk.token.types.EvolvableToken
import java.util.*
import javax.persistence.criteria.CriteriaQuery

@InitiatingFlow
@StartableByRPC
class UpdateEvolvableToken(
        val old: StateAndRef<EvolvableToken>,
        val new: EvolvableToken
) : FlowLogic<SignedTransaction>() {
    @Suspendable
    override fun call(): SignedTransaction {
        // Create a transaction which updates the ledger with the new evolvable token.
        // The parties listed as maintainers in the old state should be the signers.
        val signingKeys = old.state.data.maintainers.map { it.owningKey }
        val utx: TransactionBuilder = TransactionBuilder(notary = old.state.notary).apply {
            addCommand(data = TokenCommand.Update(), keys = signingKeys)
            addInputState(old)
            addOutputState(state = new, contract = old.state.contract)
        }
        // Sign the transaction. Only Concrete Parties should be used here.
        val stx: SignedTransaction = serviceHub.signInitialTransaction(utx)
        // Get the list of parties on the distribution list for this evolvable token.
        val sessions: List<FlowSession> = serviceHub.withEntityManager {
            val query: CriteriaQuery<DistributionRecord> = criteriaBuilder.createQuery(DistributionRecord::class.java)
            query.apply {
                val root = from(DistributionRecord::class.java)
                where(criteriaBuilder.equal(root.get<UUID>("linearId"), new.linearId.id))
                select(root)
            }
            createQuery(query).resultList
        }.map { initiateFlow(it.party) }
        // Send to all on the list.
        return subFlow(FinalityFlow(transaction = stx, sessions = sessions))
    }
}