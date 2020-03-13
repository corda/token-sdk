package com.r3.corda.lib.tokens.money;

import com.r3.corda.lib.tokens.contracts.types.TokenType;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public class CurrencyAccessFromJavaTest {

    /**
     * Sanity check for easy access of digital currency TokenTypes from java code
     */
    @Test
    public void javaWrappedDigitalCurrencyIsIdenticalToKotlinCompanionObject() throws Exception {

        List<String> digitalCurrencies = Arrays.asList("XRP", "BTC", "ETH", "DOGE");

        digitalCurrencies.forEach((currencyCode) -> {
            TokenType kotlinFiat = DigitalCurrency.Companion.getInstance(currencyCode);
            TokenType javaStatic = DigitalCurrency.getInstance(currencyCode);
            TokenType javaWrappedFiat = new DigitalCurrency(currencyCode);

            assert(kotlinFiat.getTokenIdentifier().equals(javaWrappedFiat.getTokenIdentifier()));
            assert(kotlinFiat.getTokenIdentifier().equals(javaStatic.getTokenIdentifier()));
            assert(kotlinFiat.getFractionDigits() == javaWrappedFiat.getFractionDigits());
            assert(kotlinFiat.getFractionDigits() == javaStatic.getFractionDigits());
        });
    }

    /**
     * Sanity check for easy access of fiat currency TokenTypes from java code
     */
    @Test
    public void javaWrappedFiatCurrencyIsIdenticalToKotlinCompanionObject() throws Exception {

        List<String> fiatCurrencies = Arrays.asList("GBP", "USD", "EUR", "CHF", "JPY", "CAD", "AUD", "NZD");

        fiatCurrencies.forEach((currencyCode) -> {
            TokenType kotlinFiat = FiatCurrency.Companion.getInstance(currencyCode);
            TokenType javaStatic = FiatCurrency.getInstance(currencyCode);
            TokenType javaWrappedFiat = new FiatCurrency(currencyCode);

            assert(kotlinFiat.getTokenIdentifier().equals(javaWrappedFiat.getTokenIdentifier()));
            assert(kotlinFiat.getTokenIdentifier().equals(javaStatic.getTokenIdentifier()));
            assert(kotlinFiat.getFractionDigits() == javaStatic.getFractionDigits());
            assert(kotlinFiat.getFractionDigits() == javaWrappedFiat.getFractionDigits());
        });
    }
}

