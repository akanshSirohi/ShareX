package com.akansh.fileserversuit.ui;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import com.akansh.fileserversuit.R;
import com.akansh.fileserversuit.common.Constants;
import com.akansh.fileserversuit.common.EdgeToEdge;
import com.akansh.fileserversuit.common.Utils;
import com.google.android.material.button.MaterialButton;

/** User-initiated permission requests, shared by onboarding and Settings. */
public class PermissionsActivity extends AppCompatActivity {
    public static final String ONBOARDING = "permission_onboarding";
    private boolean onboarding;
    private boolean leaving;
    private final ActivityResultLauncher<Intent> settings = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> refresh());
    private final ActivityResultLauncher<String[]> storage = registerForActivityResult(
            new ActivityResultContracts.RequestMultiplePermissions(), result -> refresh());
    private final ActivityResultLauncher<String> camera = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(), result -> refresh());
    private final ActivityResultLauncher<String> notifications = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(), result -> refresh());

    public static boolean hasStorageAccess(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) return Environment.isExternalStorageManager();
        return ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
                && ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
    }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_permissions);
        EdgeToEdge.apply(this, findViewById(R.id.permissions_root));
        onboarding = getIntent().getBooleanExtra(ONBOARDING, false);
        ((TextView)findViewById(R.id.permissions_title)).setText(onboarding ? R.string.permissions_setup_title : R.string.permissions_title);
        ((TextView)findViewById(R.id.permissions_summary)).setText(onboarding ? R.string.permissions_setup_summary : R.string.permissions_summary);
        findViewById(R.id.permission_camera_card).setVisibility(onboarding ? View.GONE : View.VISIBLE);
        findViewById(R.id.permissions_later).setVisibility(onboarding ? View.VISIBLE : View.GONE);
        findViewById(R.id.permissions_later).setOnClickListener(view -> complete());
        findViewById(R.id.permissions_finish).setOnClickListener(view -> complete());
        findViewById(R.id.permission_storage_action).setOnClickListener(view -> requestStorage());
        findViewById(R.id.permission_background_action).setOnClickListener(view -> {
            if (hasBackgroundAccess()) openAppSettings();
            else launch(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, packageUri()));
        });
        findViewById(R.id.permission_camera_action).setOnClickListener(view -> {
            Utils utils = new Utils(this);
            if (hasCameraAccess() || (utils.loadSetting("asked_camera_permission") && !shouldShowRequestPermissionRationale(Manifest.permission.CAMERA))) openAppSettings();
            else {
                utils.saveSetting("asked_camera_permission", true);
                camera.launch(Manifest.permission.CAMERA);
            }
        });
        findViewById(R.id.permission_notifications_action).setOnClickListener(view -> {
            Utils utils = new Utils(this);
            if (Build.VERSION.SDK_INT >= 33
                    && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                    && (!utils.loadSetting("asked_notifications_permission") || shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS))) {
                utils.saveSetting("asked_notifications_permission", true);
                notifications.launch(Manifest.permission.POST_NOTIFICATIONS);
            } else launch(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName()));
        });
        refresh();
    }

    @Override protected void onResume() { super.onResume(); refresh(); }

    private boolean hasCameraAccess() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
    }

    private boolean hasBackgroundAccess() {
        PowerManager manager = (PowerManager)getSystemService(POWER_SERVICE);
        return manager != null && manager.isIgnoringBatteryOptimizations(getPackageName());
    }

    private void refresh() {
        if (findViewById(R.id.permissions_finish) == null) return;
        update(R.id.permission_storage_status, R.id.permission_storage_action, hasStorageAccess(this), R.string.permissions_storage_action);
        update(R.id.permission_background_status, R.id.permission_background_action, hasBackgroundAccess(), R.string.permissions_background_action);
        update(R.id.permission_camera_status, R.id.permission_camera_action, hasCameraAccess(), R.string.permissions_camera_action);
        android.app.NotificationManager manager = (android.app.NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        android.app.NotificationChannel channel = manager == null ? null : manager.getNotificationChannel(getPackageName());
        boolean notificationAccess = androidx.core.app.NotificationManagerCompat.from(this).areNotificationsEnabled()
                && (channel == null || channel.getImportance() != android.app.NotificationManager.IMPORTANCE_NONE);
        update(R.id.permission_notifications_status, R.id.permission_notifications_action, notificationAccess, R.string.permissions_notifications_action);
        if (notificationAccess && new Utils(this).isServiceRunning(com.akansh.fileserversuit.server.ServerService.class)) {
            startService(new Intent(this, com.akansh.fileserversuit.server.ServerService.class)
                    .setAction(com.akansh.fileserversuit.server.ServerService.ACTION_REFRESH_NOTIFICATION));
        }
        MaterialButton finish = findViewById(R.id.permissions_finish);
        finish.setText(onboarding ? R.string.permissions_continue : R.string.permissions_done);
        finish.setEnabled(!onboarding || hasStorageAccess(this));
    }

    private void update(int statusId, int actionId, boolean allowed, int actionLabel) {
        ((TextView)findViewById(statusId)).setText(allowed ? R.string.permissions_granted : R.string.permissions_missing);
        ((MaterialButton)findViewById(actionId)).setText(allowed ? R.string.permissions_app_settings : actionLabel);
    }

    private void requestStorage() {
        if (hasStorageAccess(this)) { openAppSettings(); return; }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try { settings.launch(new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, packageUri())); }
            catch (RuntimeException error) { launch(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)); }
        } else {
            Utils utils = new Utils(this);
            if (utils.loadSetting("asked_storage_permission")
                    && !shouldShowRequestPermissionRationale(Manifest.permission.READ_EXTERNAL_STORAGE)
                    && !shouldShowRequestPermissionRationale(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                openAppSettings();
            } else {
                utils.saveSetting("asked_storage_permission", true);
                storage.launch(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE});
            }
        }
    }

    private Uri packageUri() { return Uri.parse("package:" + getPackageName()); }
    private void openAppSettings() { launch(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri())); }
    private void launch(Intent intent) {
        try { settings.launch(intent); }
        catch (RuntimeException error) {
            try { settings.launch(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri())); }
            catch (RuntimeException unavailable) { Toast.makeText(this, R.string.permissions_settings_unavailable, Toast.LENGTH_LONG).show(); }
        }
    }

    private void complete() {
        if (leaving) return;
        leaving = true;
        if (onboarding) {
            new Utils(this).saveSetting(Constants.APP_LOAD_F1, true);
            startActivity(new Intent(this, MainActivity.class));
        }
        finish();
    }
}
