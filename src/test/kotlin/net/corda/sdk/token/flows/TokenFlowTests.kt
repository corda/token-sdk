package net.corda.sdk.token.flows

import net.corda.core.utilities.getOrThrow
import net.corda.sdk.token.MockNetworkTest
import net.corda.sdk.token.statesAndContracts.House
import net.corda.sdk.token.types.TokenPointer
import net.corda.sdk.token.types.money.GBP
import net.corda.testing.node.StartedMockNode
import org.junit.Before
import org.junit.Test

class TokenFlowTests : MockNetworkTest(numberOfNodes = 3) {

    lateinit var A: StartedMockNode
    lateinit var B: StartedMockNode
    lateinit var I: StartedMockNode

    @Before
    override fun initialiseNodes() {
        A = nodes[0]
        B = nodes[1]
        I = nodes[2]
    }

    @Test
    fun `create evolvable token`() {
        val house = House("24 Leinster Gardens, Bayswater, London", 1_000_000.GBP, listOf(I.legalIdentity()))
        val createEvolvableTokenTransaction = I.createEvolvableToken(house, NOTARY.legalIdentity()).getOrThrow()
    }

    @Test
    fun `create evolvable token then issue token`() {
        val house = House("24 Leinster Gardens, Bayswater, London", 1_000_000.GBP, listOf(I.legalIdentity()))
        val createEvolvableTokenTransaction = I.createEvolvableToken(house, NOTARY.legalIdentity()).getOrThrow()
        val token: TokenPointer<House> = house.toPointer()
        val issueTokenAmountTransaction = I.issueToken(token, A, NOTARY, 100).getOrThrow()
        println(issueTokenAmountTransaction.tx)
    }

}