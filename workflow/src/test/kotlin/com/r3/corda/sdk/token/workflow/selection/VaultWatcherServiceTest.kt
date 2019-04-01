package com.r3.corda.sdk.token.workflow.selection

import com.r3.corda.sdk.token.contracts.states.FungibleToken
import com.r3.corda.sdk.token.contracts.types.IssuedTokenType
import com.r3.corda.sdk.token.money.FiatCurrency
import com.r3.corda.sdk.token.money.GBP
import net.corda.core.contracts.Amount
import net.corda.core.contracts.StateAndRef
import net.corda.core.contracts.StateRef
import net.corda.core.contracts.TransactionState
import net.corda.core.crypto.Crypto
import net.corda.core.crypto.SecureHash
import net.corda.core.identity.AnonymousParty
import net.corda.core.identity.CordaX500Name
import net.corda.core.internal.sumByLong
import net.corda.testing.core.TestIdentity
import org.hamcrest.CoreMatchers
import org.hamcrest.CoreMatchers.`is`
import org.hamcrest.Matchers.greaterThan
import org.junit.Assert
import org.junit.Test
import java.security.PublicKey

class VaultWatcherServiceTest{

    val issuer = TestIdentity(CordaX500Name("BANK", "London", "GB")).party
    val notary1 = TestIdentity(CordaX500Name("Notary1", "London", "GB")).party
    val notary2 = TestIdentity(CordaX500Name("Notary2", "London", "GB")).party


    @Test
    fun `should accept token into the cache`() {

        val vaultWatcherService = VaultWatcherService()
        val owner = Crypto.generateKeyPair(Crypto.DEFAULT_SIGNATURE_SCHEME).public
        val amountToIssue: Long = 100
        val stateAndRef = createNewTokenRef(amountToIssue, owner)
        vaultWatcherService.addTokenToCache(stateAndRef)

        val selectedTokens = vaultWatcherService.selectTokens(owner, Amount(5, IssuedTokenType(issuer, GBP)))
        Assert.assertThat(selectedTokens, `is`(CoreMatchers.equalTo(listOf<StateAndRef<FungibleToken<*>>>(stateAndRef))))
    }

    @Test
    fun `should select enough tokens to satisfy the requested amount`() {
        val vaultWatcherService = VaultWatcherService()
        val owner = Crypto.generateKeyPair(Crypto.DEFAULT_SIGNATURE_SCHEME).public
        for (i in 1..100){
            val stateAndRef = createNewTokenRef(((Math.random()*10) + 1).toLong(), owner)
            vaultWatcherService.addTokenToCache(stateAndRef)
        }

        val selectedTokens = vaultWatcherService.selectTokens(owner, Amount(45, IssuedTokenType(issuer, GBP)))
        Assert.assertThat(selectedTokens.map { it.state.data.amount.quantity }.sumByLong { it }, `is`(greaterThan(45L)))
    }

    @Test(expected = InsufficientBalanceException::class)
    fun `should not allow double selection of token in the cache`() {

        val vaultWatcherService = VaultWatcherService()
        val owner = Crypto.generateKeyPair(Crypto.DEFAULT_SIGNATURE_SCHEME).public
        val amountToIssue: Long = 100
        val stateAndRef = createNewTokenRef(amountToIssue, owner)
        vaultWatcherService.addTokenToCache(stateAndRef)

        val selectedTokens = vaultWatcherService.selectTokens(owner, Amount(5, IssuedTokenType(issuer, GBP)))
        Assert.assertThat(selectedTokens, `is`(CoreMatchers.equalTo(listOf<StateAndRef<FungibleToken<*>>>(stateAndRef))))
        vaultWatcherService.selectTokens(owner, Amount(5, IssuedTokenType(issuer, GBP)))
    }



    private fun createNewTokenRef(amountToIssue: Long, owner: PublicKey): StateAndRef<FungibleToken<FiatCurrency>> {
        val amount = Amount(amountToIssue, IssuedTokenType(issuer, GBP))
        val thing = FungibleToken(amount, AnonymousParty(owner))
        val state = TransactionState(data = thing, notary = notary1)
        val stateAndRef = StateAndRef(state, StateRef(SecureHash.randomSHA256(), 0))
        return stateAndRef
    }
}
