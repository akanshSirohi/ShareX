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
import android.widget.CheckBox;
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
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputLayout;

import java.io.File;
import java.io.FileOutputStream;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private final String[] PERMISSIONS = {
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
    };

    private ImageButton serverBtn,hide_logger_btn;
    Utils utils;
    String url = "";

    Timer progressTimer = new Timer();
    boolean isAuthDialogOpened = false;

    private ProgressDialog progress;
    private String serverRoot = null;
    private ConstraintLayout settings_view, main_view, qr_view, logger_wrapper;
    private TextView settDRoot, settRemDev, settTheme, plugin_folder_label;
    private TextView logger;
    private TextView scan_url, ssl_note;

    private ImageView main_bg,second_bg;
    BroadcastReceiver updateUIReciver;
    List<String> pmode_send_images = new ArrayList<>(), pmode_send_files = new ArrayList<>(), pmode_send_final_files = new ArrayList<>();
    ThemesData themesData = new ThemesData();
    private DrawerLayout drawerLayout;
    Dialog qrDialog;

    FabActionsHandler fabActionsHandler;
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
        setContentView(R.layout.activity_main);
        logger = findViewById(R.id.logger);
        logger_wrapper = findViewById(R.id.logger_wrapper);
        hide_logger_btn = findViewById(R.id.hide_logger_btn);
        logger.setMovementMethod(new ScrollingMovementMethod());
        utils = new Utils(this);
        serverRoot = utils.loadRoot();
        deviceManager = new DeviceManager(this);
        qrDialog = new Dialog(this);
        serverBtn = findViewById(R.id.serverBtn);
        settings_view = findViewById(R.id.settings_view);
        main_view = findViewById(R.id.main_view);
        qr_view = findViewById(R.id.qr_view);
        settings_view.setVisibility(View.GONE);
        main_view.setVisibility(View.VISIBLE);
        qr_view.setVisibility(View.GONE);
        main_bg = findViewById(R.id.main_bg);
        second_bg = findViewById(R.id.second_bg);
        scan_url = findViewById(R.id.scan_url);
        ssl_note = findViewById(R.id.ssl_note);
        drawerLayout = findViewById(R.id.root_container);

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

        fabActionsHandler = new FabActionsHandler(this, this);

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
                    pushLog("Server root changed to " + serverRoot, true);
                    settDRoot.setText(serverRoot);
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

        mutipleFilesActivityResultLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if(result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                Intent data = result.getData();
                if (data.getClipData() != null) {
                    int count = data.getClipData().getItemCount();
                    for (int i = 0; i < count; i++) {
                        Uri uri = data.getClipData().getItemAt(i).getUri();
                        String path = utils.filePickerUriResolve(uri);
                        if(path != null) {
                            pmode_send_files.add(path);
                        }
                    }
                    mergeAndUpdatePFilesList();
                }else if(data.getData() != null){
                    Uri uri = data.getData();
                    String path = utils.filePickerUriResolve(uri);
                    if(path != null) {
                        pmode_send_files.add(path);
                    }
                    mergeAndUpdatePFilesList();
                }
            }
        });

        gallerySelectorActivityResultLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                Intent data = result.getData();

                if (data.getClipData() != null) {
                    int count = data.getClipData().getItemCount();
                    for (int i = 0; i < count; i++) {
                        Uri uri = data.getClipData().getItemAt(i).getUri();
                        String path = utils.filePickerUriResolve(uri);
                        if(path != null && !pmode_send_images.contains(path)) {
                            pmode_send_images.add(path);
                        }
                    }
                    mergeAndUpdatePFilesList();
                }else if(data.getData() != null){
                    Uri uri = data.getData();
                    String path = utils.filePickerUriResolve(uri);
                    if(path != null && !pmode_send_images.contains(path)) {
                        pmode_send_images.add(path);
                    }
                    mergeAndUpdatePFilesList();
                }
            }
        });

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
        FloatingActionButton qrBtn = findViewById(R.id.qrBtn);
        qrBtn.setOnClickListener(v -> toggleQRView());
        hide_logger_btn.setOnClickListener(v -> {
            toggleLogger();
        });

        fabActionsHandler.setFabActionsHandlerListener(new FabActionsHandler.FabActionsHandlerListener() {
            @Override
            public void onClickImageSelect() {
                try {
                    Intent intent = new Intent(Intent.ACTION_PICK);
                    intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
                    intent.setDataAndType(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, "image/* video/*");
                    gallerySelectorActivityResultLauncher.launch(intent);
                } catch (Exception e) {
                    pushLog(e.toString(), true);
                }
                fabActionsHandler.hideFab();
            }

            @Override
            public void onClickFilesSelect() {
                Intent i = new Intent(Intent.ACTION_GET_CONTENT);
                i.setType("*/*");
                i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
                mutipleFilesActivityResultLauncher.launch(i);
                Toast.makeText(MainActivity.this, "Press and hold to select multiple files...", Toast.LENGTH_SHORT).show();
                fabActionsHandler.hideFab();
            }
        });
        if(utils.loadSetting(Constants.IS_LOGGER_VISIBLE)) {
            ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) logger_wrapper.getLayoutParams();
            params.bottomMargin = 0;
            logger_wrapper.setLayoutParams(params);
            hide_logger_btn.setImageResource(R.drawable.ic_caret_down);
        }else{
            if(utils.loadString(Constants.LOGGER_HEIGHT) != null) {
                ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) logger_wrapper.getLayoutParams();
                params.bottomMargin = -(Integer.parseInt(utils.loadString(Constants.LOGGER_HEIGHT)));
                logger_wrapper.setLayoutParams(params);
                hide_logger_btn.setImageResource(R.drawable.ic_caret_up);
            }
        }

        ImageButton url_cpy_btn = findViewById(R.id.url_cpy_btn);
        ImageButton url_share_btn = findViewById(R.id.url_share_btn);
        url_cpy_btn.setOnClickListener(view -> {
            // Copying code
            if (url.length() > 0) {
                ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                ClipData clip = ClipData.newPlainText("ShareX URL", url);
                clipboard.setPrimaryClip(clip);
                showSnackbar("URL Copied!");
            }else{
                showSnackbar("Start ShareX First!");
            }
        });
        url_share_btn.setOnClickListener(view -> {
            if (url.length() > 0) {
                Intent sharingIntent = new Intent(Intent.ACTION_SEND);
                sharingIntent.setType("text/plain");
                sharingIntent.putExtra(Intent.EXTRA_SUBJECT, "Sharing Url...");
                sharingIntent.putExtra(Intent.EXTRA_TEXT, url);
                startActivity(Intent.createChooser(sharingIntent, "Sharing ShareX Url"));
            }else{
                showSnackbar("Start ShareX first!");
            }
        });
        fabActionsHandler.init();
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
        unregisterReceiver(updateUIReciver);
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if(settings_view.getVisibility()==View.VISIBLE) {
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
        try {
            IntentFilter filter = new IntentFilter();
            filter.addAction(Constants.BROADCAST_SERVICE_TO_ACTIVITY);
            registerReceiver(updateUIReciver, filter);
        }catch (Exception e) {
            //Do Nothing!
        }
        privateMode();
        super.onResume();
    }

    @Override
    protected void onStart() {
        privateMode();
        super.onStart();
    }

    @SuppressLint("SdCardPath")
    public void initializeApp() {
        if(utils.isServiceRunning(ServerService.class)) {
            changeUI(Constants.SERVER_ON);
            String u=utils.loadString(Constants.SERVER_URL);
            if(u!=null) {
                url=u;
                pushLog("Server running at: "+url,false);
            }
        }
        serverBtn.setOnClickListener(view -> {
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
        });

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
                    try {
                        IntentFilter filter = new IntentFilter();
                        filter.addAction(Constants.BROADCAST_SERVICE_TO_ACTIVITY);
                        registerReceiver(updateUIReciver, filter);
                    }catch (Exception e) {
                        //Do Nothing!
                    }
                    if(!Constants.DEBUG) {
                        showAbout();
                    }
                    askIgnoreBatteryOptimizations();
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
        }else{
            askIgnoreBatteryOptimizations();
        }

        // Setup Side Bar Drawer Navigation
        NavigationView navigationView=findViewById(R.id.navigationView);
        navigationView.setNavigationItemSelectedListener(item -> {
            int itemId = item.getItemId();

            switch (itemId) {
                case R.id.plugins:
                    Intent plugins_intent=new Intent(MainActivity.this, PluginsActivity.class);
                    startActivity(plugins_intent);
                    break;
                case R.id.settings:
                    toggleSettings();
                    break;
                case R.id.scan_qr:
                    if(checkCameraPermission()) {
                        initQrScanner();
                    }else{
                        requestCameraPermission();
                    }
                    break;
                case R.id.trans_hist:
                    Intent tranferHistoryIntent=new Intent(MainActivity.this, TransferHistoryActivity.class);
                    startActivity(tranferHistoryIntent);
                    break;
                case R.id.clear_log:
                    clearLog();
                    break;
                case R.id.privacy_policy:
                    try {
                        Intent i = new Intent(Intent.ACTION_VIEW);
                        i.setData(Uri.parse(Constants.PRIVACY_POLICY_URL));
                        startActivity(i);
                    }catch (Exception e) {
                        //Do Nothing
                    }
                    break;
                case R.id.feedback:
                    Intent email = new Intent(Intent.ACTION_SENDTO,Uri.fromParts("mailto",Constants.FEEDBACK_MAIL,null));
                    email.putExtra(Intent.EXTRA_SUBJECT, "ShareX Feedback");
                    email.putExtra(Intent.EXTRA_TEXT, "Any feedback, query or suggestion...");
                    startActivity(Intent.createChooser(email, "Send Feedback"));
                    break;
                case R.id.about:
                    showAbout();
                    break;
            }
            drawerLayout.closeDrawer(GravityCompat.START);
            return true;
        });
        setUp_settingsListener();

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
                    Intent intent = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                    intent.addCategory("android.intent.category.DEFAULT");
                    intent.setData(Uri.parse("package:"+getPackageName()));
                    storagePermissionResultLauncher.launch(intent);
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
        if(action.equals(Constants.ACTION_URL)) {
            runOnUiThread(() -> {
                url=intent.getStringExtra("url");
                pushLog("Server started at: "+url,true);
                if(!utils.loadSetting(Constants.IS_LOGGER_VISIBLE)) {
                    showSnackbar("Server started at: "+url);
                }
            });
        }else if(action.equals(Constants.ACTION_MSG)) {
            runOnUiThread(() -> pushLog(intent.getStringExtra("msg"),true));
        }else if(action.equals(Constants.ACTION_PROGRESS)){
            changeP(intent.getIntExtra("value",100));
        }else if(action.equals(Constants.ACTION_AUTH)) {
            showAuthDialog(intent.getStringExtra("device_id"));
        }else if(action.equals(Constants.ACTION_UPDATE_UI_STOP)) {
            runOnUiThread(()->{
                changeUI(Constants.SERVER_OFF);
            });
        }
    }

    private void toggleLogger() {
        Animation animation;
        int modifier = logger_wrapper.getHeight() - 115;
        utils.saveString(Constants.LOGGER_HEIGHT,String.valueOf(modifier));
        animation = utils.loadSetting(Constants.IS_LOGGER_VISIBLE) ? new TranslateAnimation(0, 0,0, modifier) : new TranslateAnimation(0, 0,modifier, 0);
        animation.setDuration(500);
        animation.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {
                if(utils.loadSetting(Constants.IS_LOGGER_VISIBLE)) {
                    ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) logger_wrapper.getLayoutParams();
                    params.bottomMargin = 0;
                    logger_wrapper.setLayoutParams(params);
                }
            }

            @Override
            public void onAnimationEnd(Animation animation) {
                ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) logger_wrapper.getLayoutParams();
                if(utils.loadSetting(Constants.IS_LOGGER_VISIBLE)) {
                    params.bottomMargin = 0;
                    hide_logger_btn.setImageResource(R.drawable.ic_caret_down);
                }else{
                    params.bottomMargin = -modifier;
                    hide_logger_btn.setImageResource(R.drawable.ic_caret_up);
                }
                logger_wrapper.setLayoutParams(params);
            }

            @Override
            public void onAnimationRepeat(Animation animation) {}
        });
        logger_wrapper.startAnimation(animation);
        utils.saveSetting(Constants.IS_LOGGER_VISIBLE,!utils.loadSetting(Constants.IS_LOGGER_VISIBLE));
    }

    private void setUp_settingsListener() {
        CardView card1 = findViewById(R.id.sett_card1);
        CardView card2 = findViewById(R.id.sett_card2);
        CardView card3 = findViewById(R.id.sett_card3);
        CardView card4 = findViewById(R.id.sett_card4);
        CardView card5 = findViewById(R.id.sett_card5);
        CardView card6 = findViewById(R.id.sett_card6);
        CardView card7 = findViewById(R.id.sett_card7);
        CardView card8 = findViewById(R.id.sett_card8);
        CardView card9 = findViewById(R.id.sett_card9);
        CardView card10 = findViewById(R.id.sett_card10);
        CardView card11 = findViewById(R.id.sett_card11);
        CheckBox settHFCheck = findViewById(R.id.sett_hideF_checkBox);
        CheckBox settRMCheck = findViewById(R.id.sett_resMod_checkBox);
        CheckBox settFDCheck = findViewById(R.id.sett_frceDwl_checkBox);
        CheckBox settPMCheck = findViewById(R.id.sett_pMode_checkBox);
        CheckBox settAppsCheck = findViewById(R.id.sett_apps_checkBox);
        CheckBox settSslCheck = findViewById(R.id.sett_ssl_checkBox);
        CheckBox settPluginDevCheck = findViewById(R.id.sett_plugin_debug_checkBox);
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
                    pushLog("Server root changed to " + serverRoot, true);
                    settDRoot.setText(serverRoot);
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
                pushLog("Server root changed to " + serverRoot, true);
                settDRoot.setText(serverRoot);
                restartServer();
            }
        });
        card1.setOnClickListener(view -> {
            try {
                Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
                i.addCategory(Intent.CATEGORY_DEFAULT);
                Intent fIntent = Intent.createChooser(i, "Choose server root");
                rootFolderPickerResultLauncher.launch(fIntent);
            }catch (Exception e) {
                Log.d(Constants.LOG_TAG,e.toString());
            }
        });
        settDRoot.setText(serverRoot);
        
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

        // Private mode Settings
        settPMCheck.setOnCheckedChangeListener((compoundButton, b) -> {
            utils.saveSetting(Constants.PRIVATE_MODE,b);
            restartServer();
            privateMode();
        });
        settPMCheck.setChecked(utils.loadSetting(Constants.PRIVATE_MODE));
        card5.setOnClickListener(v -> settPMCheck.setChecked(!settPMCheck.isChecked()));

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
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url + "/SharexApp/debug"));
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

    private void toggleSettings() {
        qr_view.setVisibility(View.GONE);
        if(settings_view.getVisibility()==View.GONE) {
            settings_view.setVisibility(View.VISIBLE);
            main_view.setVisibility(View.GONE);
        }else{
            settings_view.setVisibility(View.GONE);
            main_view.setVisibility(View.VISIBLE);
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
        fabActionsHandler.setLabels("0 media selected","0 files selected");
        deviceManager.clearTmp();
        utils.junkCleaner();
        pmode_send_files.clear();
        pmode_send_images.clear();
        pmode_send_final_files.clear();
    }

    public void pushLog(String log,boolean b) {
        if(logger==null) {
            logger=findViewById(R.id.logger);
        }
        logger.append("$ "+log+"\n");
        if(b) {
            try {
                int scrollAmount = logger.getLayout().getLineTop(logger.getLineCount()) - logger.getHeight();
                if (scrollAmount > 0) {
                    logger.scrollTo(0, scrollAmount + 46);
                } else {
                    logger.scrollTo(0, 0);
                }
            }catch (Exception e) {
                //Do Nothing...
            }
        }
    }

    private void clearLog() {
        String str = "$ Welcome to "+getString(R.string.app_name)+"\n";
        logger.setText(str);
        logger.scrollTo(0, 0);
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
            if(url.length()>0) {
                TextView scan_url=findViewById(R.id.scan_url);
                scan_url.setText(url);
                ImageView qr_view=findViewById(R.id.qr_img);
                GenerateQR generateQR=new GenerateQR(qr_view, this, this);
                generateQR.execute(url);
            }
        }else{
            main_view.setVisibility(View.VISIBLE);
            qr_view.setVisibility(View.GONE);
            ImageView qr_view=findViewById(R.id.qr_img);
            qr_view.setImageResource(R.drawable.ic_logo);
            scan_url.setText("Start ShareX First!");
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

    public void changeP(int progress) {
        if(progress<=100) {
            try {
                final ProgressBar pBar;
                pBar=findViewById(R.id.progressBar);
                if(pBar.getVisibility()==ProgressBar.GONE) {
                    pBar.setVisibility(View.VISIBLE);
                }
                pBar.setProgress(progress);
                if(progress==100) {
                    pBar.setVisibility(View.GONE);
                }else{
                    try {
                        progressTimer.cancel();
                    }catch (Exception e) {
                        //Do nothing...
                    }
                    progressTimer=new Timer();
                    progressTimer.schedule(new TimerTask() {
                        @Override
                        public void run() {
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    changeP(100);
                                }
                            });
                        }
                    },3000);
                }
            }catch (Exception e) {
                Toast.makeText(this,e.getMessage(),Toast.LENGTH_LONG).show();
            }
        }
    }

    private void privateMode() {
        boolean b=utils.loadSetting(Constants.PRIVATE_MODE);
        ConstraintLayout topPanel=findViewById(R.id.top_panel);
        if(b) {
            topPanel.setVisibility(View.VISIBLE);
        }else{
            topPanel.setVisibility(View.GONE);
        }
    }

    private void mergeAndUpdatePFilesList() {
        pmode_send_final_files=new ArrayList<>();
        if(pmode_send_images.size()>0) {
            pmode_send_final_files.addAll(pmode_send_images);
            for(String path:pmode_send_files) {
                if(!pmode_send_final_files.contains(path)) {
                    pmode_send_final_files.add(path);
                }
            }
        }else{
            pmode_send_final_files.addAll(pmode_send_files);
            for(String path:pmode_send_images) {
                if(!pmode_send_final_files.contains(path)) {
                    pmode_send_final_files.add(path);
                }
            }
        }
        fabActionsHandler.setLabels(pmode_send_images.size()+" media selected",pmode_send_files.size()+" files selected");
        utils.pListWriter(pmode_send_final_files);
    }

    private void changeUI(int code) {
        if(code==Constants.SERVER_ON) {
            serverBtn.setImageResource(R.drawable.ic_stop);
            main_bg.setImageResource(R.drawable.trans_off_to_on);
            second_bg.setImageResource(R.drawable.bg_red);
            ((TransitionDrawable) main_bg.getDrawable()).startTransition(200);
        }else if(code==Constants.SERVER_OFF) {
            serverBtn.setImageResource(R.drawable.ic_start);
            main_bg.setImageResource(R.drawable.trans_on_to_off);
            second_bg.setImageResource(R.drawable.bg_green);
            ((TransitionDrawable) main_bg.getDrawable()).startTransition(200);
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
        snackbar.setBackgroundTint(Color.parseColor("#000a12"));
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
                Typeface face = Typeface.createFromAsset(getAssets(), "fonts/product_sans.ttf");
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
