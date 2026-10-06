package com.akansh.sharex.server;

import org.junit.Test;
import static org.junit.Assert.*;

public class PluginDevelopmentTest {
    private final String token = "a".repeat(64);
    private String uri() { return "/__sharex_dev?token=" + token + "&package=sharex.starter.plugin"; }

    @Test public void acceptsOnlyEnabledCredentialAndIsolatesDevelopmentData() {
        assertEquals("dev.sharex.starter.plugin", PluginDevelopment.authorizedPackage(uri(), true, token));
        assertNull(PluginDevelopment.authorizedPackage(uri(), false, token));
        assertNull(PluginDevelopment.authorizedPackage(uri(), true, "b".repeat(64)));
        assertNull(PluginDevelopment.authorizedPackage(uri(), true, ""));
    }

    @Test public void rejectsFileRoutesAmbiguousCredentialsAndUnsafePackages() {
        assertNull(PluginDevelopment.authorizedPackage(uri().replace("/__sharex_dev", "/ShareX"), true, token));
        assertNull(PluginDevelopment.authorizedPackage(uri() + "&token=" + token, true, token));
        assertNull(PluginDevelopment.authorizedPackage(uri() + "&package=other", true, token));
        assertNull(PluginDevelopment.authorizedPackage(uri().replace("sharex.starter.plugin", "..%2Fother"), true, token));
        assertNull(PluginDevelopment.authorizedPackage("/__sharex_dev?token=" + token, true, token));
    }
}
