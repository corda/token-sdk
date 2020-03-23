package com.r3.corda.lib.tokens.workflows;

import com.r3.corda.lib.tokens.money.DigitalCurrency;
import com.r3.corda.lib.tokens.workflows.utilities.TokenBuilder;
import net.corda.core.identity.CordaX500Name;
import net.corda.core.identity.Party;
import net.corda.testing.node.StartedMockNode;
import org.junit.Test;

import java.math.BigDecimal;

public class TokenBuilderTests extends JITMockNetworkTests {

    @Test
    public void TokenBuilderTest() throws Exception {
        CordaX500Name aliceX500Name = new CordaX500Name("Alice", "NY", "US");
        StartedMockNode alice = node(aliceX500Name);
        Party aliceParty = alice.getInfo().getLegalIdentities().get(0);

        new TokenBuilder()
                .withAmount(1)
                .of(DigitalCurrency.getInstance("BTC"))
                .resolveAmountTokenType();

        new TokenBuilder()
                .withAmount(1)
                .of(DigitalCurrency.getInstance("BTC"))
                .issuedBy(aliceParty)
                .resolveAmountIssuedTokenType();

        new TokenBuilder()
                .withAmount(1)
                .of(DigitalCurrency.getInstance("BTC"))
                .issuedBy(aliceParty)
                .heldBy(aliceParty)
                .resolveFungibleState();
    }

    @Test
    public void AmountTokenTypeWithInt() {
        new TokenBuilder()
                .withAmount(1)
                .of(DigitalCurrency.getInstance("BTC"))
                .resolveAmountTokenType();
    }

    @Test
    public void AmountTokenTypeWithDouble() {
        new TokenBuilder()
                .withAmount(1.0)
                .of(DigitalCurrency.getInstance("BTC"))
                .resolveAmountTokenType();
    }

    @Test
    public void AmountTokenTypeWithBigDecimal() {
        new TokenBuilder()
                .withAmount(BigDecimal.ONE)
                .of(DigitalCurrency.getInstance("BTC"))
                .resolveAmountTokenType();
    }

    @Test
    public void AmountTokenTypeWithLong() {
        new TokenBuilder()
                .withAmount(new Long(1))
                .of(DigitalCurrency.getInstance("BTC"))
                .resolveAmountTokenType();
    }

    @Test
    public void InitializationFailsWhenAlreadyInitialized() throws Exception {
        CordaX500Name aliceX500Name = new CordaX500Name("Alice", "NY", "US");
        StartedMockNode alice = node(aliceX500Name);
        Party aliceParty = alice.getInfo().getLegalIdentities().get(0);

        try {
            new TokenBuilder()
                    .withAmount(new Long(1))
                    .withAmount(1);
            assert(false);
        } catch(Exception ex) {
            assert(ex.getLocalizedMessage().equals("The token amount has already been initialized"));
        }

    }
}
