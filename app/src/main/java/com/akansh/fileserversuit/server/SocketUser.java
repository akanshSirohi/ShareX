package com.akansh.fileserversuit.server;

import androidx.annotation.NonNull;

import org.json.JSONObject;

public class SocketUser {
    private final String uuid;
    private final String plugin_package;
    private String public_data;
    private WSDSocket wsdSocket;

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

    public void updatePublic_data(String public_data) {
        this.public_data = public_data;
    }

    public String getPlugin_package() {
        return plugin_package;
    }

    public JSONObject getJSONObject() {
        JSONObject jsonObject = new JSONObject();
        try {
            JSONObject public_data = new JSONObject(this.public_data);
            jsonObject.put("uuid", uuid);
            jsonObject.put("public_data", public_data);
            return jsonObject;
        }catch (Exception e) {}
        return jsonObject;
    }

    public WSDSocket getWsdSocket() {
        return wsdSocket;
    }

    public void setWsdSocket(WSDSocket wsdSocket) {
        this.wsdSocket = wsdSocket;
    }

    @NonNull
    @Override
    public String toString() {
        JSONObject jsonObject = getJSONObject();
        return jsonObject.toString();
    }
}
