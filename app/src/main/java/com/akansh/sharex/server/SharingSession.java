package com.akansh.sharex.server;

/** Monotonic session clock, reset when sharing starts or stops. */
public final class SharingSession {
    private static long startedAt = -1;
    private SharingSession() {}
    public static synchronized void start(long elapsedRealtime) { startedAt = elapsedRealtime; }
    public static synchronized void stop() { startedAt = -1; }
    public static synchronized boolean isRunning() { return startedAt >= 0; }
    public static synchronized long elapsed(long elapsedRealtime) { return startedAt < 0 ? 0 : Math.max(0, elapsedRealtime - startedAt); }
}
