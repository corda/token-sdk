package com.r3.corda.lib.tokens.workflows

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.lib.tokens.contracts.states.FungibleToken
import com.r3.corda.lib.tokens.contracts.types.TokenType
import com.r3.corda.lib.tokens.selection.TokenQueryBy
import com.r3.corda.lib.tokens.selection.memory.selector.LocalTokenSelector
import com.r3.corda.lib.tokens.selection.memory.services.VaultWatcherService
import net.corda.core.concurrent.CordaFuture
import net.corda.core.contracts.Amount
import net.corda.core.contracts.StateAndRef
import net.corda.core.flows.FlowExternalAsyncOperation
import net.corda.core.flows.FlowLogic
import net.corda.core.internal.FlowAsyncOperation
import net.corda.core.internal.executeAsync
import java.security.PublicKey
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.Future


val e = Executors.newSingleThreadExecutor()

class SuspendingSelector(val owningKey: PublicKey,
                         val amount: Amount<TokenType>) : FlowLogic<List<StateAndRef<FungibleToken>>>() {


    @Suspendable
    override fun call(): List<StateAndRef<FungibleToken>> {
        val vaultWatcherService = serviceHub.cordaService(VaultWatcherService::class.java)
        val localTokenSelector = LocalTokenSelector(serviceHub, vaultWatcherService)

        val selectedTokens = localTokenSelector.selectTokens(requiredAmount = amount, queryBy = TokenQueryBy())

        println("SUSPENDING:::: ${runId.uuid}")

        val string = await(object : FlowExternalAsyncOperation<String> {
            override fun execute(deduplicationId: String): CompletableFuture<String> {
                val f = CompletableFuture<String>()
                e.submit {
                    Thread.sleep(1000)
                    f.complete("yes")
                }
                return f
            }
        })

        println("RESUMED:::: ${runId.uuid} :: $string")

        return selectedTokens
    }

}