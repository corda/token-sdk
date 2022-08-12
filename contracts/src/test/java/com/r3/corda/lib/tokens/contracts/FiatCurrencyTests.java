package com.r3.corda.lib.tokens.contracts;

import com.r3.corda.lib.tokens.contracts.types.TokenType;
import com.r3.corda.lib.tokens.money.FiatCurrency;
import org.junit.Assert;
import org.junit.Test;

public class FiatCurrencyTests {

    @Test
    public void FiatCurrency_Should_Return_GBP_Token_Type() {
        TokenType candidate = FiatCurrency.getInstance("GBP");

        Assert.assertEquals("GBP", candidate.getTokenIdentifier());
        Assert.assertEquals(2, candidate.getFractionDigits());
    }

    @Test
    public void FiatCurrency_Should_Return_USD_Token_Type() {
        TokenType candidate = FiatCurrency.getInstance("USD");

        Assert.assertEquals("USD", candidate.getTokenIdentifier());
        Assert.assertEquals(2, candidate.getFractionDigits());
    }

    @Test
    public void FiatCurrency_Should_Return_EUR_Token_Type() {
        TokenType candidate = FiatCurrency.getInstance("EUR");

        Assert.assertEquals("EUR", candidate.getTokenIdentifier());
        Assert.assertEquals(2, candidate.getFractionDigits());
    }
}
