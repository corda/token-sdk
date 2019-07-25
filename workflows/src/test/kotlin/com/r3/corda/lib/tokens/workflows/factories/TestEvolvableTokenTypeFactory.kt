package com.r3.corda.lib.tokens.workflows.factories

import com.r3.corda.lib.tokens.testing.states.TestEvolvableTokenType
import com.r3.corda.lib.tokens.workflows.JITMockNetworkTests
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.Party

class TestEvolvableTokenTypeFactory(val testSuite: JITMockNetworkTests, val maintainerA: String, val maintainerB: String, val observerC: String, val observerD: String) {

    fun withOneMaintainer(linearId: UniqueIdentifier = UniqueIdentifier(), maintainer: Party = testSuite.identity(maintainerA)): TestEvolvableTokenType {
        return TestEvolvableTokenType(
                maintainers = listOf(maintainer),
                linearId = linearId
        )
    }

    fun withTwoMaintainers(linearId: UniqueIdentifier = UniqueIdentifier(), maintainer1: Party = testSuite.identity(maintainerA), maintainer2: Party = testSuite.identity(maintainerB)): TestEvolvableTokenType {
        return TestEvolvableTokenType(
                maintainers = listOf(maintainer1, maintainer2),
                linearId = linearId
        )
    }

    fun withOneMaintainerAndOneParticipant(linearId: UniqueIdentifier = UniqueIdentifier(), maintainer: Party = testSuite.identity(maintainerA), observer: Party = testSuite.identity(observerC)): TestEvolvableTokenType {
        return TestEvolvableTokenType(
                maintainers = listOf(maintainer),
                observers = listOf(observer),
                linearId = linearId
        )
    }

    fun withOneMaintainerAndTwoParticipants(linearId: UniqueIdentifier = UniqueIdentifier(), maintainer1: Party = testSuite.identity(maintainerA), observer1: Party = testSuite.identity(observerC), observer2: Party = testSuite.identity(observerD)): TestEvolvableTokenType {
        return TestEvolvableTokenType(
                maintainers = listOf(maintainer1),
                observers = listOf(observer1, observer2),
                linearId = linearId
        )
    }

    fun withTwoMaintainersAndOneParticipant(linearId: UniqueIdentifier = UniqueIdentifier(), maintainer1: Party = testSuite.identity(maintainerA), maintainer2: Party = testSuite.identity(maintainerB), observer1: Party = testSuite.identity(observerC)): TestEvolvableTokenType {
        return TestEvolvableTokenType(
                maintainers = listOf(maintainer1, maintainer2),
                observers = listOf(observer1),
                linearId = linearId
        )
    }

    fun withTwoMaintainersAndTwoParticipants(linearId: UniqueIdentifier = UniqueIdentifier(), maintainer1: Party = testSuite.identity(maintainerA), maintainer2: Party = testSuite.identity(maintainerB), observer1: Party = testSuite.identity(observerC), observer2: Party = testSuite.identity(observerD)): TestEvolvableTokenType {
        return TestEvolvableTokenType(
                maintainers = listOf(maintainer1, maintainer2),
                observers = listOf(observer1, observer2),
                linearId = linearId
        )
    }

    fun withDifferingMaintainersAndParticipants(linearId: UniqueIdentifier = UniqueIdentifier(), maintainer: Party = testSuite.identity(maintainerA), participant: Party = testSuite.identity(observerC)): TestEvolvableTokenType {
        return TestEvolvableTokenType(
                maintainers = listOf(maintainer),
                participants = listOf(participant),
                linearId = linearId
        )
    }
}