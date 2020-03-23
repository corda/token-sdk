package com.r3.corda.lib.tokens.workflows;

import com.r3.corda.lib.tokens.contracts.states.FungibleToken;
import com.r3.corda.lib.tokens.contracts.types.IssuedTokenType;
import com.r3.corda.lib.tokens.contracts.types.TokenType;
import com.r3.corda.lib.tokens.contracts.utilities.AmountUtilities;
import com.r3.corda.lib.tokens.money.DigitalCurrency;
import com.r3.corda.lib.tokens.money.FiatCurrency;
import com.r3.corda.lib.tokens.workflows.utilities.TokenBuilder;
import kotlin.UninitializedPropertyAccessException;
import net.corda.core.contracts.Amount;
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

        // Token builder resolves to Amount<TokenType>
        Amount<TokenType> amountTokenType = new TokenBuilder()
                .withAmount(1)
                .of(DigitalCurrency.getInstance("BTC"))
                .resolveAmountTokenType();

        // Token builder resolves to Amount<IssuedTokenType>
        Amount<IssuedTokenType> amountIssuedTokenType = new TokenBuilder()
                .withAmount(1)
                .of(DigitalCurrency.getInstance("BTC"))
                .issuedBy(aliceParty)
                .resolveAmountIssuedTokenType();

        // Token builder resolves to FungibleState
        FungibleToken fungibleToken = new TokenBuilder()
                .withAmount(1)
                .of(DigitalCurrency.getInstance("BTC"))
                .issuedBy(aliceParty)
                .heldBy(aliceParty)
                .resolveFungibleToken();
    }

    @Test
    public void VaryingInputAmountTypesAreEquivalent() {
        Amount<TokenType> intAmountTokenType = new TokenBuilder()
                .withAmount(1)
                .of(FiatCurrency.getInstance("USD"))
                .resolveAmountTokenType();

        Amount<TokenType> doubleAmountTokenType = new TokenBuilder()
                .withAmount(1.0)
                .of(FiatCurrency.getInstance("USD"))
                .resolveAmountTokenType();

        Amount<TokenType> bigDecimalAmountTokenType = new TokenBuilder()
                .withAmount(BigDecimal.ONE)
                .of(FiatCurrency.getInstance("USD"))
                .resolveAmountTokenType();

        Amount<TokenType> longAmountTokenType = new TokenBuilder()
                .withAmount(new Long(1))
                .of(FiatCurrency.getInstance("USD"))
                .resolveAmountTokenType();

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
                    .withAmount(new Long(1))
                    .withAmount(1);
            assert(false);
        } catch(Exception ex) {
            assert(ex.getLocalizedMessage().equals("The token amount has already been initialized"));
        }
    }

    @Test
    public void UnableToRetrieveAmountTokenTypeWithoutTokenType() throws Exception {
        try {
            new TokenBuilder()
                    .withAmount(new Long(1))
                    .resolveAmountTokenType();
            assert(false);
        } catch(UninitializedPropertyAccessException ex) {
            assert(ex.getLocalizedMessage().equals("lateinit property amountTokenType has not been initialized"));
        }
    }

    @Test
    public void UnableToRetrieveAmountIssuedTokenTypeWithoutIssuer() throws Exception {
        try {
            new TokenBuilder()
                    .withAmount(new Long(1))
                    .of(FiatCurrency.getInstance("USD"))
                    .resolveAmountIssuedTokenType();
            assert(false);
        } catch(UninitializedPropertyAccessException ex) {
            assert(ex.getLocalizedMessage().equals("lateinit property amountIssuedTokenType has not been initialized"));
        }
    }

    @Test
    public void UnableToRetrieveFungibleTokenWithoutHolder() throws Exception {
        CordaX500Name aliceX500Name = new CordaX500Name("Alice", "NY", "US");
        StartedMockNode alice = node(aliceX500Name);
        Party aliceParty = alice.getInfo().getLegalIdentities().get(0);
        try {
            new TokenBuilder()
                    .withAmount(new Long(1))
                    .of(FiatCurrency.getInstance("USD"))
                    .issuedBy(aliceParty)
                    .resolveFungibleToken();
            assert(false);
        } catch(UninitializedPropertyAccessException ex) {
            assert(ex.getLocalizedMessage().equals("lateinit property fungibleToken has not been initialized"));
        }
    }
}
