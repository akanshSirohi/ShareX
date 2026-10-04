package com.akansh.fileserversuit.ui;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.TransitionDrawable;
import android.net.Uri;
import android.net.wifi.SupplicantState;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.PowerManager;
import android.provider.MediaStore;
import android.provider.Settings;
import android.text.InputType;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.TranslateAnimation;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import com.akansh.fileserversuit.R;
import com.akansh.fileserversuit.common.GenerateQR;
import com.akansh.fileserversuit.common.EdgeToEdge;
import com.akansh.fileserversuit.common.WifiApManager;
import com.akansh.fileserversuit.common.Constants;
import com.akansh.fileserversuit.transfer_history.TransferHistoryActivity;
import com.akansh.fileserversuit.common.Utils;
import com.akansh.fileserversuit.server.DeviceManager;
import com.akansh.fileserversuit.server.ServerService;
import com.akansh.fileserversuit.server.ThemesData;
import com.akansh.fileserversuit.server.WebInterfaceSetup;
import com.akansh.plugins.PluginsManager;
import com.akansh.plugins.ui.PluginsActivity;
import com.bumptech.glide.Glide;
import com.dlazaro66.qrcodereaderview.QRCodeReaderView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.navigation.NavigationView;
import com.google.android.material.navigation.NavigationBarView;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputLayout;
import com.google.android.material.materialswitch.MaterialSwitch;

import java.io.File;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

public class MainActivity extends AppCompatActivity {

    private final String[] PERMISSIONS = {
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
    };

    private ImageButton serverBtn,hide_logger_btn;
    Utils utils;
    String url = "";

    private final Handler activityHandler = new Handler(android.os.Looper.getMainLooper());
    private android.animation.ObjectAnimator statusPulse;
    private Boolean displayedSharingState;
    private long lastSampleTime, lastSent, lastReceived;
    private final Runnable activitySampler = new Runnable() {
        @Override public void run() {
            updateTransferActivity();
            activityHandler.postDelayed(this, 1000);
        }
    };
    boolean isAuthDialogOpened = false;

    private ProgressDialog progress;
    private String serverRoot = null;
    private ConstraintLayout settings_view, main_view, qr_view;
    private View logger_wrapper;
    private TextView settDRoot, settRemDev, settTheme, plugin_folder_label, serverBtnTxt;
    private SessionLogView logger;
    private TextView scan_url, ssl_note;

    private ImageView main_bg,second_bg;
    BroadcastReceiver updateUIReciver;
    List<String> pmode_send_images = new ArrayList<>(), pmode_send_files = new ArrayList<>(), pmode_send_final_files = new ArrayList<>();
    ThemesData themesData = new ThemesData();
    private DrawerLayout drawerLayout;
    private NavigationBarView bottomNavigation;
    Dialog qrDialog;

    private final java.util.concurrent.ExecutorService selectionExecutor = java.util.concurrent.Executors.newSingleThreadExecutor();
    private MaterialSwitch privateModeSwitch;
    DeviceManager deviceManager;
    ActivityResultLauncher<Intent> storagePermissionResultLauncher,
            rootFolderPickerResultLauncher,
            pluginFolderPickerResultLauncher,
            batteryActivityResultLauncher,
            mutipleFilesActivityResultLauncher,
            gallerySelectorActivityResultLauncher;

    int exit = 0;
    int currentTheme, storageChoice = 0;
    boolean requestingStorage = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        boolean openSettings = getIntent().getBooleanExtra("open_settings", false);
        setContentView(R.layout.activity_main);
        EdgeToEdge.apply(this, findViewById(R.id.root_container));
        logger = findViewById(R.id.logger);
        logger_wrapper = findViewById(R.id.logger_wrapper);
        hide_logger_btn = findViewById(R.id.hide_logger_btn);

        utils = new Utils(this);
        serverRoot = utils.loadRoot();
        deviceManager = new DeviceManager(this);
        qrDialog = new Dialog(this);
        serverBtn = findViewById(R.id.serverBtn);
        serverBtnTxt = findViewById(R.id.serverBtnTxt);
        settings_view = findViewById(R.id.settings_view);
        main_view = findViewById(R.id.main_view);
        qr_view = findViewById(R.id.qr_view);
        settings_view.setVisibility(openSettings ? View.VISIBLE : View.GONE);
        main_view.setVisibility(openSettings ? View.GONE : View.VISIBLE);
        qr_view.setVisibility(View.GONE);
        main_bg = findViewById(R.id.main_bg);
        second_bg = findViewById(R.id.second_bg);
        scan_url = findViewById(R.id.scan_url);
        ssl_note = findViewById(R.id.ssl_note);
        drawerLayout = findViewById(R.id.root_container);

        bottomNavigation = findViewById(R.id.bottom_nav);
        if (openSettings) bottomNavigation.setSelectedItemId(R.id.settings);
        bottomNavigation.setOnItemSelectedListener(item -> {
            if (item.getItemId() == R.id.home) {
                main_view.setVisibility(View.VISIBLE);
                settings_view.setVisibility(View.GONE);
                qr_view.setVisibility(View.GONE);
                return true;
            }
            if (item.getItemId() == R.id.settings) {
                main_view.setVisibility(View.GONE);
                settings_view.setVisibility(View.VISIBLE);
                qr_view.setVisibility(View.GONE);
                return true;
            }
            if (item.getItemId() == R.id.trans_hist) {
                startActivity(new Intent(MainActivity.this, TransferHistoryActivity.class));
                return true;
            }
            return false;
        });

        ImageButton nav_btn = findViewById(R.id.nav_btn);
        nav_btn.setOnClickListener(v -> drawerLayout.openDrawer(GravityCompat.START));

        //Settings Components
        settDRoot = findViewById(R.id.sett_subtitle1);
        settRemDev = findViewById(R.id.sett_subtitle6);
        settTheme = findViewById(R.id.sett_subtitle7);
        plugin_folder_label = findViewById(R.id.sett_subtitle11);
        plugin_folder_label.setText(utils.loadPluginDevFolder());
        String dev_count = deviceManager.getRemDevices() + " devices remembered";
        settRemDev.setText(dev_count);
        settTheme.setText(themesData.getDisplayItem(utils.loadInt(Constants.WEB_INTERFACE_THEME,0)));

        currentTheme = utils.loadInt(Constants.WEB_INTERFACE_THEME,0);

        logger.setOnLongClickListener(v -> true);
        clearLog();

        pmode_send_files.addAll(utils.pListReader());
        privateModeSwitch = findViewById(R.id.private_mode_toggle);
        privateModeSwitch.setChecked(utils.loadSetting(Constants.PRIVATE_MODE));
        privateModeSwitch.setOnCheckedChangeListener((button, enabled) -> {
            if (utils.loadSetting(Constants.PRIVATE_MODE) == enabled) return;
            utils.saveSetting(Constants.PRIVATE_MODE, enabled);
            privateMode();
            restartServer();
        });

        storagePermissionResultLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && requestingStorage) {
                if(Environment.isExternalStorageManager()) {
                    requestingStorage = false;
                    initializeApp();
                }else{
                    requestStoragePermissions();
                }
            }else{
                requestingStorage = true;
            }
        });

        rootFolderPickerResultLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if(result.getResultCode() == Activity.RESULT_OK) {
                try {
                    Uri uri = result.getData().getData();
                    String decode = URLDecoder.decode(uri.toString(), "UTF-8");
                    if(decode.split(":")[1].contains("primary")) {
                        utils.saveStorage(Environment.getExternalStorageDirectory().getAbsolutePath());
                    }else{
                        utils.saveStorage(utils.getSDCardRoot());
                    }
                    File f = new File(utils.loadRoot(),decode.split(":")[2]);
                    serverRoot = f.getAbsolutePath();
                    utils.saveRoot(serverRoot);
                    pushLog("Shared folder changed to " + serverRoot, true);
                    updateRootLabel();
                    restartServer();
                }catch (Exception e) {
                    Log.d(Constants.LOG_TAG,"Err2: "+e);
                }
            }
        });

        pluginFolderPickerResultLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), new ActivityResultCallback<ActivityResult>() {
            @Override
            public void onActivityResult(ActivityResult result) {
                if(result.getResultCode() == Activity.RESULT_OK) {
                    try {
                        Uri uri = result.getData().getData();
                        String decode = URLDecoder.decode(uri.toString(), "UTF-8");
                        String root = "";
                        if(decode.split(":")[1].contains("primary")) {
                            root = Environment.getExternalStorageDirectory().getAbsolutePath();
                        }else{
                            root = utils.getSDCardRoot();
                        }
                        File f = new File(root, decode.split(":")[2]);
                        plugin_folder_label.setText(f.getAbsolutePath());
                        utils.savePluginDevFolder(f.getAbsolutePath());
                        restartServer();
                    } catch (Exception e) {}
                }
            }
        });

        mutipleFilesActivityResultLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(), result -> addPickedFiles(result, false));
        gallerySelectorActivityResultLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(), result -> addPickedFiles(result, true));

        batteryActivityResultLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            utils.saveSetting(Constants.ASKED_BATTERY_OPT,true);
        });

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            initRequestPermissions();
        } else {
            initializeApp();
        }

        IntentFilter filter = new IntentFilter();
        filter.addAction(Constants.BROADCAST_SERVICE_TO_ACTIVITY);
        updateUIReciver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, final Intent intent) {
                if (intent != null) {
                    parseBroadcast(intent);
                }
            }
        };
        ContextCompat.registerReceiver(this, updateUIReciver, filter, ContextCompat.RECEIVER_NOT_EXPORTED);
        View qrBtn = findViewById(R.id.qrBtn);
        qrBtn.setOnClickListener(v -> toggleQRView());
        findViewById(R.id.qr_back_btn).setOnClickListener(v -> toggleQRView());
        findViewById(R.id.qr_copy_btn).setOnClickListener(v -> copyConnectionAddress());
        findViewById(R.id.qr_share_btn).setOnClickListener(v -> shareConnectionAddress());
        findViewById(R.id.clear_session_log).setOnClickListener(v -> clearLog());
        hide_logger_btn.setOnClickListener(v -> {
            toggleLogger();
        });

        findViewById(R.id.select_files_btn).setOnClickListener(v -> openFilePicker(false));
        findViewById(R.id.select_media_btn).setOnClickListener(v -> openFilePicker(true));
        findViewById(R.id.manage_files_btn).setOnClickListener(v -> reviewSelectedFiles());
        findViewById(R.id.choose_folder_btn).setOnClickListener(v -> findViewById(R.id.sett_card1).performClick());
        updateSelectedFilesUI();
        logger.setVisibility(utils.loadSetting(Constants.IS_LOGGER_VISIBLE) ? View.VISIBLE : View.GONE);

        ImageButton url_cpy_btn = findViewById(R.id.url_cpy_btn);
        ImageButton url_share_btn = findViewById(R.id.url_share_btn);
        url_cpy_btn.setOnClickListener(view -> {
            copyConnectionAddress();
        });
        url_share_btn.setOnClickListener(view -> {
            shareConnectionAddress();
        });
        privateMode();
        if (savedInstanceState != null) {
            int destination = savedInstanceState.getInt("destination", R.id.home);
            bottomNavigation.setSelectedItemId(destination);
            if (savedInstanceState.getBoolean("show_qr")) toggleQRView();
        }
        centerContent(findViewById(R.id.home_content), 680);
        centerContent(findViewById(R.id.settings_content), 760);
    }

    @Override
    protected void onPause() {
        try {
            if(qrDialog!=null) {
                qrDialog.dismiss();
            }
        }catch (Exception e) {
            // DO Nothing...
        }
        activityHandler.removeCallbacks(activitySampler);
        if (statusPulse != null) { statusPulse.cancel(); statusPulse = null; }
        super.onPause();
    }

    @Override
    protected void onStop() {
        // Clear Glide Cache
        new Thread(() -> Glide.get(MainActivity.this).clearDiskCache()).start();
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        try { unregisterReceiver(updateUIReciver); } catch (IllegalArgumentException ignored) { }
        selectionExecutor.shutdown();
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START);
        } else if(settings_view.getVisibility()==View.VISIBLE) {
            toggleSettings();
        }else if(qr_view.getVisibility()==View.VISIBLE) {
            toggleQRView();
        }else{
            exit();
        }
    }

    // Function For Double Tap Exit
    public void exit() {
        if(exit==0) {
            Toast.makeText(this,"Press One More Time To Exit!",Toast.LENGTH_LONG).show();
            Timer myTimer=new Timer();
            myTimer.schedule(new TimerTask() {
                @Override
                public void run() {
                    exit=0;
                }
            },2000);
            exit++;
        }
        if(exit==1) {
            exit++;
        }else if(exit==2) {
            finishAffinity();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (utils != null && serverBtn != null) {
            boolean running = utils.isServiceRunning(ServerService.class);
            if (running) {
                String savedUrl = utils.loadString(Constants.SERVER_URL);
                if (savedUrl != null) url = savedUrl;
            }
            changeUI(running ? Constants.SERVER_ON : Constants.SERVER_OFF);
            if (running && !url.isEmpty()) ((TextView) findViewById(R.id.connection_address)).setText(url);
        }
        com.akansh.fileserversuit.server.TransferStats.Snapshot activity = com.akansh.fileserversuit.server.TransferStats.snapshot();
        lastSent = activity.sent; lastReceived = activity.received;
        lastSampleTime = android.os.SystemClock.elapsedRealtime();
        activityHandler.removeCallbacks(activitySampler);
        activityHandler.post(activitySampler);
        if (settings_view != null && qr_view != null && qr_view.getVisibility() == View.GONE) {
            NavigationBarView bottomNavigation = findViewById(R.id.bottom_nav);
            bottomNavigation.setSelectedItemId(settings_view.getVisibility() == View.VISIBLE
                    ? R.id.settings : R.id.home);
        }

        privateMode();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        boolean openSettings = intent.getBooleanExtra("open_settings", false);
        settings_view.setVisibility(openSettings ? View.VISIBLE : View.GONE);
        main_view.setVisibility(openSettings ? View.GONE : View.VISIBLE);
        qr_view.setVisibility(View.GONE);
        ((NavigationBarView) findViewById(R.id.bottom_nav))
                .setSelectedItemId(openSettings ? R.id.settings : R.id.home);
    }

    @Override
    protected void onStart() {
        super.onStart();
        privateMode();
    }

    @SuppressLint("SdCardPath")
    public void initializeApp() {
        Log.d(Constants.LOG_TAG,"App Initialized!");
        if(utils.isServiceRunning(ServerService.class)) {
            changeUI(Constants.SERVER_ON);
            String u=utils.loadString(Constants.SERVER_URL);
            if(u!=null) {
                url=u;
                TextView address = findViewById(R.id.connection_address);
                if (address != null) address.setText(url);
                if (scan_url != null) scan_url.setText(url);
                pushLog("Server running at: "+url,false);
            }
        }
        View.OnClickListener sharingListener = view -> {
            askIgnoreBatteryOptimizations();
            if (!utils.isServiceRunning(ServerService.class)) {
                if(isValidIP()) {
                    pushLog("Starting server...", true);
                    startServer();
                    changeUI(Constants.SERVER_ON);
                }else{
                    showSnackbar("Make sure your hotspot is open or wifi connected...");
                }
            } else {
                pushLog("Stopping server...",true);
                stopServer();
                changeUI(Constants.SERVER_OFF);
            }
        };
        serverBtn.setOnClickListener(sharingListener);
        serverBtnTxt.setOnClickListener(sharingListener);

        // Copy WebApp from app assets to Internal Storage
        File f=new File(String.format("/data/data/%s/%s/index.html",getPackageName(),Constants.NEW_DIR));
        if(!f.exists() || Constants.DEBUG) {
            WebInterfaceSetup webInterfaceSetup=new WebInterfaceSetup(getPackageName(), this, this, utils);
            webInterfaceSetup.setupListeners=new WebInterfaceSetup.SetupListeners() {
                @Override
                public void onSetupCompeted(boolean status) {
                    progress.cancel();
                    if(!status) {
                        AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
                        builder.setTitle(utils.getSpannableFont("Error"));
                        builder.setMessage(utils.getSpannableFont("Something went wrong!"));
                        builder.setPositiveButton("OK", (dialog, id) -> {
                            dialog.dismiss();
                            finishAffinity();
                        });
                        AlertDialog alert = builder.create();
                        alert.setCancelable(false);
                        alert.setCanceledOnTouchOutside(false);
                        alert.show();
                    }

                    if(!Constants.DEBUG) {
                        showAbout();
                    }
                }

                @Override
                public void onSetupStarted(boolean updating) {
                    progress=new ProgressDialog( MainActivity.this);
                    try {
                        progress.setTitle(utils.getSpannableFont(getResources().getString(R.string.app_name)));
                        if(updating) {
                            progress.setMessage(utils.getSpannableFont("Updating App Content\nPlease Wait..."));
                        }else{
                            progress.setMessage(utils.getSpannableFont("Preparing App For First Use\nPlease Wait..."));
                        }
                        progress.setProgressStyle(ProgressDialog.STYLE_SPINNER);
                        progress.setIndeterminate(true);
                        progress.setProgress(0);
                        progress.setCancelable(false);
                        progress.setCanceledOnTouchOutside(false);
                        progress.show();
                    }catch (Exception e) {
                        Log.d(Constants.LOG_TAG,e.toString());
                    }
                }
            };
            webInterfaceSetup.setup();
        }

        // Setup Side Bar Drawer Navigation
        NavigationView navigationView=findViewById(R.id.navigationView);
        boolean advancedMode = utils.loadSetting(Constants.ADVANCED_MODE);
        navigationView.getMenu().findItem(R.id.clear_log).setVisible(advancedMode);
        navigationView.setNavigationItemSelectedListener(item -> {
            int itemId = item.getItemId();

            if (itemId == R.id.home) {
                settings_view.setVisibility(View.GONE);
                qr_view.setVisibility(View.GONE);
                main_view.setVisibility(View.VISIBLE);
                bottomNavigation.setSelectedItemId(R.id.home);
            } else if (itemId == R.id.plugins) {
                Intent pluginsIntent = new Intent(MainActivity.this, PluginsActivity.class);
                startActivity(pluginsIntent);
            } else if (itemId == R.id.settings) {
                settings_view.setVisibility(View.VISIBLE);
                main_view.setVisibility(View.GONE);
                qr_view.setVisibility(View.GONE);
                bottomNavigation.setSelectedItemId(R.id.settings);
            } else if (itemId == R.id.scan_qr) {
                if (checkCameraPermission()) initQrScanner(); else requestCameraPermission();
            } else if (itemId == R.id.trans_hist) {
                Intent transferHistoryIntent = new Intent(MainActivity.this, TransferHistoryActivity.class);
                startActivity(transferHistoryIntent);
            } else if (itemId == R.id.clear_log) {
                clearLog();
            } else if (itemId == R.id.privacy_policy) {
                try {
                    Intent intent = new Intent(Intent.ACTION_VIEW);
                    intent.setData(Uri.parse(Constants.PRIVACY_POLICY_URL));
                    startActivity(intent);
                } catch (Exception ignored) { }
            } else if (itemId == R.id.feedback) {
                Intent email = new Intent(Intent.ACTION_SENDTO, Uri.fromParts("mailto", Constants.FEEDBACK_MAIL, null));
                email.putExtra(Intent.EXTRA_SUBJECT, "ShareX Feedback");
                email.putExtra(Intent.EXTRA_TEXT, "Any feedback, query or suggestion...");
                startActivity(Intent.createChooser(email, "Send Feedback"));
            } else if (itemId == R.id.about) {
                showAbout();
            }
            drawerLayout.closeDrawer(GravityCompat.START);
            return true;
        });
        setUp_settingsListener();
        MaterialSwitch advancedModeSwitch = findViewById(R.id.advanced_mode_switch);
        advancedModeSwitch.setChecked(advancedMode);
        advancedModeSwitch.setOnCheckedChangeListener((buttonView, enabled) -> {
            utils.saveSetting(Constants.ADVANCED_MODE, enabled);
            applyAdvancedMode(enabled);
            navigationView.getMenu().findItem(R.id.clear_log).setVisible(enabled);
        });
        applyAdvancedMode(advancedMode);

        PluginsManager pluginsManager = new PluginsManager(this, this, utils);
        pluginsManager.fetchPluginAppsFile();
    }

    public void askIgnoreBatteryOptimizations() {
        if(!utils.loadSetting(Constants.ASKED_BATTERY_OPT)) {
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (pm != null && !pm.isIgnoringBatteryOptimizations(getPackageName())) {
                    Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                    intent.setData(Uri.parse("package:" + getPackageName()));
                    batteryActivityResultLauncher.launch(intent);
                }
            }
        }
    }

    public boolean checkStoragePermissions() {
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return ActivityCompat.checkSelfPermission(this,PERMISSIONS[0]) == PackageManager.PERMISSION_GRANTED &&
                    ActivityCompat.checkSelfPermission(this,PERMISSIONS[1]) == PackageManager.PERMISSION_GRANTED;
        }
        return true;
    }

    public boolean checkCameraPermission() {
        return ActivityCompat.checkSelfPermission(this,Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
    }

    public void requestCameraPermission() {
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            requestPermissions(new String[]{Manifest.permission.CAMERA},Constants.CAMERA_REQ_CODE);
        }
    }

    public void requestStoragePermissions() {
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                if (!Environment.isExternalStorageManager()) {
                    Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                    intent.addCategory("android.intent.category.DEFAULT");
                    intent.setData(Uri.parse("package:"+getPackageName()));
                    storagePermissionResultLauncher.launch(intent);
                    requestingStorage = true;
                }
            } catch (Exception e) {
                if (!Environment.isExternalStorageManager()) {
                    Intent intent = new Intent();
                    intent.setAction(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                    storagePermissionResultLauncher.launch(intent);
                }
            }
        }
    }

    public void initRequestPermissions() {
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            requestPermissions(PERMISSIONS,Constants.STORAGE_REQ_CODE);
        }else if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.R){
            if (!Environment.isExternalStorageManager()) {
                requestStoragePermissions();
            } else {
                initializeApp();
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if(requestCode == Constants.STORAGE_REQ_CODE) {
            if (!checkStoragePermissions()) {
                initRequestPermissions();
                return;
            }
            initializeApp();
        }else if(requestCode == Constants.CAMERA_REQ_CODE) {
            if(checkCameraPermission()) {
                initQrScanner();
            }
        }else{
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }

    public void parseBroadcast(final Intent intent) {
        String action=intent.getStringExtra("action");
        if (action == null) return;
        if(action.equals(Constants.ACTION_URL)) {
            runOnUiThread(() -> {
                url=intent.getStringExtra("url");
                TextView address = findViewById(R.id.connection_address);
                if (address != null) address.setText(url);
                if (scan_url != null) scan_url.setText(url);
                changeUI(Constants.SERVER_ON);
                pushLog("Server started at: "+url,true);
                if(!utils.loadSetting(Constants.IS_LOGGER_VISIBLE)) {
                    showSnackbar("Server started at: "+url);
                }
            });
        }else if(action.equals(Constants.ACTION_MSG)) {
            runOnUiThread(() -> pushLog(intent.getStringExtra("msg"),true));
        }else if(action.equals(Constants.ACTION_PROGRESS)){
            updateTransferActivity();
        }else if(action.equals(Constants.ACTION_AUTH)) {
            showAuthDialog(intent.getStringExtra("device_id"));
        }else if(action.equals(Constants.ACTION_UPDATE_UI_STOP)) {
            runOnUiThread(()->{
                changeUI(Constants.SERVER_OFF);
            });
        }
    }

    private void toggleLogger() {
        boolean visible = logger.getVisibility() != View.VISIBLE;
        logger.setVisibility(visible ? View.VISIBLE : View.GONE);
        hide_logger_btn.setImageResource(visible ? R.drawable.ic_caret_up : R.drawable.ic_caret_down);
        utils.saveSetting(Constants.IS_LOGGER_VISIBLE, visible);
    }

    private void setUp_settingsListener() {
        CardView card1 = findViewById(R.id.sett_card1);
        CardView card2 = findViewById(R.id.sett_card2);
        CardView card3 = findViewById(R.id.sett_card3);
        CardView card4 = findViewById(R.id.sett_card4);
        CardView card6 = findViewById(R.id.sett_card6);
        CardView card7 = findViewById(R.id.sett_card7);
        CardView card8 = findViewById(R.id.sett_card8);
        CardView card9 = findViewById(R.id.sett_card9);
        CardView card10 = findViewById(R.id.sett_card10);
        CardView card11 = findViewById(R.id.sett_card11);
        CompoundButton settHFCheck = findViewById(R.id.sett_hideF_checkBox);
        CompoundButton settRMCheck = findViewById(R.id.sett_resMod_checkBox);
        CompoundButton settFDCheck = findViewById(R.id.sett_frceDwl_checkBox);
        CompoundButton settAppsCheck = findViewById(R.id.sett_apps_checkBox);
        CompoundButton settSslCheck = findViewById(R.id.sett_ssl_checkBox);
        CompoundButton settPluginDevCheck = findViewById(R.id.sett_plugin_debug_checkBox);
        TextView settPort = findViewById(R.id.sett_subtitle8);
        ImageButton sett_plugin_folder = findViewById(R.id.sett_plugin_folder);
        ImageButton settResetRoot = findViewById(R.id.sett_reset_root);
        ImageButton sett_plugin_debug_link = findViewById(R.id.sett_plugin_debug_link);


        // App Root Settings
        settResetRoot.setOnClickListener(view -> {
            if(utils.isExternalStorageMounted()) {
                String[] options = {"Internal Storage","SD Card"};
                String[] storages = {Environment.getExternalStorageDirectory().getAbsolutePath(),utils.getSDCardRoot()};
                AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
                builder.setTitle("Choose Default Storage");
                builder.setSingleChoiceItems(options, storageChoice, (dialog, which) -> {
                    storageChoice = which;
                });
                builder.setPositiveButton("Set", (dialog, which) -> {
                    dialog.dismiss();
                    utils.saveStorage(storages[storageChoice]);
                    serverRoot = storages[storageChoice];
                    utils.saveRoot(serverRoot);
                    pushLog("Shared folder changed to " + serverRoot, true);
                    updateRootLabel();
                    restartServer();
                });
                builder.setNegativeButton("Cancel", (dialog, which) -> {
                    dialog.dismiss();
                });
                builder.show();
            }else{
                utils.saveStorage(Environment.getExternalStorageDirectory().getAbsolutePath());
                serverRoot = Environment.getExternalStorageDirectory().getAbsolutePath();
                utils.saveRoot(serverRoot);
                pushLog("Shared folder changed to " + serverRoot, true);
                updateRootLabel();
                restartServer();
            }
        });
        card1.setOnClickListener(view -> {
            try {
                Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
                i.addCategory(Intent.CATEGORY_DEFAULT);
                Intent fIntent = Intent.createChooser(i, "Choose folder to share");
                rootFolderPickerResultLauncher.launch(fIntent);
            }catch (Exception e) {
                Log.d(Constants.LOG_TAG,e.toString());
            }
        });
        updateRootLabel();

        // Show hidden files check
        settHFCheck.setChecked(utils.loadSetting(Constants.LOAD_HIDDEN_MEDIA));
        settHFCheck.setOnCheckedChangeListener((compoundButton, b) -> {
            utils.saveSetting(Constants.LOAD_HIDDEN_MEDIA,b);
            restartServer();
        });
        card2.setOnClickListener(v -> settHFCheck.setChecked(!settHFCheck.isChecked()));

        // Restrict Modification Settings
        settRMCheck.setChecked(utils.loadSetting(Constants.RESTRICT_MODIFY));
        settRMCheck.setOnCheckedChangeListener((compoundButton, b) -> {
            utils.saveSetting(Constants.RESTRICT_MODIFY,b);
            restartServer();
        });
        card3.setOnClickListener(v -> settRMCheck.setChecked(!settRMCheck.isChecked()));

        // ForceD Download Settings
        settFDCheck.setChecked(utils.loadSetting(Constants.FORCE_DOWNLOAD));
        settFDCheck.setOnCheckedChangeListener((compoundButton, b) -> utils.saveSetting(Constants.FORCE_DOWNLOAD,b));
        card4.setOnClickListener(v -> settFDCheck.setChecked(!settFDCheck.isChecked()));

        // Clear Remember Device Settings
        card6.setOnClickListener(v -> {
            deviceManager.clearAll();
            showSnackbar("All remembered devices cleared!");
            Timer myTimer=new Timer();
            myTimer.schedule(new TimerTask() {
                @Override
                public void run() {
                    MainActivity.this.runOnUiThread(() -> settRemDev.setText(deviceManager.getRemDevices()+" devices remembered"));
                }
            },500);
        });

        // Theme Chooser Setting
        card7.setOnClickListener(v -> {
            AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
            builder.setTitle("Choose Theme");
            builder.setSingleChoiceItems(themesData.getDisplayList(), currentTheme, (dialog, which) -> {
                currentTheme = which;
            });
            builder.setPositiveButton("Set", (dialog, which) -> {
                utils.saveInt(Constants.WEB_INTERFACE_THEME, currentTheme);
                settTheme.setText(themesData.getDisplayItem(currentTheme));
                dialog.dismiss();
            });
            builder.setNegativeButton("Cancel", (dialog, which) -> {
                currentTheme = utils.loadInt(Constants.WEB_INTERFACE_THEME,0);
                dialog.dismiss();
            });
            builder.show();
        });

        // Sharex Port Settings
        settPort.setText("Port: "+utils.loadInt(Constants.SERVER_PORT,Constants.SERVER_PORT_DEFAULT));
        card8.setOnClickListener(v-> {
            AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
            builder.setTitle("ShareX Port:");
            builder.setMessage("Please enter a port number between 1024 and 65535");
            TextInputLayout textInputLayout = new TextInputLayout(MainActivity.this);
            textInputLayout.setPadding(getResources().getDimensionPixelOffset(R.dimen.dp_19),0,getResources().getDimensionPixelOffset(R.dimen.dp_19),0);
            final EditText input = new EditText(MainActivity.this);
            input.setText(String.valueOf(utils.loadInt(Constants.SERVER_PORT,Constants.SERVER_PORT_DEFAULT)));
            input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_NORMAL);
            textInputLayout.addView(input);
            builder.setView(textInputLayout);
            builder.setPositiveButton("Set", (dialog, which) -> {
                if(input.getText().toString().length()>0) {
                    int port = Integer.parseInt(input.getText().toString());
                    if(port >= 1024 && port <= 65535) {
                        utils.saveInt(Constants.SERVER_PORT, port);
                        settPort.setText("Port: " + port);
                        Toast.makeText(MainActivity.this, "ShareX port changed to " + port, Toast.LENGTH_LONG).show();
                        dialog.dismiss();
                        restartServer();
                    }else{
                        Toast.makeText(MainActivity.this, "Please enter a valid port number", Toast.LENGTH_LONG).show();
                    }
                }
            });
            builder.setNegativeButton("Cancel", (dialog, which) -> {
                dialog.dismiss();
            });
            builder.show();
        });

        // Load Apps Settings
        card9.setOnClickListener(v -> settAppsCheck.setChecked(!settAppsCheck.isChecked()));
        settAppsCheck.setChecked(utils.loadSetting(Constants.LOAD_APPS));
        settAppsCheck.setOnCheckedChangeListener((compoundButton, b) -> {
            utils.saveSetting(Constants.LOAD_APPS,b);
            restartServer();
        });

        // SSL Settings
        card10.setOnClickListener(v -> settSslCheck.setChecked(!settSslCheck.isChecked()));
        settSslCheck.setChecked(utils.loadSetting(Constants.SSL));
        settSslCheck.setOnCheckedChangeListener((compoundButton, b) -> {
            utils.saveSetting(Constants.SSL,b);
            restartServer();
        });

        // Plugin Settings
        card11.setOnClickListener(v -> {
            settPluginDevCheck.setChecked(!settPluginDevCheck.isChecked());
            if(settPluginDevCheck.isChecked()) {
                utils.createDefualtPluginDevDir();
            }
        });
        settPluginDevCheck.setChecked(utils.loadSetting(Constants.PLUGIN_DEV));
        settPluginDevCheck.setOnCheckedChangeListener((buttonView, isChecked) -> {
            utils.saveSetting(Constants.PLUGIN_DEV, isChecked);
            if(isChecked) {
                utils.createDefualtPluginDevDir();
                restartServer();
            }
        });

        sett_plugin_folder.setOnClickListener(v -> {
            try {
                Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
                i.addCategory(Intent.CATEGORY_DEFAULT);
                Intent fIntent = Intent.createChooser(i, "Choose plugin debug folder");
                pluginFolderPickerResultLauncher.launch(fIntent);
            }catch (Exception e) {
                Log.d(Constants.LOG_TAG,e.toString());
            }
        });

        sett_plugin_debug_link.setOnClickListener(v -> {
            try {
                if(utils.isServiceRunning(ServerService.class)) {
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url + "/SharexApp/debug/"));
                    if (intent.resolveActivity(getPackageManager()) != null) {
                        startActivity(intent);
                    } else {
                        Toast.makeText(MainActivity.this, "No browser available to open the url!", Toast.LENGTH_LONG).show();
                    }
                }else{
                    Toast.makeText(MainActivity.this, "Start ShareX first!", Toast.LENGTH_LONG).show();
                }
            }catch (Exception e) {
                Log.d(Constants.LOG_TAG,e.toString());
                Toast.makeText(MainActivity.this, "Something went wrong!", Toast.LENGTH_LONG).show();
            }
        });


    }

    private void applyAdvancedMode(boolean enabled) {
        int[] advancedCards = {R.id.sett_card2, R.id.sett_card3, R.id.sett_card4,
                R.id.sett_card6, R.id.sett_card8, R.id.sett_card9, R.id.sett_card10,
                R.id.sett_card11};
        for (int cardId : advancedCards) {
            View card = findViewById(cardId);
            if (card != null) card.setVisibility(enabled ? View.VISIBLE : View.GONE);
        }
        int[] advancedHeadings = {R.id.advanced_details_heading, R.id.settings_network_heading,
                R.id.settings_security_heading, R.id.settings_system_heading,
                R.id.settings_advanced_security_heading, R.id.settings_diagnostics_heading};
        for (int headingId : advancedHeadings) {
            View heading = findViewById(headingId);
            if (heading != null) heading.setVisibility(enabled ? View.VISIBLE : View.GONE);
        }
        if (logger_wrapper != null) logger_wrapper.setVisibility(enabled ? View.VISIBLE : View.GONE);
        updateGraphVisibility();
        updateRootLabel();
    }

    private void updateGraphVisibility() {
        View section = findViewById(R.id.transfer_activity_section);
        if (section != null) section.setVisibility(utils.loadSetting(Constants.ADVANCED_MODE)
                && Boolean.TRUE.equals(displayedSharingState) ? View.VISIBLE : View.GONE);
    }

    private void updateRootLabel() {
        if (settDRoot == null || utils == null) return;
        if (utils.loadSetting(Constants.ADVANCED_MODE)) {
            settDRoot.setText(getString(R.string.settings_root_advanced_summary, serverRoot));
        } else {
            settDRoot.setText(R.string.sett_sub_title_1);
        }
    }

    private void toggleSettings() {
        qr_view.setVisibility(View.GONE);
        if(settings_view.getVisibility() == View.GONE) {
            settings_view.setVisibility(View.VISIBLE);
            main_view.setVisibility(View.GONE);
        }else{
            settings_view.setVisibility(View.GONE);
            main_view.setVisibility(View.VISIBLE);
        }
        bottomNavigation.setSelectedItemId(settings_view.getVisibility() == View.VISIBLE
                ? R.id.settings : R.id.home);
    }

    private void copyConnectionAddress() {
        if (!url.isEmpty()) {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            clipboard.setPrimaryClip(ClipData.newPlainText("ShareX address", url));
            showSnackbar("Sharing address copied");
        } else {
            showSnackbar("Start sharing to get an address");
        }
    }

    private void shareConnectionAddress() {
        if (!url.isEmpty()) {
            Intent sharingIntent = new Intent(Intent.ACTION_SEND);
            sharingIntent.setType("text/plain");
            sharingIntent.putExtra(Intent.EXTRA_SUBJECT, "ShareX connection");
            sharingIntent.putExtra(Intent.EXTRA_TEXT, url);
            startActivity(Intent.createChooser(sharingIntent, "Share connection address"));
        } else {
            showSnackbar("Start sharing to get an address");
        }
    }

    public void restartServer() {
        if(utils.isServiceRunning(ServerService.class)) {
            changeUI(Constants.SERVER_OFF);
            stopServer();
            pushLog("Restarting server...", true);
            new Handler().postDelayed(() -> {
                if(isValidIP()) {
                    startServer();
                    changeUI(Constants.SERVER_ON);
                }else{
                    showSnackbar("Make sure your hotspot is open or wifi connected...");
                }
            }, 2000);
        }
    }

    public void stopServer() {
        if(utils.isServiceRunning(ServerService.class)) {
            Intent intent = new Intent(MainActivity.this, ServerService.class);
            stopService(intent);
        }
        url="";
        TextView address = findViewById(R.id.connection_address);
        if (address != null) address.setText(R.string.sharing_address_stopped);
        deviceManager.clearTmp();

    }

    public void pushLog(String log, boolean scroll) {
        if (logger != null) logger.addEvent(log);
    }

    private void clearLog() {
        logger.clearEvents();
    }

    private boolean isValidIP() {
        WifiApManager wifiApManager=new WifiApManager(getApplicationContext());
        return wifiApManager.isWifiApEnabled() || checkWifiOnAndConnected();
    }

    private boolean checkWifiOnAndConnected() {
        WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        WifiInfo wifiInfo = wifiManager.getConnectionInfo();
        if(wifiInfo!=null) {
            SupplicantState supState = wifiInfo.getSupplicantState();
            return supState.toString().equals("COMPLETED");
        }else{
            return false;
        }
    }

    public void startServer() {
        if(!utils.isServiceRunning(ServerService.class)) {
            Intent intent = new Intent(MainActivity.this, ServerService.class);
            startService(intent);
        }
    }

    public void toggleQRView() {
        if(qr_view.getVisibility()==View.GONE) {
            main_view.setVisibility(View.GONE);
            qr_view.setVisibility(View.VISIBLE);
            findViewById(R.id.bottom_nav).setVisibility(View.GONE);
            if(url.length()>0) {
                TextView scan_url=findViewById(R.id.scan_url);
                scan_url.setText(url);
                TextView address=findViewById(R.id.connection_address);
                if(address!=null) address.setText(url);
                ImageView qr_view=findViewById(R.id.qr_img);
                GenerateQR generateQR=new GenerateQR(qr_view, this, this);
                generateQR.execute(url);
            }
        }else{
            main_view.setVisibility(View.VISIBLE);
            qr_view.setVisibility(View.GONE);
            findViewById(R.id.bottom_nav).setVisibility(View.VISIBLE);
            ImageView qr_view=findViewById(R.id.qr_img);
            qr_view.setImageResource(R.drawable.ic_logo);
            scan_url.setText(R.string.qr_stopped);
            TextView address=findViewById(R.id.connection_address);
            if(address!=null) address.setText(R.string.address_ready_hint);
        }
        ssl_note.setVisibility(utils.loadSetting(Constants.SSL) ? View.VISIBLE : View.GONE);
    }

    public void showAbout() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        final View customLayout = getLayoutInflater().inflate(R.layout.about_view, null);
        builder.setView(customLayout);
        AlertDialog dialog = builder.create();
        dialog.show();
    }

    private void updateTransferActivity() {
        com.akansh.fileserversuit.server.TransferStats.Snapshot snapshot = com.akansh.fileserversuit.server.TransferStats.snapshot();
        long now = android.os.SystemClock.elapsedRealtime();
        long interval = Math.max(1, now - lastSampleTime);
        double sending = Math.max(0, snapshot.sent - lastSent) * 1000d / interval;
        double receiving = Math.max(0, snapshot.received - lastReceived) * 1000d / interval;
        // Broadcasts can arrive many times per second; graph samples remain one second apart.
        if (interval >= 900) {
            ((TransferGraphView) findViewById(R.id.transfer_graph)).addSample(sending, receiving);
            lastSampleTime = now; lastSent = snapshot.sent; lastReceived = snapshot.received;
            ((TextView) findViewById(R.id.send_speed)).setText(formatBytes((long) sending) + "/s");
            ((TextView) findViewById(R.id.receive_speed)).setText(formatBytes((long) receiving) + "/s");
            findViewById(R.id.transfer_graph).setContentDescription("Sending " + formatBytes((long) sending)
                    + " per second, receiving " + formatBytes((long) receiving) + " per second");
        }
        ((TextView) findViewById(R.id.session_totals)).setText(formatBytes(snapshot.sent) + " sent \u00b7 " + formatBytes(snapshot.received) + " received");
        ((TextView) findViewById(R.id.activity_state)).setText(snapshot.activeCount > 0
                ? snapshot.activeCount + (snapshot.activeCount == 1 ? " active transfer" : " active transfers") : "Waiting for a transfer");
        View transfer = findViewById(R.id.active_transfer_card);
        ProgressBar progress = findViewById(R.id.progressBar);
        TextView description = findViewById(R.id.transfer_progress_label);
        if (snapshot.activeCount > 0) {
            transfer.setVisibility(View.VISIBLE);
            progress.setIndeterminate(snapshot.unknownTotal || snapshot.total <= 0);
            if (!progress.isIndeterminate()) progress.setProgress((int) Math.min(100, snapshot.bytes * 100 / snapshot.total));
            String detail = snapshot.unknownTotal ? formatBytes(snapshot.bytes)
                    : formatBytes(snapshot.bytes) + " / " + formatBytes(snapshot.total);
            description.setText(snapshot.name + " \u00b7 " + detail);
        } else if (snapshot.completedAt > 0 && now - snapshot.completedAt < 1500) {
            transfer.setVisibility(View.VISIBLE);
            progress.setIndeterminate(false); progress.setProgress(100);
            description.setText("Transfer complete");
        } else transfer.setVisibility(View.GONE);
    }

    private String formatBytes(long bytes) {
        if (bytes >= 1024 * 1024) return String.format(java.util.Locale.getDefault(), "%.1f MiB", bytes / (1024d * 1024));
        if (bytes >= 1024) return String.format(java.util.Locale.getDefault(), "%.1f KiB", bytes / 1024d);
        return bytes + " B";
    }

    private void privateMode() {
        if (utils == null) return;
        boolean enabled = utils.loadSetting(Constants.PRIVATE_MODE);
        if (privateModeSwitch != null && privateModeSwitch.isChecked() != enabled) privateModeSwitch.setChecked(enabled);
        ((TextView) findViewById(R.id.mode_description)).setText(enabled
                ? R.string.private_mode_explanation : R.string.folder_mode_explanation);
        findViewById(R.id.file_selection_panel).setVisibility(enabled ? View.VISIBLE : View.GONE);
        findViewById(R.id.choose_folder_btn).setVisibility(enabled ? View.GONE : View.VISIBLE);
        findViewById(R.id.top_panel).setVisibility(View.GONE);
        updateSelectedFilesUI();
    }

    private void mergeAndUpdatePFilesList() {
        java.util.LinkedHashSet<String> paths = new java.util.LinkedHashSet<>(pmode_send_files);
        paths.addAll(pmode_send_images);
        pmode_send_final_files = new ArrayList<>(paths);
        utils.pListWriter(pmode_send_final_files);
        updateSelectedFilesUI();
    }

    private void updateSelectedFilesUI() {
        java.util.LinkedHashSet<String> paths = new java.util.LinkedHashSet<>(pmode_send_files);
        paths.addAll(pmode_send_images);
        TextView summary = findViewById(R.id.selected_files_summary);
        summary.setText(paths.isEmpty() ? getString(R.string.no_files_selected)
                : getResources().getQuantityString(R.plurals.files_selected, paths.size(), paths.size()));
        findViewById(R.id.manage_files_btn).setVisibility(paths.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private void openFilePicker(boolean media) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        if (media) intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"image/*", "video/*"});
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        try {
            (media ? gallerySelectorActivityResultLauncher : mutipleFilesActivityResultLauncher).launch(intent);
        } catch (android.content.ActivityNotFoundException e) {
            showSnackbar("No file picker available on this device");
        }
    }

    private void addPickedFiles(ActivityResult result, boolean media) {
        if (result.getResultCode() != Activity.RESULT_OK || result.getData() == null) return;
        Intent data = result.getData();
        List<Uri> uris = new ArrayList<>();
        if (data.getClipData() != null) {
            for (int i = 0; i < data.getClipData().getItemCount(); i++) uris.add(data.getClipData().getItemAt(i).getUri());
        } else if (data.getData() != null) uris.add(data.getData());
        findViewById(R.id.select_files_btn).setEnabled(false);
        findViewById(R.id.select_media_btn).setEnabled(false);
        ((TextView) findViewById(R.id.selected_files_summary)).setText("Preparing selected files...");
        selectionExecutor.execute(() -> {
            List<String> paths = new ArrayList<>();
            for (Uri uri : uris) {
                try { getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION); }
                catch (SecurityException ignored) { }
                String path = utils.filePickerUriResolve(uri);
                if (path != null && !paths.contains(path)) paths.add(path);
            }
            runOnUiThread(() -> {
                List<String> selected = media ? pmode_send_images : pmode_send_files;
                for (String path : paths) if (!selected.contains(path)) selected.add(path);
                mergeAndUpdatePFilesList();
                findViewById(R.id.select_files_btn).setEnabled(true);
                findViewById(R.id.select_media_btn).setEnabled(true);
                if (paths.size() < uris.size()) showSnackbar("Some files could not be read. Choose them again from device storage.");
                else showSnackbar(getResources().getQuantityString(R.plurals.files_ready, paths.size(), paths.size()));
            });
        });
    }

    private void reviewSelectedFiles() {
        java.util.LinkedHashSet<String> paths = new java.util.LinkedHashSet<>(pmode_send_files);
        paths.addAll(pmode_send_images);
        BottomSheetDialog sheet = new BottomSheetDialog(this);
        android.widget.LinearLayout content = new android.widget.LinearLayout(this);
        content.setOrientation(android.widget.LinearLayout.VERTICAL);
        int padding = Math.round(24 * getResources().getDisplayMetrics().density);
        content.setPadding(padding, padding, padding, padding);
        TextView heading = new TextView(this);
        heading.setText("Selected files"); heading.setTextSize(22);
        heading.setTextColor(getColor(R.color.txt_color)); content.addView(heading);
        android.widget.ScrollView scroll = new android.widget.ScrollView(this);
        android.widget.LinearLayout list = new android.widget.LinearLayout(this);
        list.setOrientation(android.widget.LinearLayout.VERTICAL);
        for (String path : paths) {
            android.widget.LinearLayout row = new android.widget.LinearLayout(this);
            row.setGravity(android.view.Gravity.CENTER_VERTICAL);
            TextView name = new TextView(this);
            name.setText(new File(path).getName()); name.setTextSize(16);
            name.setTextColor(getColor(R.color.txt_color));
            row.addView(name, new android.widget.LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            com.google.android.material.button.MaterialButton remove = new com.google.android.material.button.MaterialButton(this, null, com.google.android.material.R.attr.borderlessButtonStyle);
            remove.setText("Remove"); remove.setAllCaps(false);
            remove.setOnClickListener(v -> {
                pmode_send_files.remove(path); pmode_send_images.remove(path);
                mergeAndUpdatePFilesList(); list.removeView(row);
                if (list.getChildCount() == 0) sheet.dismiss();
            });
            row.addView(remove); list.addView(row);
        }
        scroll.addView(list);
        content.addView(scroll, new android.widget.LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                Math.min(Math.round(360 * getResources().getDisplayMetrics().density), getResources().getDisplayMetrics().heightPixels / 2)));
        sheet.setContentView(content); sheet.show();
    }

    private void centerContent(View content, int maxWidthDp) {
        View parent = (View) content.getParent();
        Runnable center = () -> {
            if (parent.getWidth() == 0) return;
            float density = getResources().getDisplayMetrics().density;
            android.widget.FrameLayout.LayoutParams params = (android.widget.FrameLayout.LayoutParams) content.getLayoutParams();
            int width = Math.min(parent.getWidth(), Math.round(maxWidthDp * density));
            if (params.width != width) {
                params.width = width;
                params.gravity = android.view.Gravity.CENTER_HORIZONTAL;
                content.setLayoutParams(params);
            }
        };
        parent.addOnLayoutChangeListener((v, l, t, r, b, oldL, oldT, oldR, oldB) -> center.run());
        parent.post(center);
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle state) {
        super.onSaveInstanceState(state);
        state.putInt("destination", settings_view.getVisibility() == View.VISIBLE ? R.id.settings : R.id.home);
        state.putBoolean("show_qr", qr_view.getVisibility() == View.VISIBLE);
    }

    private void changeUI(int code) {
        boolean sharing = code == Constants.SERVER_ON;
        serverBtn.setSelected(sharing);
        ((SharingStatusView) findViewById(R.id.sharing_status_dial)).setRunning(sharing);
        if (displayedSharingState == null || displayedSharingState != sharing) {
            if (sharing) ((TransferGraphView) findViewById(R.id.transfer_graph)).clearSamples();
            int background = sharing ? R.drawable.bg_green : R.drawable.bg_red;
            if (displayedSharingState != null && android.animation.ValueAnimator.areAnimatorsEnabled()) {
                TransitionDrawable transition = new TransitionDrawable(new android.graphics.drawable.Drawable[]{
                        ContextCompat.getDrawable(this, displayedSharingState ? R.drawable.bg_green : R.drawable.bg_red),
                        ContextCompat.getDrawable(this, background)});
                transition.setCrossFadeEnabled(true);
                main_bg.setImageDrawable(transition);
                transition.startTransition(280);
            } else main_bg.setImageResource(background);
            displayedSharingState = sharing;
        }
        updateGraphVisibility();
        TextView drawerStatus = ((NavigationView) findViewById(R.id.navigationView)).getHeaderView(0).findViewById(R.id.drawer_server_status);
        if (drawerStatus != null) drawerStatus.setText(sharing ? "Sharing is running" : "Sharing is off");
        View light = findViewById(R.id.sharing_light);
        ((TextView) findViewById(R.id.sharing_light_label)).setText(sharing ? "LIVE" : "READY WHEN YOU ARE");
        light.setVisibility(sharing ? View.VISIBLE : View.INVISIBLE);
        if (sharing && statusPulse == null && android.animation.ValueAnimator.areAnimatorsEnabled()) {
            statusPulse = android.animation.ObjectAnimator.ofFloat(light, "alpha", 1f, 0.45f, 1f);
            statusPulse.setDuration(1800); statusPulse.setRepeatCount(android.animation.ValueAnimator.INFINITE);
            statusPulse.start();
        } else if (!sharing && statusPulse != null) {
            statusPulse.cancel(); statusPulse = null; light.setAlpha(1f);
        }
        serverBtnTxt.setText(sharing ? R.string.app_stop_txt : R.string.app_start_txt);
        serverBtnTxt.setTextColor(getColor(sharing ? R.color.share_active_icon : R.color.share_ready_icon));
        serverBtnTxt.setSelected(sharing);
        ((View) serverBtn.getParent()).setSelected(sharing);
        TextView status = findViewById(R.id.sharing_status);
        TextView statusHint = findViewById(R.id.sharing_status_hint);
        if (status != null) {
            status.setText(sharing ? R.string.sharing_active : R.string.sharing_off);
            status.setTextColor(getColor(R.color.color_white));
        }
        if (statusHint != null) statusHint.setText(sharing ? R.string.sharing_active_hint : R.string.sharing_home_hint);
        serverBtn.setContentDescription(getString(sharing ? R.string.app_stop_txt : R.string.app_start_txt));
        TextView address = findViewById(R.id.connection_address);
        if (!sharing && address != null) address.setText(R.string.sharing_address_stopped);
        if(code==Constants.SERVER_ON) {
            serverBtn.setImageResource(R.drawable.ic_stop_sharing);
        }else if(code==Constants.SERVER_OFF) {
            serverBtn.setImageResource(R.drawable.ic_start_sharing);
        }
    }

    private void initQrScanner() {
        qrDialog.setContentView(R.layout.qr_scanner_layout);
        final QRCodeReaderView qrCodeReaderView=qrDialog.findViewById(R.id.qrdecoderview);
        final PointsOverlayLayout pointsOverlayView = qrDialog.findViewById(R.id.points_overlay_view);
        qrCodeReaderView.setOnQRCodeReadListener((text, points) -> {
            pointsOverlayView.setPoints(points);
            if(text.startsWith("http")) {
                qrDialog.dismiss();
                if(qrCodeReaderView!=null) {
                    qrCodeReaderView.stopCamera();
                }
                try {
                    Intent i = new Intent(Intent.ACTION_VIEW);
                    i.setData(Uri.parse(text));
                    startActivity(i);
                }catch (Exception e) {
                    //Do Nothing
                }
            }
        });
        qrCodeReaderView.setAutofocusInterval(2000L);
        qrCodeReaderView.setBackCamera();
        qrCodeReaderView.startCamera();
        qrDialog.show();
        qrDialog.setOnDismissListener(dialog -> {
            if(qrCodeReaderView!=null) {
                qrCodeReaderView.stopCamera();
            }
        });
    }

    public void showSnackbar(String msg) {
        DrawerLayout drawerLayout=findViewById(R.id.root_container);
        Snackbar snackbar = Snackbar.make(drawerLayout, msg, Snackbar.LENGTH_LONG);
        snackbar.setBackgroundTint(getColor(R.color.surface_container));
        snackbar.setTextColor(getColor(R.color.txt_color));
        if (bottomNavigation != null && bottomNavigation.getVisibility() == View.VISIBLE
                && !(bottomNavigation instanceof com.google.android.material.navigationrail.NavigationRailView)) snackbar.setAnchorView(bottomNavigation);
        snackbar.show();
    }

    public void showAuthDialog(final String device_id) {
        if(!isAuthDialogOpened) {
            DialogInterface.OnClickListener dialogClickListener = (dialog, which) -> {
                isAuthDialogOpened=false;
                switch (which) {
                    case DialogInterface.BUTTON_POSITIVE:
                        deviceManager.addDevice(device_id,Constants.DEVICE_TYPE_TEMP);
                        break;
                    case DialogInterface.BUTTON_NEGATIVE:
                        deviceManager.addDevice(device_id,Constants.DEVICE_TYPE_DENIED);
                        break;
                    case DialogInterface.BUTTON_NEUTRAL:
                        deviceManager.addDevice(device_id,Constants.DEVICE_TYPE_PERMANENT);
                        break;
                }
                Timer myTimer=new Timer();
                myTimer.schedule(new TimerTask() {
                    @Override
                    public void run() {
                        String count = deviceManager.getRemDevices() + " devices remembered";
                        MainActivity.this.runOnUiThread(() -> settRemDev.setText(count));
                    }
                },500);
            };

            isAuthDialogOpened=true;
            try {
                AlertDialog dialog = new AlertDialog.Builder(MainActivity.this)
                        .setMessage("Incoming new device request!\nAre you sure to allow this device?")
                        .setPositiveButton("Allow", dialogClickListener)
                        .setNegativeButton("Don't Allow", dialogClickListener)
                        .setNeutralButton("Always allow this device", dialogClickListener)
                        .setTitle("Request Confirmation")
                        .setIcon(R.drawable.ic_logo)
                        .setCancelable(false).show();
                TextView textView = dialog.findViewById(android.R.id.message);
                TextView textView2 = dialog.findViewById(android.R.id.button1);
                TextView textView3 = dialog.findViewById(android.R.id.button2);
                TextView textView4 = dialog.findViewById(android.R.id.button3);
                TextView textView5 = dialog.findViewById(getResources().getIdentifier( "alertTitle", "id", "android" ));
                Typeface face = Typeface.createFromAsset(getAssets(), "fonts/google_sans.ttf");
                textView.setTypeface(face);
                textView2.setTypeface(face, Typeface.BOLD);
                textView3.setTypeface(face, Typeface.BOLD);
                textView4.setTypeface(face, Typeface.BOLD);
                textView5.setTypeface(face, Typeface.BOLD);
            } catch (Exception e) {
                Log.d(Constants.LOG_TAG, "Dialog Error: " + e);
            }
        }
    }
}
