package com.akansh.fileserversuit;

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
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import java.io.IOException;

public class ServerService extends Service {

    public Context context = this;
    private static final int NOTIFICATION_ID = 2;
    private WebServer webServer;
    Utils utils=new Utils(context);

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if(intent.getAction() == null) {
            try {
                String host = utils.getIPAddress(true);
                webServer = new WebServer(host, utils.loadInt(Constants.SERVER_PORT,Constants.SERVER_PORT_DEFAULT));
                webServer.setContext(context);
                webServer.setRoot(utils.loadRoot());
                webServer.setAllowHiddenMedia(utils.loadSetting(Constants.LOAD_HIDDEN_MEDIA));
                webServer.start();
                String url = "http://" + webServer.getHostname() + ":" + webServer.getListeningPort();
                sendLog(Constants.ACTION_URL, "url", url);
                utils.saveString(Constants.TEMP_URL, url);
                showForegroundNotification("Running At: " + url);
            } catch (IOException e) {
                Toast.makeText(this, "Server Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                sendLog(Constants.ACTION_UPDATE_UI_STOP,"","");
                sendLog(Constants.ACTION_MSG,"msg","Try to change ShareX port");
                stopForeground(true);
                stopSelf();
            }
        }else if(intent.getAction().equals(Constants.ACTION_STOP_SERVICE)) {
            sendLog(Constants.ACTION_UPDATE_UI_STOP,"","");
            stopForeground(true);
            stopSelf();
        }
        return Service.START_STICKY;
    }

    @Override
    public void onDestroy() {
        stopForeground(true);
        if(webServer!=null) {
            webServer.closeAllConnections();
            webServer.stop();
        }
        sendLog(Constants.ACTION_MSG,"msg","Server Stopped!");
        utils.clearCache();
        utils.clearTemp();
        utils.clearThumbs();
    }

    public void sendLog(String action,String key,String value) {
        Intent local = new Intent();
        local.setAction("service.to.activity.transfer");
        local.putExtra("action",action);
        local.putExtra(key,value);
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
                    PendingIntent.FLAG_CANCEL_CURRENT);
        }

        // Stop Service Intent
        Intent stopSelf = new Intent(this.getApplicationContext(), ServerService.class);
        stopSelf.setAction(Constants.ACTION_STOP_SERVICE);

        PendingIntent pStopSelf;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            pStopSelf = PendingIntent.getActivity
                    (getApplicationContext(), 0, stopSelf, PendingIntent.FLAG_MUTABLE);
        }else{
            pStopSelf = PendingIntent.getActivity
                    (getApplicationContext(), 0, stopSelf, PendingIntent.FLAG_UPDATE_CURRENT);
        }


        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            String NOTIFICATION_CHANNEL_ID = "com.akansh.fileserversuit";
            String channelName = "ShareX";
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
                    .setSmallIcon(R.drawable.ic_notification)
                    .setContentTitle(cTitle)
                    .setContentText(contentText)
                    .setContentIntent(contentIntent)
                    .setWhen(System.currentTimeMillis())
                    .setPriority(NotificationManager.IMPORTANCE_HIGH)
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
