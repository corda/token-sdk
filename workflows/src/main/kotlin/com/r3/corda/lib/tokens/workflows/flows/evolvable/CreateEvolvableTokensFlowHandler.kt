package com.r3.corda.lib.tokens.workflows.flows.evolvable

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.lib.tokens.contracts.commands.Create
import com.r3.corda.lib.tokens.contracts.states.EvolvableTokenType
import com.r3.corda.lib.tokens.workflows.internal.flows.finality.ObserverAwareFinalityFlowHandler
import net.corda.core.contracts.requireThat
import net.corda.core.flows.FlowException
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.flows.SignTransactionFlow
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.unwrap

/** In-line counter-flow to [CreateEvolvableTokensFlow].
 *
 * @param additionalTransactionChecks - can be used by developers to add extra transaction verification steps to the responding flow. The default logic
 *  verifies that the responding party is both a signer of the Create command and a maintainer of the token type state.
 * */
class CreateEvolvableTokensFlowHandler(val otherSession: FlowSession, val additionalTransactionChecks: (stx: SignedTransaction) -> Unit) : FlowLogic<Unit>() {

    constructor(otherSession: FlowSession) : this(otherSession, {})

    @Suspendable
    override fun call() {
        // Receive the notification
        val notification = otherSession.receive<CreateEvolvableTokensFlow.Notification>().unwrap { it }

        // Sign the transaction proposal, if required
        if (notification.signatureRequired) {
            val signTransactionFlow = object : SignTransactionFlow(otherSession) {
                override fun checkTransaction(stx: SignedTransaction) {
                    val createCommand = stx.tx.commands.single { it.value is Create }
                    if (!createCommand.signers.contains(ourIdentity.owningKey)) {
                        throw FlowException("Signing party should be one of the Create command signers.")
                    }

                    val evolvableTokenTypeOutputState = stx.coreTransaction.outputsOfType<EvolvableTokenType>().single()
                    if (!evolvableTokenTypeOutputState.maintainers.contains(ourIdentity)) {
                        throw FlowException("Signing party should be one of the token type maintainers.")
                    }

                    if (!evolvableTokenTypeOutputState.participants.contains(ourIdentity)) {
                        throw FlowException("Signing party should be one of the token type participants.")
                    }

                    stx.verify(serviceHub, false)

                    additionalTransactionChecks(stx)
                }
            }
            subFlow(signTransactionFlow)
        }

        // Resolve the creation transaction.
        subFlow(ObserverAwareFinalityFlowHandler(otherSession))
    }
}