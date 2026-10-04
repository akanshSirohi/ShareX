package com.akansh.fileserversuit.server;

import org.junit.Test;
import static org.junit.Assert.*;

public class BrowserNameTest {
    @Test public void specificBrowserMarkersTakePriorityOverChromeAndSafari() {
        assertEquals("Edge on Windows", BrowserName.fromUserAgent("Windows NT 10.0 Chrome/120 Safari/537 Edg/120"));
        assertEquals("Opera on Linux", BrowserName.fromUserAgent("Linux Chrome/120 Safari/537 OPR/120"));
        assertEquals("Samsung Internet on Android", BrowserName.fromUserAgent("Linux Android Chrome/120 SamsungBrowser/24 Safari/537"));
        assertEquals("Firefox on Windows", BrowserName.fromUserAgent("Windows NT 10.0 Firefox/130"));
    }
    @Test public void iosBrowsersAndUnknownClientsHaveReadableLabels() {
        assertEquals("Chrome on iPhone", BrowserName.fromUserAgent("iPhone Mac OS X CriOS/130 Safari/604"));
        assertEquals("Firefox on iPad", BrowserName.fromUserAgent("iPad Mac OS X FxiOS/130 Safari/604"));
        assertEquals("Safari on Mac", BrowserName.fromUserAgent("Macintosh Safari/605"));
        assertEquals("Chrome on ChromeOS", BrowserName.fromUserAgent("CrOS Chrome/130 Safari/537"));
        assertEquals("Browser", BrowserName.fromUserAgent(null));
        assertEquals("Browser", BrowserName.fromUserAgent("<script>arbitrary label</script>"));
    }
}
