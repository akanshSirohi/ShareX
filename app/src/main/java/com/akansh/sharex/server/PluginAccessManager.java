package com.akansh.sharex.server;

import android.content.Context;
import android.content.SharedPreferences;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Plugin-specific approval. It does not grant access to the file manager. */
public final class PluginAccessManager {
    private static final ConcurrentHashMap<String, String> states = new ConcurrentHashMap<>();
    private static final Set<String> sessionGrants = ConcurrentHashMap.newKeySet();
    private final SharedPreferences preferences;

    public PluginAccessManager(Context context) {
        preferences = context.getSharedPreferences("plugin_access", Context.MODE_PRIVATE);
    }

    private static String hash(String input) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder key = new StringBuilder();
            for (byte item : digest) key.append(String.format(java.util.Locale.ROOT, "%02x", item & 255));
            return key.toString();
        } catch (Exception error) { throw new IllegalStateException(error); }
    }

    private static String grantKey(String device, String plugin) { return "grant_" + hash(device + "|" + plugin); }
    private static String deviceKey(String device) { return "device_" + hash(device); }

    private static String requestKey(String device, String plugin) { return device + "|" + plugin; }

    public String state(String device, String plugin) {
        if (device == null || device.isEmpty()) return null;
        String key = requestKey(device, plugin);
        if (preferences.getBoolean(grantKey(device, plugin), false) || sessionGrants.contains(key)) return "granted";
        return states.get(key);
    }

    public boolean markPending(String device, String plugin) {
        return states.putIfAbsent(requestKey(device, plugin), "pending") == null;
    }

    public void resolve(String device, String plugin, boolean allow, boolean remember) {
        String key = requestKey(device, plugin);
        if (allow && remember) {
            Set<String> packages = preferences.getStringSet(deviceKey(device), ConcurrentHashMap.newKeySet());
            Set<String> updated = ConcurrentHashMap.newKeySet();
            updated.addAll(packages);
            updated.add(plugin);
            preferences.edit().putBoolean(grantKey(device, plugin), true).putStringSet(deviceKey(device), updated).apply();
        }
        else if (allow) sessionGrants.add(key);
        states.put(key, allow ? "granted" : "denied");
    }

    public boolean hasAnyGrant(String device) {
        if (device == null || device.isEmpty()) return false;
        Set<String> packages = preferences.getStringSet(deviceKey(device), null);
        if (packages != null && !packages.isEmpty()) return true;
        String prefix = requestKey(device, "");
        for (String key : sessionGrants) if (key.startsWith(prefix)) return true;
        return false;
    }

    public boolean isGranted(String device, String plugin) {
        return "granted".equals(state(device, plugin));
    }

    public void clearSessionGrants() {
        sessionGrants.clear();
        states.clear();
    }
}
