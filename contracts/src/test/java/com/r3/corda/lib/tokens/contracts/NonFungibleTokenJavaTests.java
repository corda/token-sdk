package com.r3.corda.lib.tokens.contracts;

import com.r3.corda.lib.tokens.contracts.states.NonFungibleToken;
import com.r3.corda.lib.tokens.contracts.types.IssuedTokenType;
import net.corda.core.contracts.UniqueIdentifier;
import org.junit.Test;

import static com.r3.corda.lib.tokens.testing.states.Rubles.RUB;

public class NonFungibleTokenJavaTests extends ContractTestCommon {
    @Test(timeout = 300_000)
    public void testFungibleToken() {
        IssuedTokenType issuedRubles = new IssuedTokenType(ALICE.getParty(), RUB);
        new NonFungibleToken(issuedRubles, ALICE.getParty(), new UniqueIdentifier());
    }
}
