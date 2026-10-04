package com.akansh.fileserversuit.server;

import android.content.Context;
import android.content.SharedPreferences;

public final class WebPasswordSettings {
    private final SharedPreferences preferences;
    public static final int[] HOURS = {1, 6, 12, 24, 48, 168};
    public WebPasswordSettings(Context context) { preferences = context.getSharedPreferences("web_password", Context.MODE_PRIVATE); }
    public boolean enabled() { return preferences.getBoolean("enabled", false); }
    public boolean configured() { return !passwordHash().isEmpty(); }
    public String passwordHash() { return preferences.getString("hash", ""); }
    public boolean exemptRemembered() { return preferences.getBoolean("exempt", false); }
    public int hours() { return preferences.getInt("hours", 24); }
    public long duration() { return hours() * 60L * 60 * 1000; }
    public String revision() { return preferences.getString("revision", ""); }
    public synchronized String secret() {
        synchronized (WebPasswordSettings.class) {
        String secret = preferences.getString("secret", "");
        if (secret.isEmpty()) { secret = WebSecurity.randomToken(); if (!preferences.edit().putString("secret", secret).commit()) throw new IllegalStateException("Unable to save session key"); }
        return secret;
        }
    }
    /** A single atomic revision invalidates previously issued password sessions. */
    public synchronized void save(boolean enabled, String newHash, int hours, boolean exempt) {
        boolean validHours = false;
        for (int allowed : HOURS) if (allowed == hours) validHours = true;
        if (!validHours || (enabled && (newHash == null || newHash.isEmpty()) && !configured())) throw new IllegalArgumentException("Set a password first");
        SharedPreferences.Editor editor = preferences.edit().putBoolean("enabled", enabled).putInt("hours", hours)
                .putBoolean("exempt", exempt).putString("revision", WebSecurity.randomToken());
        if (newHash != null && !newHash.isEmpty()) editor.putString("hash", newHash);
        if (!editor.commit()) throw new IllegalStateException("Unable to save password settings");
    }
}
