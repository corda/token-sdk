package com.r3.corda.lib.tokens.workflows;

import com.r3.corda.lib.tokens.contracts.states.NonFungibleToken;
import com.r3.corda.lib.tokens.contracts.types.IssuedTokenType;
import com.r3.corda.lib.tokens.money.DigitalCurrency;
import com.r3.corda.lib.tokens.money.FiatCurrency;
import com.r3.corda.lib.tokens.workflows.utilities.NonFungibleTokenBuilder;
import net.corda.core.identity.CordaX500Name;
import net.corda.core.identity.Party;
import org.junit.Test;

import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;

public class NonFungibleTokenBuilderTests {

    @Test
    public void NonFungibleTokenBuilderResolvesWithoutThrowing() throws TokenBuilderException, NoSuchAlgorithmException {
        CordaX500Name aliceX500Name = new CordaX500Name("Alice", "NY", "US");
        PublicKey aliceKey = KeyPairGenerator.getInstance("RSA").generateKeyPair().getPublic();
        Party aliceParty = new Party(aliceX500Name, aliceKey);

        // Token builder resolves to Amount<TokenType>
        IssuedTokenType issuedTokenType = new NonFungibleTokenBuilder()
                .ofTokenType(DigitalCurrency.getInstance("BTC"))
                .issuedBy(aliceParty)
                .buildIssuedTokenType();

        // Token builder resolves to Amount<IssuedTokenType>
        NonFungibleToken amountIssuedTokenType = new NonFungibleTokenBuilder()
                .ofTokenType(DigitalCurrency.getInstance("BTC"))
                .issuedBy(aliceParty)
                .heldBy(aliceParty)
                .buildNonFungibleToken();
    }

    @Test
    public void TokenTypeMayBeSetMoreThanOnce() throws NoSuchAlgorithmException, TokenBuilderException {
        CordaX500Name aliceX500Name = new CordaX500Name("Alice", "NY", "US");
        PublicKey aliceKey = KeyPairGenerator.getInstance("RSA").generateKeyPair().getPublic();
        Party aliceParty = new Party(aliceX500Name, aliceKey);
        IssuedTokenType issuedTokenType = new NonFungibleTokenBuilder()
                .ofTokenType(FiatCurrency.getInstance("USD"))
                .ofTokenType(FiatCurrency.getInstance("CAD"))
                .issuedBy(aliceParty)
                .buildIssuedTokenType();
        assert (issuedTokenType.getIssuer() == aliceParty);
    }

    @Test
    public void UnableToRetrieveIssuedTokenTypeWithoutTokenType() throws NoSuchAlgorithmException, TokenBuilderException {
        CordaX500Name aliceX500Name = new CordaX500Name("Alice", "NY", "US");
        PublicKey aliceKey = KeyPairGenerator.getInstance("RSA").generateKeyPair().getPublic();
        Party aliceParty = new Party(aliceX500Name, aliceKey);
        try {
            new NonFungibleTokenBuilder()
                    .issuedBy(aliceParty)
                    .buildIssuedTokenType();
            assert(false);
        } catch(TokenBuilderException ex) {
            assert(ex.getLocalizedMessage().equals("A token type has not been provided to the builder."));
        }
    }

    @Test
    public void UnableToRetrieveIssuedTokenTypeWithoutIssuer() throws TokenBuilderException {
        try {
            new NonFungibleTokenBuilder()
                    .ofTokenType(FiatCurrency.getInstance("USD"))
                    .buildIssuedTokenType();
            assert(false);
        } catch(TokenBuilderException ex) {
            assert(ex.getLocalizedMessage().equals("A token issuer has not been provided to the builder."));
        }
    }

    @Test
    public void UnableToRetrieveNonFungibleTokenWithoutHolder() throws TokenBuilderException, NoSuchAlgorithmException {
        CordaX500Name aliceX500Name = new CordaX500Name("Alice", "NY", "US");
        PublicKey aliceKey = KeyPairGenerator.getInstance("RSA").generateKeyPair().getPublic();
        Party aliceParty = new Party(aliceX500Name, aliceKey);
        try {
            NonFungibleToken testToken = new NonFungibleTokenBuilder()
                    .ofTokenType(FiatCurrency.getInstance("USD"))
                    .issuedBy(aliceParty)
                    .buildNonFungibleToken();
            assert(false);
        } catch(TokenBuilderException ex) {
            assert(ex.getLocalizedMessage().equals("A token holder has not been provided to the builder."));
        }
    }
}
