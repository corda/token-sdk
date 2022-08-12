package com.r3.corda.lib.tokens.contracts;

import com.r3.corda.lib.tokens.contracts.states.NonFungibleToken;
import com.r3.corda.lib.tokens.contracts.types.IssuedTokenType;
import com.r3.corda.lib.tokens.testing.tokentypes.Ruble;
import net.corda.core.contracts.UniqueIdentifier;
import org.junit.Test;

public class NonFungibleTokenJavaTests extends ContractTestCommon {
    @Test
    public void testFungibleToken() {
        IssuedTokenType issuedRubles = new IssuedTokenType(ALICE.getParty(), Ruble.INSTANCE);
        new NonFungibleToken(issuedRubles, ALICE.getParty(), new UniqueIdentifier());
    }
}
