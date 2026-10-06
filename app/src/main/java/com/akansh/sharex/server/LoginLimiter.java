package com.akansh.sharex.server;

import java.util.HashMap;
import java.util.Map;

final class LoginLimiter {
    private final Map<String, Integer> attempts = new HashMap<>();
    private long window;
    private int total;
    synchronized boolean allow(String address, long now) {
        if (now - window >= 60_000 || now < window) { window = now; total = 0; attempts.clear(); }
        int count = attempts.getOrDefault(address, 0);
        if (total >= 20 || count >= 5) return false;
        total++; attempts.put(address, count + 1);
        return true;
    }
}
