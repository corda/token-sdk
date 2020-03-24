package com.r3.corda.lib.tokens.workflows;

import com.r3.corda.lib.tokens.contracts.states.FungibleToken;
import com.r3.corda.lib.tokens.contracts.types.IssuedTokenType;
import com.r3.corda.lib.tokens.contracts.types.TokenType;
import com.r3.corda.lib.tokens.contracts.utilities.AmountUtilities;
import com.r3.corda.lib.tokens.money.DigitalCurrency;
import com.r3.corda.lib.tokens.money.FiatCurrency;
import com.r3.corda.lib.tokens.workflows.utilities.TokenBuilder;
import net.corda.core.contracts.Amount;
import net.corda.core.identity.CordaX500Name;
import net.corda.core.identity.Party;
import net.corda.testing.node.StartedMockNode;
import org.junit.Test;

import java.math.BigDecimal;

public class TokenBuilderTests extends JITMockNetworkTests {

    @Test
    public void TokenBuilderResolvesWithoutThrowing() throws Exception {
        CordaX500Name aliceX500Name = new CordaX500Name("Alice", "NY", "US");
        StartedMockNode alice = node(aliceX500Name);
        Party aliceParty = alice.getInfo().getLegalIdentities().get(0);

        // Token builder resolves to Amount<TokenType>
        Amount<TokenType> amountTokenType = new TokenBuilder()
                .withAmountValue(1)
                .of(DigitalCurrency.getInstance("BTC"))
                .buildAmountTokenType();

        // Token builder resolves to Amount<IssuedTokenType>
        Amount<IssuedTokenType> amountIssuedTokenType = new TokenBuilder()
                .withAmountValue(1)
                .of(DigitalCurrency.getInstance("BTC"))
                .issuedBy(aliceParty)
                .buildAmountIssuedTokenType();

        // Token builder resolves to FungibleState
        FungibleToken fungibleToken = new TokenBuilder()
                .withAmountValue(1)
                .of(DigitalCurrency.getInstance("BTC"))
                .issuedBy(aliceParty)
                .heldBy(aliceParty)
                .buildFungibleToken();
    }

    @Test
    public void VaryingInputAmountTypesAreEquivalent() {
        Amount<TokenType> intAmountTokenType = new TokenBuilder()
                .withAmountValue(1)
                .of(FiatCurrency.getInstance("USD"))
                .buildAmountTokenType();

        Amount<TokenType> doubleAmountTokenType = new TokenBuilder()
                .withAmountValue(1.0)
                .of(FiatCurrency.getInstance("USD"))
                .buildAmountTokenType();

        Amount<TokenType> bigDecimalAmountTokenType = new TokenBuilder()
                .withAmountValue(BigDecimal.ONE)
                .of(FiatCurrency.getInstance("USD"))
                .buildAmountTokenType();

        Amount<TokenType> longAmountTokenType = new TokenBuilder()
                .withAmountValue(new Long(1))
                .of(FiatCurrency.getInstance("USD"))
                .buildAmountTokenType();

        Amount<TokenType> total = intAmountTokenType
                .plus(doubleAmountTokenType)
                .plus(bigDecimalAmountTokenType)
                .plus(longAmountTokenType);

        assert(total.getQuantity() == AmountUtilities.of(4, FiatCurrency.getInstance("USD")).getQuantity());
    }

    @Test
    public void AmountInitializationFailsWhenAmountIsAlreadyInitialized() throws Exception {
        CordaX500Name aliceX500Name = new CordaX500Name("Alice", "NY", "US");
        StartedMockNode alice = node(aliceX500Name);
        Party aliceParty = alice.getInfo().getLegalIdentities().get(0);
        try {
            new TokenBuilder()
                    .withAmountValue(new Long(1))
                    .withAmountValue(1);
            assert(false);
        } catch(Exception ex) {
            assert(ex instanceof TokenBuilderException);
            assert(ex.getLocalizedMessage().equals("The token amount has already been initialized"));
        }
    }

    @Test
    public void UnableToRetrieveAmountTokenTypeWithoutTokenType() throws Exception {
        try {
            new TokenBuilder()
                    .withAmountValue(new Long(1))
                    .buildAmountTokenType();
            assert(false);
        } catch(Exception ex) {
            assert(ex instanceof TokenBuilderException);
            assert(ex.getLocalizedMessage().equals("An amount value has not been provided to the builder."));
        }
    }

    @Test
    public void UnableToRetrieveAmountIssuedTokenTypeWithoutIssuer() throws Exception {
        try {
            new TokenBuilder()
                    .withAmountValue(new Long(1))
                    .of(FiatCurrency.getInstance("USD"))
                    .buildAmountIssuedTokenType();
            assert(false);
        } catch(Exception ex) {
            assert(ex instanceof TokenBuilderException);
            assert(ex.getLocalizedMessage().equals("An token issuer has not been provided to the builder."));
        }
    }

    @Test
    public void UnableToRetrieveFungibleTokenWithoutHolder() throws Exception {
        CordaX500Name aliceX500Name = new CordaX500Name("Alice", "NY", "US");
        StartedMockNode alice = node(aliceX500Name);
        Party aliceParty = alice.getInfo().getLegalIdentities().get(0);
        try {
            new TokenBuilder()
                    .withAmountValue(new Long(1))
                    .of(FiatCurrency.getInstance("USD"))
                    .issuedBy(aliceParty)
                    .buildFungibleToken();
            assert(false);
        } catch(Exception ex) {
            assert(ex instanceof TokenBuilderException);
            assert(ex.getLocalizedMessage().equals("A token holder has not been provided to the builder."));
        }
    }
}
