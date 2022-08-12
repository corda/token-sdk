package com.r3.corda.lib.tokens.contracts;

import com.r3.corda.lib.tokens.contracts.types.TokenType;
import com.r3.corda.lib.tokens.money.DigitalCurrency;
import org.junit.Assert;
import org.junit.Test;

public class DigitalCurrencyTests {

    @Test
    public void DigitalCurrency_Should_Return_XRP_Token_Type() {
        TokenType candidate = DigitalCurrency.getInstance("XRP");

        Assert.assertEquals("Ripple", candidate.getTokenIdentifier());
        Assert.assertEquals(6, candidate.getFractionDigits());
    }

    @Test
    public void DigitalCurrency_Should_Return_BTC_Token_Type() {
        TokenType candidate = DigitalCurrency.getInstance("BTC");

        Assert.assertEquals("Bitcoin", candidate.getTokenIdentifier());
        Assert.assertEquals(8, candidate.getFractionDigits());
    }

    @Test
    public void DigitalCurrency_Should_Return_ETH_Token_Type() {
        TokenType candidate = DigitalCurrency.getInstance("ETH");

        Assert.assertEquals("Ethereum", candidate.getTokenIdentifier());
        Assert.assertEquals(18, candidate.getFractionDigits());
    }

    @Test
    public void DigitalCurrency_Should_Return_DOGE_Token_Type() {
        TokenType candidate = DigitalCurrency.getInstance("DOGE");

        Assert.assertEquals("Dogecoin", candidate.getTokenIdentifier());
        Assert.assertEquals(8, candidate.getFractionDigits());
    }
}
