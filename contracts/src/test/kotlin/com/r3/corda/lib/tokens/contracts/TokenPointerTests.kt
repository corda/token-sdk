package com.r3.corda.lib.tokens.contracts

import com.r3.corda.lib.tokens.contracts.commands.Create
import com.r3.corda.lib.tokens.contracts.commands.IssueTokenCommand
import com.r3.corda.lib.tokens.contracts.states.FungibleToken
import com.r3.corda.lib.tokens.contracts.types.TokenPointer
import com.r3.corda.lib.tokens.contracts.utilities.heldBy
import com.r3.corda.lib.tokens.contracts.utilities.issuedBy
import com.r3.corda.lib.tokens.contracts.utilities.of
import com.r3.corda.lib.tokens.contracts.utilities.singleOutput
import net.corda.core.crypto.Crypto
import net.corda.core.crypto.SignableData
import net.corda.core.crypto.SignatureMetadata
import net.corda.core.crypto.sign
import net.corda.core.transactions.TransactionBuilder
import org.junit.Test
import kotlin.test.assertEquals

class TokenPointerTests : ContractTestCommon() {
    @Test
    fun test() {
        // Create the evolvable token type.
        val evolvableToken = TestEvolvableTokenType(listOf(ALICE.party))
        val committedTransaction = aliceServices.run {
            val transaction = TransactionBuilder(notary = NOTARY.party).apply {
                addCommand(Create(), ALICE.publicKey)
                addOutputState(evolvableToken)
            }
            val signedTransaction = signInitialTransaction(transaction)
            recordTransactions(signedTransaction)
            signedTransaction
        }
        val outputStateAndRef = committedTransaction.tx.outRefsOfType<TestEvolvableTokenType>().single()
        val tokenPointer = evolvableToken.toPointer<TestEvolvableTokenType>()
        // Create a transaction which contains a state with a pointer to the above evolvable token type.
        val testTransaction = aliceServices.run {
            val transaction = TransactionBuilder(notary = NOTARY.party).apply {
                // Must add the reference state manually as VaultService is not implemented for MockServices.
                addReferenceState(outputStateAndRef.referenced())
                addOutputState(100 of tokenPointer issuedBy ALICE.party heldBy ALICE.party)
                addCommand(IssueTokenCommand(tokenPointer issuedBy ALICE.party), ALICE.publicKey)
            }
            val signedTransaction = signInitialTransaction(transaction)
            val signaureMetadata = SignatureMetadata(myInfo.platformVersion, Crypto.findSignatureScheme(NOTARY.publicKey).schemeNumberID)
            val signableData = SignableData(signedTransaction.id, signaureMetadata)
            val signature = NOTARY.keyPair.sign(signableData)
            val transactionWithNotarySignature = signedTransaction + signature
            recordTransactions(transactionWithNotarySignature)
            transactionWithNotarySignature
        }
        // Check we can resolve the pointer inside the ledger transaction.
        val ledgerTransaction = testTransaction.toLedgerTransaction(aliceServices)
        val fungibleToken = ledgerTransaction.singleOutput<FungibleToken<TokenPointer<TestEvolvableTokenType>>>()
        assertEquals(fungibleToken.tokenType.pointer.resolve(ledgerTransaction), outputStateAndRef)
    }
}