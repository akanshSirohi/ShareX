package com.akansh.sharex;

import android.app.Application;

public class ShareXApplication extends Application {
    @Override public void onCreate() {
        super.onCreate();
        com.akansh.sharex.server.TransferStats.initialize(this);
    }
}
