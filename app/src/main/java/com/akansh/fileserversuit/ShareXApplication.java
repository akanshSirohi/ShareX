package com.akansh.fileserversuit;

import android.app.Application;

public class ShareXApplication extends Application {
    @Override public void onCreate() {
        super.onCreate();
        com.akansh.fileserversuit.server.TransferStats.initialize(this);
    }
}
