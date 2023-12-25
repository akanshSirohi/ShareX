package com.akansh.plugins.common;

import com.akansh.plugins.common.InstallStatus;

public class PluginInstallStatus {

    public String message;
    public boolean error;
    public InstallStatus status;

    public PluginInstallStatus(String message, boolean error, InstallStatus status) {
        this.message = message;
        this.error = error;
        this.status = status;
    }
}
