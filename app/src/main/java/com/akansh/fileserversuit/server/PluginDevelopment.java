package com.akansh.fileserversuit.server;

import android.content.Context;
import android.content.SharedPreferences;
import com.akansh.fileserversuit.common.Constants;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.QueryStringDecoder;

/** Explicitly enabled credentials for plugin sockets, separate from file-sharing access. */
public final class PluginDevelopment {
    public static final String SOCKET_PATH = "/__sharex_dev";
    private static final String TOKEN = "PLUGIN_DEV_TOKEN";
    private final SharedPreferences preferences;

    public PluginDevelopment(Context context) {
        preferences = context.getSharedPreferences(context.getPackageName(), Context.MODE_PRIVATE);
    }

    public synchronized void setEnabled(boolean enabled) {
        SharedPreferences.Editor editor = preferences.edit().putBoolean(Constants.PLUGIN_DEV, enabled);
        if (enabled) editor.putString(TOKEN, WebSecurity.randomToken());
        else editor.remove(TOKEN);
        editor.apply();
    }

    public synchronized String token() {
        if (!preferences.getBoolean(Constants.PLUGIN_DEV, false)) return "";
        String token = preferences.getString(TOKEN, "");
        if (token.isEmpty()) {
            token = WebSecurity.randomToken();
            preferences.edit().putString(TOKEN, token).apply();
        }
        return token;
    }

    public String socketPackage(HttpRequest request) {
        return authorizedPackage(request.uri(), preferences.getBoolean(Constants.PLUGIN_DEV, false),
                preferences.getString(TOKEN, ""));
    }

    static String authorizedPackage(String uri, boolean enabled, String expectedToken) {
        if (!enabled || expectedToken == null || !expectedToken.matches("[a-f0-9]{64}")) return null;
        try {
            QueryStringDecoder query = new QueryStringDecoder(uri);
            if (!SOCKET_PATH.equals(query.path())) return null;
            List<String> tokens = query.parameters().get("token");
            List<String> packages = query.parameters().get("package");
            if (tokens == null || tokens.size() != 1 || packages == null || packages.size() != 1) return null;
            String supplied = tokens.get(0), packageName = packages.get(0);
            if (!supplied.matches("[a-f0-9]{64}") || !packageName.matches("[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)*")) return null;
            if (packageName.length() > 128 || !MessageDigest.isEqual(expectedToken.getBytes(StandardCharsets.US_ASCII),
                    supplied.getBytes(StandardCharsets.US_ASCII))) return null;
            return "dev." + packageName;
        } catch (IllegalArgumentException error) { return null; }
    }
}
