package com.akansh.fileserversuit.server;

/** A display label from the browser's own request header; never used for authorization. */
public final class BrowserName {
    private BrowserName() {}
    public static String fromUserAgent(String value) {
        String agent = value == null ? "" : value;
        String browser = agent.contains("Edg/") || agent.contains("EdgA/") || agent.contains("EdgiOS/") ? "Edge"
                : agent.contains("OPR/") || agent.contains("Opera/") ? "Opera"
                : agent.contains("SamsungBrowser/") ? "Samsung Internet"
                : agent.contains("Firefox/") || agent.contains("FxiOS/") ? "Firefox"
                : agent.contains("Chrome/") || agent.contains("CriOS/") ? "Chrome"
                : agent.contains("Safari/") ? "Safari" : "Browser";
        String platform = agent.contains("iPad") ? "iPad" : agent.contains("iPhone") ? "iPhone"
                : agent.contains("Android") ? "Android" : agent.contains("Windows") ? "Windows"
                : agent.contains("Macintosh") || agent.contains("Mac OS X") ? "Mac"
                : agent.contains("CrOS") ? "ChromeOS" : agent.contains("Linux") ? "Linux" : "";
        return platform.isEmpty() ? browser : browser + " on " + platform;
    }
}
