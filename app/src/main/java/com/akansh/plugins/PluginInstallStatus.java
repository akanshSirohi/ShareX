package com.akansh.plugins;

public class PluginInstallStatus {
    enum Status {
        INSTALL, UPDATE, UNKNOWN
    }

    public String message;
    public boolean error;
    public Status status;

    public PluginInstallStatus(String message, boolean error, Status status) {
        this.message = message;
        this.error = error;
        this.status = status;
    }
}
