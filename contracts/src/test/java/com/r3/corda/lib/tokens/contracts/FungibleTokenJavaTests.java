package com.r3.corda.lib.tokens.contracts;

import com.r3.corda.lib.tokens.contracts.states.FungibleToken;
import com.r3.corda.lib.tokens.contracts.types.IssuedTokenType;
import org.junit.Test;

import static com.r3.corda.lib.tokens.contracts.utilities.AmountUtilities.amount;
import static com.r3.corda.lib.tokens.testing.states.Rubles.RUB;

public class FungibleTokenJavaTests extends ContractTestCommon {
    @Test
    public void testFungibleToken() {
        IssuedTokenType issuedRubles = new IssuedTokenType(ALICE.getParty(), RUB);
        new FungibleToken(amount(10, issuedRubles), ALICE.getParty());
    }
}
