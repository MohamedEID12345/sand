package com.legalguide.app.core.util;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class MoneyTest {

    @Test
    public void formatsWholeAmountsWithoutDecimals() {
        assertEquals("1,500 ج.م", Money.format(1500, "EGP"));
        assertEquals("99 ج.م", Money.format(99, null));
    }

    @Test
    public void formatsFractionsWithTwoDecimals() {
        assertEquals("1,249.50 ج.م", Money.format(1249.5, "EGP"));
    }

    @Test
    public void otherCurrencies() {
        assertEquals("$", Money.symbol("usd"));
        assertEquals("SAR", Money.symbol("SAR"));
    }

    @Test
    public void signedNumbers() {
        assertEquals("+250", Money.signed(250));
        assertEquals("-3", Money.signed(-3));
        assertEquals("0", Money.signed(0));
        assertEquals("12,000", Money.number(12000));
    }
}
