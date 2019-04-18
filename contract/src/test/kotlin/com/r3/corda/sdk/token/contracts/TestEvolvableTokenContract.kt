package com.r3.corda.sdk.token.contracts

import com.r3.corda.sdk.token.contracts.states.EvolvableTokenType
import net.corda.core.contracts.Contract
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.Party
import net.corda.core.transactions.LedgerTransaction
import java.math.BigDecimal

class TestEvolvableTokenContract : EvolvableTokenContract(), Contract {

    companion object {
        val ID: String = this::class.java.enclosingClass.canonicalName
    }

    override fun additionalCreateChecks(tx: LedgerTransaction) = Unit

    override fun additionalUpdateChecks(tx: LedgerTransaction) = Unit

    data class TestEvolvableTokenType(
            override val maintainers: List<Party>,
            val observers: List<Party> = emptyList(),
            override val participants: List<Party> = (maintainers + observers),
            override val linearId: UniqueIdentifier = UniqueIdentifier()
    ) : EvolvableTokenType() {
        override val displayTokenSize: BigDecimal = BigDecimal.ZERO
    }
}