package com.r3.corda.lib.tokens.workflows;

import com.r3.corda.lib.tokens.money.DigitalCurrency;
import com.r3.corda.lib.tokens.workflows.utilities.TokenBuilder;
import net.corda.core.identity.CordaX500Name;
import net.corda.core.identity.Party;
import net.corda.testing.node.StartedMockNode;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public class CurrencyAccessFromJavaTest extends JITMockNetworkTests {

    /**
     * Sanity check for easy access of digital currency TokenTypes from java code
     */
    @Test
    public void javaWrappedDigitalCurrencyIsIdenticalToKotlinCompanionObject() throws Exception {
        CordaX500Name aliceX500Name = new CordaX500Name("Alice", "NY", "US");
        StartedMockNode alice = node(aliceX500Name);
        Party aliceParty = alice.getInfo().getLegalIdentities().get(0);

        new TokenBuilder()
                .amount(1)
                .of(DigitalCurrency.getInstance("BTC"))
                .issuedBy(aliceParty)
                .heldBy(aliceParty);
    }
}