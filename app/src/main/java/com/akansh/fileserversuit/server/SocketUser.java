package com.akansh.fileserversuit.server;

import androidx.annotation.NonNull;

import org.json.JSONObject;

public class SocketUser {
    private final String uuid;
    private final String public_data;
    private final String plugin_package;
    private boolean is_alive = true;

    public SocketUser(String uuid, String public_data, String plugin_package) {
        this.uuid = uuid;
        this.public_data = public_data;
        this.plugin_package = plugin_package;
    }

    public String getUuid() {
        return uuid;
    }

    public String getPublic_data() {
        return public_data;
    }

    public String getPlugin_package() {
        return plugin_package;
    }

    public boolean is_alive() {
        return is_alive;
    }

    public void set_alive(boolean is_alive) {
        this.is_alive = is_alive;
    }

    public JSONObject getJSONObject() {
        JSONObject jsonObject = new JSONObject();
        try {
            jsonObject.put("uuid", uuid);
            jsonObject.put("public_data", public_data);
            jsonObject.put("plugin_package", plugin_package);
            return jsonObject;
        }catch (Exception e) {}
        return jsonObject;
    }

    @NonNull
    @Override
    public String toString() {
        JSONObject jsonObject = getJSONObject();
        return jsonObject.toString();
    }
}
