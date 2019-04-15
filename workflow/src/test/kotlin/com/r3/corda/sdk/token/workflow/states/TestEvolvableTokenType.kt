package com.r3.corda.sdk.token.workflow.states

import com.r3.corda.sdk.token.contracts.states.EvolvableTokenType
import com.r3.corda.sdk.token.workflow.contracts.TestEvolvableTokenContract
import net.corda.core.contracts.BelongsToContract
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.Party
import java.math.BigDecimal

@BelongsToContract(TestEvolvableTokenContract::class)
data class TestEvolvableTokenType(
        override val maintainers: List<Party>,
        val observers: List<Party> = emptyList(),
        override val participants: List<Party> = (maintainers + observers),
        override val linearId: UniqueIdentifier = UniqueIdentifier()
) : EvolvableTokenType() {
    override val displayTokenSize: BigDecimal = BigDecimal.ZERO
}