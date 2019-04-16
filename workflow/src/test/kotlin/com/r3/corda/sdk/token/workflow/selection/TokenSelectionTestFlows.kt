package com.r3.corda.sdk.token.workflow.selection

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.sdk.token.contracts.states.FungibleToken
import com.r3.corda.sdk.token.contracts.types.IssuedTokenType
import com.r3.corda.sdk.token.contracts.types.TokenType
import com.r3.corda.sdk.token.money.DigitalCurrency
import net.corda.core.concurrent.CordaFuture
import net.corda.core.contracts.Amount
import net.corda.core.contracts.StateAndRef
import net.corda.core.flows.FlowLogic
import net.corda.core.internal.FlowAsyncOperation
import net.corda.core.internal.executeAsync
import net.corda.node.services.statemachine.FlowLogicRefImpl
import java.security.PublicKey
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.Future


val e = Executors.newSingleThreadExecutor()

class SuspendingSelector(val owningKey: PublicKey,
                         val amount: Amount<IssuedTokenType<TokenType>>,
                         val allowShortfall: Boolean) : FlowLogic<List<StateAndRef<FungibleToken<TokenType>>>>() {


    @Suspendable
    override fun call(): List<StateAndRef<FungibleToken<TokenType>>> {
        val vaultWatcherService = serviceHub.cordaService(VaultWatcherService::class.java)
        val localTokenSelector = LocalTokenSelector(vaultWatcherService)

        val selectedTokens = localTokenSelector.selectTokens(owningKey, amount, allowShortfall = allowShortfall)

        println("SUSPENDING:::: ${runId.uuid}")

        val string = executeAsync(object : FlowAsyncOperation<String> {
            override fun execute(deduplicationId: String): CordaFuture<String> {
                val f = CompletableFuture<String>()
                e.submit {
                    Thread.sleep(1000)
                    f.complete("yes")
                }
                return object : Future<String> by f, CordaFuture<String> {
                    override fun <W> then(callback: (CordaFuture<String>) -> W) {
                        f.thenAccept { callback(this) }
                    }

                    override fun toCompletableFuture(): CompletableFuture<String> {
                        return f
                    }
                }
            }
        })

        println("RESUMED:::: ${runId.uuid} :: $string")

        return selectedTokens
    }

}