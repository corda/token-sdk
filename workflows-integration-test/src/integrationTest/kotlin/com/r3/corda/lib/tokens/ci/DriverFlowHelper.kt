package com.r3.corda.lib.tokens.ci

import net.corda.core.CordaInternal
import net.corda.core.concurrent.CordaFuture
import net.corda.core.flows.StateMachineRunId
import net.corda.core.messaging.FlowHandle
import net.corda.core.messaging.StateMachineUpdate
import net.corda.core.toFuture
import net.corda.core.utilities.getOrThrow
import net.corda.testing.driver.NodeHandle

/**
 * This helper function is designed to used in conjunction with the [waitForFlowToBeRemovedThenComplete] method. The example
 * use case is as follows:
 *
 * 1. val idFuture = watchForFlowAdded(nodeX, FlowResonderClass::class.java.name)
 * 2. val flowFuture = nodeY.rpc.startFlow(::FlowClass ...)
 * 3. waitForFlowToBeRemovedThenComplete(idFuture, flowFuture, nodeX)
 *
 * This method in particular will monitor the state machine updates feed and return the [StateMachineRunId] of the responder
 * flow when it is added to the [StateMachine]. This method must be called before the initiating flow is triggered.
 */
@CordaInternal
fun watchForFlowAdded(nodeWithFlow: NodeHandle, flowClassString: String) : CordaFuture<StateMachineUpdate> {
    return nodeWithFlow.rpc.stateMachinesFeed().updates.filter {
        if (it is StateMachineUpdate.Added) {
            it.stateMachineInfo.flowLogicClassName == flowClassString
        } else {
            false
        }
    }.toFuture()
}

/**
 * The initiating flow should be triggered before calling this method so we can call [getOrThrow] on the initial state machine
 * [Future]. This function then waits for the [StateMachineRunId] to be [StateMachineUpdate.Removed] before calling for the return
 * value of the flow and the removed [StateMachineRunId].
 */
@CordaInternal
fun waitForFlowToBeRemovedThenComplete(idFuture: CordaFuture<StateMachineUpdate>, flowFuture: FlowHandle<*>, nodeToWaitOnFlow: NodeHandle) {
    val id = idFuture.getOrThrow().id
    val removedId = watchForFlowRemoved(nodeToWaitOnFlow, id)
    flowFuture.returnValue.getOrThrow()
    removedId.getOrThrow()
}

/**
 * This function monitors the [StateMachine] feed for instances of [StateMachineUpdate.Removed] with a matching [StateMachineRunId]
 * to the one that is provided to the parent method.
 */
@CordaInternal
private fun watchForFlowRemoved(nodeToWaitOnFlow: NodeHandle, addedId: StateMachineRunId) : CordaFuture<StateMachineUpdate> {
    return nodeToWaitOnFlow.rpc.stateMachinesFeed().updates.filter {
        if (it is StateMachineUpdate.Removed) {
            it.id == addedId
        } else {
            false
        }
    }.toFuture()
}
