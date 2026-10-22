package com.legalguide.app.core.net;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ApiErrorTest {

    @Test
    public void parsesStandardEnvelope() {
        ApiError e = ApiError.parse(422, "{\"error\":{\"code\":\"VALIDATION\",\"message\":\"بيانات غير صحيحة\","
                + "\"details\":{\"fields\":{\"phone\":\"رقم غير صحيح\",\"email\":\"مستخدم من قبل\"}}}}");
        assertEquals(422, e.http);
        assertEquals(ApiError.VALIDATION, e.code);
        assertEquals("بيانات غير صحيحة", e.message);
        assertEquals("رقم غير صحيح", e.fieldErrors.get("phone"));
        assertEquals(2, e.fieldErrors.size());
    }

    @Test
    public void paywallCodesAreDetected() {
        assertTrue(ApiError.parse(402, "{\"error\":{\"code\":\"INSUFFICIENT_CREDITS\"}}").isPaywall());
        assertTrue(ApiError.parse(402, "{\"error\":{\"code\":\"FREE_USES_EXHAUSTED\"}}").isPaywall());
        assertTrue(ApiError.parse(403, "{\"error\":{\"code\":\"PLAN_LIMIT\"}}").isPaywall());
        assertFalse(ApiError.parse(403, "{\"error\":{\"code\":\"FORBIDDEN\"}}").isPaywall());
    }

    @Test
    public void fallsBackToHttpStatusWhenBodyIsNotJson() {
        ApiError e = ApiError.parse(502, "<html>Bad Gateway</html>");
        assertEquals(ApiError.SERVER, e.code);
        assertNull(e.message);
        assertTrue(e.fieldErrors.isEmpty());
    }

    @Test
    public void mapsCommonStatuses() {
        assertEquals(ApiError.UNAUTHORIZED, ApiError.parse(401, null).code);
        assertEquals(ApiError.NOT_FOUND, ApiError.parse(404, "").code);
        assertEquals(ApiError.RATE_LIMITED, ApiError.parse(429, null).code);
        assertEquals(ApiError.MAINTENANCE, ApiError.parse(503, null).code);
        assertEquals(ApiError.FILE_TOO_LARGE, ApiError.parse(413, null).code);
    }

    @Test
    public void acceptsFlatErrorObject() {
        ApiError e = ApiError.parse(409, "{\"code\":\"CONFLICT\",\"message\":\"exists\"}");
        assertEquals(ApiError.CONFLICT, e.code);
        assertEquals("exists", e.message);
    }

    @Test
    public void networkErrorsFromThrowable() {
        assertEquals(ApiError.NETWORK, ApiError.fromThrowable(new java.net.UnknownHostException()).code);
        assertEquals(ApiError.TIMEOUT, ApiError.fromThrowable(new java.net.SocketTimeoutException()).code);
        assertEquals(ApiError.UNKNOWN, ApiError.fromThrowable(new IllegalStateException("x")).code);
    }
}
