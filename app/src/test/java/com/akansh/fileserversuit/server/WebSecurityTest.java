package com.akansh.fileserversuit.server;

import org.junit.Test;
import static org.junit.Assert.*;

public class WebSecurityTest {
    @Test public void passwordHashesAreSaltedAndRejectIncorrectPasswords() {
        String hash = WebSecurity.hashPassword("correct horse".toCharArray());
        assertNotEquals(hash, WebSecurity.hashPassword("correct horse".toCharArray()));
        assertTrue(WebSecurity.verifyPassword("correct horse".toCharArray(), hash));
        assertFalse(WebSecurity.verifyPassword("incorrect horse".toCharArray(), hash));
        assertFalse(WebSecurity.verifyPassword("correct horse".toCharArray(), "malformed"));
        assertFalse(hash.contains("correct horse"));
    }
    @Test public void sessionsHaveAbsoluteExpiryAndCannotChangeBrowserOrPolicy() {
        String secret = WebSecurity.randomToken(), revision = WebSecurity.randomToken();
        String browser = WebSecurity.browserId(WebSecurity.randomToken());
        long day = 86_400_000;
        String token = WebSecurity.session(browser, revision, 1000, day, secret);
        assertTrue(WebSecurity.validSession(token, browser, revision, 1000, day, secret));
        assertTrue(WebSecurity.validSession(token, browser, revision, 1000+day-1, day, secret));
        assertFalse(WebSecurity.validSession(token, browser, revision, 1000+day, day, secret));
        assertFalse(WebSecurity.validSession(token, browser, revision, 999, day, secret));
        assertFalse(WebSecurity.validSession(token, WebSecurity.browserId(WebSecurity.randomToken()), revision, 1000, day, secret));
        assertFalse(WebSecurity.validSession(token, browser, "changed", 1000, day, secret));
        assertFalse(WebSecurity.validSession(token, browser, revision, 1000, day/2, secret));
        assertFalse(WebSecurity.validSession(token, browser, revision, 1000, day, WebSecurity.randomToken()));
        String[] parts = token.split("\\.");
        String tampered = parts[0]+"."+(parts[1].startsWith("0") ? "1" : "0")+parts[1].substring(1);
        assertFalse(WebSecurity.validSession(tampered, browser, revision, 1000, day, secret));
        assertFalse(WebSecurity.validSession("invalid", browser, revision, 1000, day, secret));
        assertFalse(WebSecurity.validSession(token, "", revision, 1000, day, secret));
    }
    @Test public void browserIdentityRequiresAnOpaqueSecretAndNeverUsesDisplayIds() {
        String secret = WebSecurity.randomToken();
        assertEquals(64, secret.length());
        assertNotEquals(secret, WebSecurity.browserId(secret));
        assertEquals(WebSecurity.browserId(secret), WebSecurity.browserId(secret));
        assertEquals("", WebSecurity.browserId("displayed-browser-id"));
        assertEquals("", WebSecurity.browserId(null));
        assertEquals("", WebSecurity.browserId(WebSecurity.browserId(secret)));
    }
    @Test public void attemptsAreLimitedAcrossBrowserIdsAndHaveABoundedGlobalLimit() {
        LoginLimiter limiter = new LoginLimiter();
        for (int i=0;i<5;i++) assertTrue(limiter.allow("192.168.1.2", 1000));
        assertFalse(limiter.allow("192.168.1.2", 1000));
        for (int i=0;i<15;i++) assertTrue(limiter.allow("other-ip-"+i, 1000));
        assertFalse(limiter.allow("another", 1000));
        assertTrue(limiter.allow("192.168.1.2", 61_000));
    }
}
