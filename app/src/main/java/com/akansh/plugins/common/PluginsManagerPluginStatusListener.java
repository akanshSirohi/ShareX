package com.akansh.plugins.common;

public interface PluginsManagerPluginStatusListener {
    void onPluginUpdateDownload(boolean res, String packageName);
    void onNewPluginDownload(boolean res, String packageName);
}
