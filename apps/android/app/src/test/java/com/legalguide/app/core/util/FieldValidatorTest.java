package com.legalguide.app.core.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class FieldValidatorTest {

    @Test
    public void egyptianMobiles() {
        assertTrue(FieldValidator.isEgyptianMobile("01012345678"));
        assertTrue(FieldValidator.isEgyptianMobile("01112345678"));
        assertTrue(FieldValidator.isEgyptianMobile("01212345678"));
        assertTrue(FieldValidator.isEgyptianMobile("01512345678"));
        assertTrue(FieldValidator.isEgyptianMobile("+201012345678"));
        assertTrue(FieldValidator.isEgyptianMobile("٠١٠١٢٣٤٥٦٧٨"));
        assertTrue(FieldValidator.isEgyptianMobile("010 1234 5678"));
        assertFalse(FieldValidator.isEgyptianMobile("01312345678"));
        assertFalse(FieldValidator.isEgyptianMobile("0101234567"));
        assertFalse(FieldValidator.isEgyptianMobile(null));
    }

    @Test
    public void emails() {
        assertTrue(FieldValidator.isEmail("lawyer@office.com.eg"));
        assertFalse(FieldValidator.isEmail("lawyer@office"));
        assertFalse(FieldValidator.isEmail(""));
    }

    @Test
    public void passwords() {
        assertTrue(FieldValidator.isStrongPassword("Adala2025"));
        assertFalse(FieldValidator.isStrongPassword("short1"));
        assertFalse(FieldValidator.isStrongPassword("onlyletters"));
        assertFalse(FieldValidator.isStrongPassword("12345678"));
    }

    @Test
    public void dynamicFieldRules() {
        assertEquals(FieldValidator.Error.REQUIRED, FieldValidator.validate("text", true, "  ", null, null, null));
        assertNull(FieldValidator.validate("text", false, "", null, null, null));
        assertEquals(FieldValidator.Error.TOO_LONG, FieldValidator.validate("text", true, "abcdef", null, null, 5));
        assertEquals(FieldValidator.Error.INVALID_NUMBER, FieldValidator.validate("number", true, "12a", null, null, null));
        assertEquals(FieldValidator.Error.TOO_SMALL, FieldValidator.validate("money", true, "50", 100d, null, null));
        assertEquals(FieldValidator.Error.TOO_LARGE, FieldValidator.validate("number", true, "1,500", null, 1000d, null));
        assertNull(FieldValidator.validate("number", true, "١٥٠", 100d, 200d, null));
        assertEquals(FieldValidator.Error.INVALID_NATIONAL_ID, FieldValidator.validate("national_id", true, "1234", null, null, null));
        assertNull(FieldValidator.validate("national_id", true, "29001011234567", null, null, null));
        assertEquals(FieldValidator.Error.INVALID_EMAIL, FieldValidator.validate("email", true, "x@", null, null, null));
    }
}
