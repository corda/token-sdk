package com.r3.corda.lib.tokens.workflows;

import com.r3.corda.lib.tokens.contracts.states.FungibleToken;
import com.r3.corda.lib.tokens.contracts.types.IssuedTokenType;
import com.r3.corda.lib.tokens.contracts.types.TokenType;
import com.r3.corda.lib.tokens.contracts.utilities.AmountUtilities;
import com.r3.corda.lib.tokens.money.DigitalCurrency;
import com.r3.corda.lib.tokens.money.FiatCurrency;
import com.r3.corda.lib.tokens.workflows.utilities.FungibleTokenBuilder;
import net.corda.core.contracts.Amount;
import net.corda.core.identity.CordaX500Name;
import net.corda.core.identity.Party;
import org.junit.Test;

import java.math.BigDecimal;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;

public class FungibleTokenBuilderTests {

    @Test
    public void fungibleTokenBuilderResolvesWithoutThrowing() throws TokenBuilderException, NoSuchAlgorithmException {
        CordaX500Name aliceX500Name = new CordaX500Name("Alice", "NY", "US");
        PublicKey aliceKey = KeyPairGenerator.getInstance("RSA").generateKeyPair().getPublic();
        Party aliceParty = new Party(aliceX500Name, aliceKey);

        // Token builder resolves to Amount<TokenType>
        Amount<TokenType> amountTokenType = new FungibleTokenBuilder()
                .withAmount(1)
                .ofTokenType(DigitalCurrency.getInstance("BTC"))
                .buildAmountTokenType();

        // Token builder resolves to Amount<IssuedTokenType>
        Amount<IssuedTokenType> amountIssuedTokenType = new FungibleTokenBuilder()
                .withAmount(1)
                .ofTokenType(DigitalCurrency.getInstance("BTC"))
                .issuedBy(aliceParty)
                .buildAmountIssuedTokenType();

        // Token builder resolves to FungibleState
        FungibleToken fungibleToken = new FungibleTokenBuilder()
                .withAmount(1)
                .ofTokenType(DigitalCurrency.getInstance("BTC"))
                .issuedBy(aliceParty)
                .heldBy(aliceParty)
                .buildFungibleToken();
    }

    @Test
    public void varyingInputAmountTypesAreEquivalent() throws TokenBuilderException {
        Amount<TokenType> intAmountTokenType = new FungibleTokenBuilder()
                .withAmount(1)
                .ofTokenType(FiatCurrency.getInstance("USD"))
                .buildAmountTokenType();

        Amount<TokenType> doubleAmountTokenType = new FungibleTokenBuilder()
                .withAmount(1.0)
                .ofTokenType(FiatCurrency.getInstance("USD"))
                .buildAmountTokenType();

        Amount<TokenType> bigDecimalAmountTokenType = new FungibleTokenBuilder()
                .withAmount(BigDecimal.ONE)
                .ofTokenType(FiatCurrency.getInstance("USD"))
                .buildAmountTokenType();

        Amount<TokenType> longAmountTokenType = new FungibleTokenBuilder()
                .withAmount(new Long(1))
                .ofTokenType(FiatCurrency.getInstance("USD"))
                .buildAmountTokenType();

        Amount<TokenType> total = intAmountTokenType
                .plus(doubleAmountTokenType)
                .plus(bigDecimalAmountTokenType)
                .plus(longAmountTokenType);

        assert(total.getQuantity() == AmountUtilities.of(4, FiatCurrency.getInstance("USD")).getQuantity());
    }

    @Test
    public void amountMayBeSetMoreThanOnce() throws Exception {
        Amount<TokenType> amount = new FungibleTokenBuilder()
                .withAmount(new Long(1))
                .withAmount(2)
                .ofTokenType(FiatCurrency.getInstance("USD"))
                .buildAmountTokenType();
        assert (amount.getQuantity() == 200L);
    }

    @Test
    public void unableToRetrieveAmountTokenTypeWithoutTokenType() throws Exception {
        try {
            new FungibleTokenBuilder()
                    .withAmount(new Long(1))
                    .buildAmountTokenType();
            assert(false);
        } catch(TokenBuilderException ex) {
            assert(ex.getLocalizedMessage().equals("A Token Type has not been provided to the builder."));
        }
    }

    @Test
    public void unableToRetrieveAmountIssuedTokenTypeWithoutIssuer() throws Exception {
        try {
            new FungibleTokenBuilder()
                    .withAmount(new Long(1))
                    .ofTokenType(FiatCurrency.getInstance("USD"))
                    .buildAmountIssuedTokenType();
            assert(false);
        } catch(TokenBuilderException ex) {
            assert(ex.getLocalizedMessage().equals("A token issuer has not been provided to the builder."));
        }
    }

    @Test
    public void unableToRetrieveFungibleTokenWithoutHolder() throws TokenBuilderException, NoSuchAlgorithmException {
        CordaX500Name aliceX500Name = new CordaX500Name("Alice", "NY", "US");
        PublicKey aliceKey = KeyPairGenerator.getInstance("RSA").generateKeyPair().getPublic();
        Party aliceParty = new Party(aliceX500Name, aliceKey);
        try {
            FungibleToken test = new FungibleTokenBuilder()
                    .withAmount(new Long(1))
                    .ofTokenType(FiatCurrency.getInstance("USD"))
                    .issuedBy(aliceParty)
                    .buildFungibleToken();
            assert(false);
        } catch(TokenBuilderException ex) {
            assert(ex.getLocalizedMessage().equals("A token holder has not been provided to the builder."));
        }
    }
}
