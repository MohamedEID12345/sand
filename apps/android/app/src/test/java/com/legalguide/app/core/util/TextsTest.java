package com.legalguide.app.core.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import java.util.Arrays;

public class TextsTest {

    @Test
    public void arabicDigitsAreNormalized() {
        assertEquals("2025", Texts.asciiDigits("٢٠٢٥"));
        assertEquals("123", Texts.asciiDigits("۱۲۳"));
        assertEquals("", Texts.asciiDigits(null));
    }

    @Test
    public void joiningSkipsBlanks() {
        assertEquals("a • b", Texts.joinNonBlank(" • ", "a", null, " ", "b"));
        assertEquals("x، y", Texts.join(Arrays.asList("x", "y"), "، "));
    }

    @Test
    public void helpers() {
        assertNull(Texts.nullIfBlank("   "));
        assertEquals("—", Texts.orDash(null));
        assertEquals("1.5 MB", Texts.fileSize(1572864));
    }
}
