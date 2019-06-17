package com.r3.corda.lib.tokens.contracts

import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.whenever
import net.corda.core.contracts.TypeOnlyCommandData
import net.corda.core.identity.CordaX500Name
import net.corda.core.node.NotaryInfo
import net.corda.node.services.api.IdentityServiceInternal
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.core.DUMMY_NOTARY_NAME
import net.corda.testing.core.SerializationEnvironmentRule
import net.corda.testing.core.TestIdentity
import net.corda.testing.dsl.EnforceVerifyOrFail
import net.corda.testing.dsl.TransactionDSL
import net.corda.testing.dsl.TransactionDSLInterpreter
import net.corda.testing.node.MockServices
import net.corda.testing.node.transaction
import org.junit.Rule

abstract class ContractTestCommon {

    protected companion object {
        val NOTARY = TestIdentity(DUMMY_NOTARY_NAME, 20)
        val ISSUER = TestIdentity(CordaX500Name("ISSUER", "London", "GB"))
        val ALICE = TestIdentity(CordaX500Name("ALICE", "London", "GB"))
        val BOB = TestIdentity(CordaX500Name("BOB", "London", "GB"))
        val CHARLIE = TestIdentity(CordaX500Name("CHARLIE", "London", "GB"))
        val DAENERYS = TestIdentity(CordaX500Name("DAENERYS", "London", "GB"))
    }

    @Rule
    @JvmField
    val testSerialization = SerializationEnvironmentRule()

    protected val aliceServices = MockServices(
            cordappPackages = listOf("com.r3.corda.lib.tokens.contracts", "com.r3.corda.lib.tokens.money"),
            initialIdentity = ALICE,
            identityService = mock<IdentityServiceInternal>().also {
                doReturn(ALICE.party).whenever(it).partyFromKey(ALICE.publicKey)
                doReturn(BOB.party).whenever(it).partyFromKey(BOB.publicKey)
                doReturn(CHARLIE.party).whenever(it).partyFromKey(CHARLIE.publicKey)
                doReturn(DAENERYS.party).whenever(it).partyFromKey(DAENERYS.publicKey)
                doReturn(ISSUER.party).whenever(it).partyFromKey(ISSUER.publicKey)
            },
            networkParameters = testNetworkParameters(
                    minimumPlatformVersion = 4,
                    notaries = listOf(NotaryInfo(NOTARY.party, false))
            )
    )

    protected fun transaction(script: TransactionDSL<TransactionDSLInterpreter>.() -> EnforceVerifyOrFail) {
        aliceServices.transaction(NOTARY.party, script)
    }

    protected class WrongCommand : TypeOnlyCommandData()
}