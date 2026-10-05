package com.akansh.fileserversuit.server;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.os.IBinder;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.akansh.fileserversuit.ui.MainActivity;
import com.akansh.fileserversuit.R;
import com.akansh.fileserversuit.common.Constants;
import com.akansh.fileserversuit.common.Utils;


public class ServerService extends Service {

    public Context context = this;
    private static final int NOTIFICATION_ID = 2;
    public static final String ACTION_REFRESH_NOTIFICATION = "com.akansh.fileserversuit.REFRESH_RUNNING_NOTIFICATION";
    private WebServer webServer;
    private WebServerSocket webServerSocket;
    Utils utils=new Utils(context);

    @Override public void onCreate() {
        super.onCreate();
        showForegroundNotification("Preparing local file sharing…");
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_REFRESH_NOTIFICATION.equals(intent.getAction())) {
            if (webServer != null) showForegroundNotification("Running At: " + utils.loadString(Constants.SERVER_URL));
            return START_STICKY;
        }
        if(intent == null || intent.getAction() == null) {
            if (webServer != null) return START_STICKY;
            try {
                String host = utils.getIPAddress(true);
                int port = utils.loadInt(Constants.SERVER_PORT,Constants.SERVER_PORT_DEFAULT);
                if (port < 1024 || port > 65534) throw new IllegalArgumentException("Sharing port must be between 1024 and 65534; plugins use the next port");
                webServer = new WebServer(host, port);
                webServer.setContext(context);
                webServer.setRoot(utils.loadRoot());
                webServer.setAllowHiddenMedia(utils.loadSetting(Constants.LOAD_HIDDEN_MEDIA));
                TransferStats.reset();
                SharingSession.start(android.os.SystemClock.elapsedRealtime());
                webServer.start();
                String prefix = utils.loadSetting(Constants.SSL) ? "https://" : "http://";
                String url = prefix + webServer.getHostname() + ":" + webServer.getListeningPort();
                sendLog(Constants.ACTION_URL, "url", url);
                utils.saveString(Constants.SERVER_URL, url);
                showForegroundNotification("Running At: " + url);
                PluginDevelopment development = new PluginDevelopment(this);
                development.token();
                webServerSocket = new WebServerSocket(port + 1, this.getApplication().getPackageName(), webServer::isAuthorized,
                        webServer::isPluginSocketAuthorized, webServer.getSslContext(), development::socketPackage);
                webServerSocket.start();
            } catch (Exception e) {
                SharingSession.stop();
                Toast.makeText(this, "Server Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                sendLog(Constants.ACTION_UPDATE_UI_STOP,"","");
                sendLog(Constants.ACTION_MSG,"msg","Try to change ShareX port");
                stopForeground(true);
                stopSelf();
            }
        }else if(intent.getAction().equals(Constants.ACTION_STOP_SERVICE)) {
            SharingSession.stop();
            sendLog(Constants.ACTION_UPDATE_UI_STOP,"","");
            stopForeground(true);
            stopSelf();
            return START_NOT_STICKY;
        }
        return Service.START_STICKY;
    }

    @Override
    public void onDestroy() {
        SharingSession.stop();
        new PluginAccessManager(this).clearSessionGrants();
        try (DeviceManager devices = new DeviceManager(this)) { devices.clearTmp(); }
        TransferStats.flush();
        stopForeground(true);
        if(webServer!=null) {
            webServer.closeAllConnections();
            webServer.stop();
        }
        if(webServerSocket!= null) {
            webServerSocket.closeAllConnections();
            webServerSocket.stop();
        }
        sendLog(Constants.ACTION_MSG,"msg","Server Stopped!");
        utils.clearCache();
        utils.clearTemp();
        utils.clearThumbs();
        super.onDestroy();
    }

    public void sendLog(String action,String key,String value) {
        Intent local = new Intent();
        local.setAction(Constants.BROADCAST_SERVICE_TO_ACTIVITY);
        local.putExtra("action",action);
        local.putExtra(key,value);
        local.setPackage(context.getPackageName());
        context.sendBroadcast(local);
    }

    private void showForegroundNotification(String contentText) {
        // Open App Intent
        Intent showTaskIntent = new Intent(getApplicationContext(), MainActivity.class);
        showTaskIntent.setAction(Intent.ACTION_MAIN);
        showTaskIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        showTaskIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        PendingIntent contentIntent;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            contentIntent = PendingIntent.getActivity(
                    getApplicationContext(),
                    0,
                    showTaskIntent,
                    PendingIntent.FLAG_IMMUTABLE);
        }else{
            contentIntent = PendingIntent.getActivity(
                    getApplicationContext(),
                    0,
                    showTaskIntent,
                    PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        }

        // Stop Service Intent
        Intent stopSelf = new Intent(this.getApplicationContext(), ServerService.class);
        stopSelf.setAction(Constants.ACTION_STOP_SERVICE);

        PendingIntent pStopSelf = PendingIntent.getService(getApplicationContext(), 1, stopSelf,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);


        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            String NOTIFICATION_CHANNEL_ID = getPackageName();
            String channelName = getString(R.string.app_name);
            NotificationChannel chan = new NotificationChannel(NOTIFICATION_CHANNEL_ID, channelName, NotificationManager.IMPORTANCE_HIGH);
            chan.setLightColor(Color.BLUE);
            chan.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);
            NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            assert manager != null;
            manager.createNotificationChannel(chan);
            String cTitle=getString(R.string.app_name)+" is running...";
            if(utils.loadSetting(Constants.PRIVATE_MODE)) {
                cTitle=getString(R.string.app_name)+" is running in private mode...";
            }
            NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID);
            Notification notification = notificationBuilder.setOngoing(true)
                    .setOnlyAlertOnce(true)
                    .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
                    .setSmallIcon(R.drawable.ic_notification)
                    .setContentTitle(cTitle)
                    .setContentText(contentText)
                    .setContentIntent(contentIntent)
                    .setWhen(System.currentTimeMillis())
                    .setPriority(NotificationCompat.PRIORITY_LOW)
                    .setCategory(Notification.CATEGORY_SERVICE)
                    .addAction(R.drawable.ic_xmark,"Stop", pStopSelf)
                    .build();
            startForeground(NOTIFICATION_ID, notification);
        }else {
            Notification notification = new Notification.Builder(getApplicationContext())
                    .setSmallIcon(R.drawable.ic_notification)
                    .setContentTitle(getString(R.string.app_name)+" is running...")
                    .setContentText(contentText)
                    .setContentIntent(contentIntent)
                    .setWhen(System.currentTimeMillis())
                    .addAction(R.drawable.ic_xmark,"Stop", pStopSelf)
                    .build();
            startForeground(NOTIFICATION_ID, notification);
        }
    }
}
