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
import androidx.viewpager2.widget.ViewPager2;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.activity.OnBackPressedCallback;
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
import com.akansh.fileserversuit.common.Utils;
import com.akansh.fileserversuit.server.DeviceManager;
import com.akansh.fileserversuit.server.ServerService;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.akansh.fileserversuit.server.ThemesData;
import com.akansh.fileserversuit.server.WebInterfaceSetup;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

public class MainActivity extends AppCompatActivity {


    private ImageButton serverBtn;
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
    private ConstraintLayout qr_view;
    public static final String EXTRA_DESTINATION = "destination";
    private ViewPager2 mainPager;
    private View homePageView, settingsPageView;
    private boolean sharingPagesReady, activityResumed, restoringQRCode;
    private final java.util.Set<String> pluginPermissionDialogs = new java.util.HashSet<>();
    private final List<Runnable> pendingPageActions = new ArrayList<>();
    private TextView settDRoot, settRemDev, settTheme, plugin_folder_label, serverBtnTxt;
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
    ActivityResultLauncher<Intent> rootFolderPickerResultLauncher,
            mutipleFilesActivityResultLauncher,
            gallerySelectorActivityResultLauncher;

    int exit = 0;
    int currentTheme, storageChoice = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        EdgeToEdge.apply(this, findScreenView(R.id.root_container));
        utils = new Utils(this);
        serverRoot = utils.loadRoot();
        deviceManager = new DeviceManager(this);
        qrDialog = new Dialog(this);
        drawerLayout = findScreenView(R.id.root_container);
        qr_view = findScreenView(R.id.qr_view);
        scan_url = findScreenView(R.id.scan_url);
        ssl_note = findScreenView(R.id.ssl_note);
        bottomNavigation = findScreenView(R.id.bottom_nav);
        mainPager = findScreenView(R.id.main_pager);
        restoringQRCode = savedInstanceState != null && savedInstanceState.getBoolean("show_qr");
        registerFilePickers();
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() { handleBackNavigation(); }
        });
        mainPager.setOffscreenPageLimit(2);
        mainPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override public void onPageSelected(int position) {
                exit = 0;
                syncNavigation(position);
            }
        });
        bottomNavigation.setOnItemSelectedListener(item -> {
            navigateToDestination(item.getItemId(), false);
            return true;
        });
        bottomNavigation.setOnItemReselectedListener(item -> { });
        mainPager.setAdapter(new MainPagerAdapter(this));
        int destination = savedInstanceState == null ? destinationFromIntent(getIntent())
                : savedInstanceState.getInt(EXTRA_DESTINATION, R.id.home);
        navigateToDestination(destination, false);
    }

    // Both sharing pages stay attached so the activity can coordinate the shared server session.
    void onSharingPageCreated(int position, View view) {
        if (position == MainPagerAdapter.HOME) homePageView = view;
        if (position == MainPagerAdapter.SETTINGS) settingsPageView = view;
        if (sharingPagesReady || homePageView == null || settingsPageView == null) return;
        sharingPagesReady = true;
        bindSharingPageControls();
        synchronizeSharingUI();
        if (restoringQRCode && utils.isServiceRunning(ServerService.class) && !url.isEmpty()) toggleQRView();
        restoringQRCode = false;
        for (Runnable action : new ArrayList<>(pendingPageActions)) action.run();
        pendingPageActions.clear();
        if (activityResumed) resumeSharingUI();
    }

    private <T extends View> T findScreenView(int id) {
        T view = super.findViewById(id);
        if (view == null && homePageView != null) view = homePageView.findViewById(id);
        if (view == null && settingsPageView != null) view = settingsPageView.findViewById(id);
        return view;
    }

    private void whenSharingPagesReady(Runnable action) {
        if (sharingPagesReady) action.run();
        else pendingPageActions.add(action);
    }

    private void registerFilePickers() {
        rootFolderPickerResultLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> whenSharingPagesReady(() -> {
            if(result.getResultCode() == Activity.RESULT_OK) {
                try {
                    if (result.getData() == null || result.getData().getData() == null) return;
                    Uri uri = result.getData().getData();
                    String documentId = android.provider.DocumentsContract.getTreeDocumentId(uri);
                    String removablePath = utils.getSDCardRoot();
                    File removable = removablePath == null || removablePath.isEmpty() ? null : new File(removablePath);
                    File f = StorageFolder.resolve(documentId, Environment.getExternalStorageDirectory(), removable);
                    utils.saveStorage(documentId.startsWith("primary:") ? Environment.getExternalStorageDirectory().getAbsolutePath() : removablePath);
                    serverRoot = f.getAbsolutePath();
                    utils.saveRoot(serverRoot);
                    pushLog("Shared folder changed to " + serverRoot, true);
                    updateRootLabel();
                    restartServer();
                }catch (Exception e) {
                    Log.d(Constants.LOG_TAG,"Err2: "+e);
                    showSnackbar("Choose an accessible folder on internal storage or an SD card.");
                }
            }
        }));

        mutipleFilesActivityResultLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(), result -> whenSharingPagesReady(() -> addPickedFiles(result, false)));
        gallerySelectorActivityResultLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(), result -> whenSharingPagesReady(() -> addPickedFiles(result, true)));

    }

    private void bindSharingPageControls() {
        serverBtn = findScreenView(R.id.serverBtn);
        serverBtnTxt = findScreenView(R.id.serverBtnTxt);
        main_bg = findScreenView(R.id.main_bg);
        second_bg = findScreenView(R.id.second_bg);
        scan_url = findScreenView(R.id.scan_url);
        ssl_note = findScreenView(R.id.ssl_note);
        drawerLayout = findScreenView(R.id.root_container);

        ImageButton nav_btn = findScreenView(R.id.nav_btn);
        nav_btn.setOnClickListener(v -> drawerLayout.openDrawer(GravityCompat.START));

        //Settings Components
        settDRoot = findScreenView(R.id.sett_subtitle1);
        settRemDev = findScreenView(R.id.sett_subtitle6);
        settTheme = findScreenView(R.id.sett_subtitle7);
        plugin_folder_label = findScreenView(R.id.sett_subtitle11);
        plugin_folder_label.setText(R.string.plugin_development_summary);
        String dev_count = rememberedDeviceCount();
        settRemDev.setText(dev_count);
        settTheme.setText(themesData.getDisplayItem(utils.loadInt(Constants.WEB_INTERFACE_THEME,0)));

        currentTheme = themesData.normalizeIndex(utils.loadInt(Constants.WEB_INTERFACE_THEME,0));

        pmode_send_files.addAll(utils.pListReader());
        privateModeSwitch = findScreenView(R.id.private_mode_toggle);
        privateModeSwitch.setChecked(utils.loadSetting(Constants.PRIVATE_MODE));
        privateModeSwitch.setOnCheckedChangeListener((button, enabled) -> {
            if (utils.loadSetting(Constants.PRIVATE_MODE) == enabled) return;
            utils.saveSetting(Constants.PRIVATE_MODE, enabled);
            privateMode();
            restartServer();
        });

        initializeApp();

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
        View qrBtn = findScreenView(R.id.qrBtn);
        qrBtn.setOnClickListener(v -> {
            if (utils.isServiceRunning(ServerService.class)) toggleQRView();
            else showSnackbar(getString(R.string.qr_requires_sharing));
        });
        findScreenView(R.id.qr_back_btn).setOnClickListener(v -> toggleQRView());
        findScreenView(R.id.qr_copy_btn).setOnClickListener(v -> copyConnectionAddress());
        findScreenView(R.id.qr_share_btn).setOnClickListener(v -> shareConnectionAddress());
        findScreenView(R.id.select_files_btn).setOnClickListener(v -> openFilePicker(false));
        findScreenView(R.id.select_media_btn).setOnClickListener(v -> openFilePicker(true));
        findScreenView(R.id.manage_files_btn).setOnClickListener(v -> reviewSelectedFiles());
        findScreenView(R.id.choose_folder_btn).setOnClickListener(v -> findScreenView(R.id.sett_card1).performClick());
        updateSelectedFilesUI();
        ImageButton url_cpy_btn = findScreenView(R.id.url_cpy_btn);
        ImageButton url_share_btn = findScreenView(R.id.url_share_btn);
        url_cpy_btn.setOnClickListener(view -> {
            copyConnectionAddress();
        });
        url_share_btn.setOnClickListener(view -> {
            shareConnectionAddress();
        });
        privateMode();
        centerContent(findScreenView(R.id.home_content), 680);
        centerContent(findScreenView(R.id.settings_content), 760);
    }

    private int destinationFromIntent(Intent intent) {
        return intent.getIntExtra(EXTRA_DESTINATION,
                intent.getBooleanExtra("open_settings", false) ? R.id.settings : R.id.home);
    }

    private void syncNavigation(int position) {
        int destination = MainPagerAdapter.destinationForPosition(position);
        bottomNavigation.getMenu().findItem(destination).setChecked(true);
        ((NavigationView) findScreenView(R.id.navigationView)).setCheckedItem(destination);
    }

    private void navigateToDestination(int destination, boolean animate) {
        if (qr_view.getVisibility() == View.VISIBLE) hideQRCode();
        int position = MainPagerAdapter.positionForDestination(destination);
        mainPager.setCurrentItem(position, animate);
        syncNavigation(position);
        exit = 0;
    }

    @Override
    protected void onPause() {
        activityResumed = false;
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
        if (updateUIReciver != null) {
            try { unregisterReceiver(updateUIReciver); } catch (IllegalArgumentException ignored) { }
        }
        selectionExecutor.shutdown();
        pendingPageActions.clear();
        homePageView = null;
        settingsPageView = null;
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        handleBackNavigation();
    }

    private void handleBackNavigation() {
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START);
        } else if (qr_view.getVisibility() == View.VISIBLE) {
            hideQRCode();
            navigateToDestination(R.id.home, false);
        } else if (mainPager.getCurrentItem() != MainPagerAdapter.HOME) {
            navigateToDestination(R.id.home, true);
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
            moveTaskToBack(true);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        activityResumed = true;
        if (sharingPagesReady) resumeSharingUI();
    }

    private void synchronizeSharingUI() {
        boolean running = utils.isServiceRunning(ServerService.class);
        String savedUrl = running ? utils.loadString(Constants.SERVER_URL) : null;
        url = savedUrl == null ? "" : savedUrl;
        changeUI(running ? Constants.SERVER_ON : Constants.SERVER_OFF);
        if (running && !url.isEmpty()) ((TextView) findScreenView(R.id.connection_address)).setText(url);
        privateMode();
    }

    private void resumeSharingUI() {
        synchronizeSharingUI();
        com.akansh.fileserversuit.server.TransferStats.Snapshot activity = com.akansh.fileserversuit.server.TransferStats.snapshot();
        lastSent = activity.sent; lastReceived = activity.received;
        lastSampleTime = android.os.SystemClock.elapsedRealtime();
        activityHandler.removeCallbacks(activitySampler);
        activityHandler.post(activitySampler);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        navigateToDestination(destinationFromIntent(intent), false);
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (sharingPagesReady) privateMode();
    }

    @SuppressLint("SdCardPath")
    public void initializeApp() {
        Log.d(Constants.LOG_TAG,"App Initialized!");
        if(utils.isServiceRunning(ServerService.class)) {
            changeUI(Constants.SERVER_ON);
            String u=utils.loadString(Constants.SERVER_URL);
            if(u!=null) {
                url=u;
                TextView address = findScreenView(R.id.connection_address);
                if (address != null) address.setText(url);
                if (scan_url != null) scan_url.setText(url);
                pushLog("Server running at: "+url,false);
            }
        }
        View.OnClickListener sharingListener = view -> {
            if (!utils.isServiceRunning(ServerService.class) && !checkStoragePermissions()) {
                new MaterialAlertDialogBuilder(this)
                        .setTitle(R.string.permissions_storage_title)
                        .setMessage(R.string.permissions_storage_reason)
                        .setNegativeButton(android.R.string.cancel, null)
                        .setPositiveButton(R.string.permissions_storage_action, (dialog, which) -> requestStoragePermissions())
                        .show();
                return;
            }
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

        // The installer controls version checks and optional development refreshes.
        WebInterfaceSetup webInterfaceSetup=new WebInterfaceSetup(getPackageName(), this, this, utils);
        if(webInterfaceSetup.needsSetup()) {
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
        NavigationView navigationView=findScreenView(R.id.navigationView);
        navigationView.getMenu().findItem(R.id.plugins).setVisible(true);
        navigationView.setNavigationItemSelectedListener(item -> {
            int itemId = item.getItemId();

            if (itemId == R.id.home) {
                navigateToDestination(R.id.home, false);
            } else if (itemId == R.id.plugins) {
                Intent pluginsIntent = new Intent(MainActivity.this, PluginsActivity.class);
                startActivity(pluginsIntent);
            } else if (itemId == R.id.settings) {
                navigateToDestination(R.id.settings, false);
            } else if (itemId == R.id.scan_qr) {
                if (checkCameraPermission()) initQrScanner(); else requestCameraPermission();
            } else if (itemId == R.id.trans_hist) {
                navigateToDestination(R.id.trans_hist, false);
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
        findScreenView(R.id.settings_permissions).setOnClickListener(view ->
                startActivity(new Intent(this, PermissionsActivity.class)));
        findScreenView(R.id.settings_web_password).setOnClickListener(view -> {
            if (getSupportFragmentManager().findFragmentByTag(WebPasswordDialog.TAG) == null) {
                new WebPasswordDialog().show(getSupportFragmentManager(), WebPasswordDialog.TAG);
            }
        });
        MaterialSwitch transferGraphSwitch = findScreenView(R.id.transfer_graph_switch);
        boolean showTransferGraph = utils.loadSetting(Constants.SHOW_TRANSFER_GRAPH);
        transferGraphSwitch.setChecked(showTransferGraph);
        transferGraphSwitch.setOnCheckedChangeListener((buttonView, enabled) -> {
            utils.saveSetting(Constants.SHOW_TRANSFER_GRAPH, enabled);
            updateGraphVisibility();
        });
        updateRootLabel();
        updateGraphVisibility();
    }

    public boolean checkStoragePermissions() {
        return PermissionsActivity.hasStorageAccess(this);
    }

    public boolean checkCameraPermission() {
        return ActivityCompat.checkSelfPermission(this,Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
    }

    public void requestCameraPermission() {
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            new MaterialAlertDialogBuilder(this).setTitle(R.string.permissions_camera_title)
                    .setMessage(R.string.permissions_camera_reason)
                    .setNegativeButton(android.R.string.cancel, null)
                    .setPositiveButton(R.string.permissions_camera_action, (dialog, which) -> {
                        if (utils.loadSetting("asked_camera_permission") && !shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) {
                            startActivity(new Intent(this, PermissionsActivity.class));
                        } else {
                            utils.saveSetting("asked_camera_permission", true);
                            requestPermissions(new String[]{Manifest.permission.CAMERA}, Constants.CAMERA_REQ_CODE);
                        }
                    }).show();
        }
    }

    public void requestStoragePermissions() {
        startActivity(new Intent(this, PermissionsActivity.class));
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if(requestCode == Constants.CAMERA_REQ_CODE) {
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
                TextView address = findScreenView(R.id.connection_address);
                if (address != null) address.setText(url);
                if (scan_url != null) scan_url.setText(url);
                changeUI(Constants.SERVER_ON);
                pushLog("Server started at: "+url,true);
                showSnackbar("Server started at: "+url);
            });
        }else if(action.equals(Constants.ACTION_MSG)) {
            runOnUiThread(() -> pushLog(intent.getStringExtra("msg"),true));
        }else if(action.equals(Constants.ACTION_PROGRESS)){
            updateTransferActivity();
        }else if(action.equals(Constants.ACTION_AUTH)) {
            showAuthDialog(intent.getStringExtra("device_id"), intent.getStringExtra("device_name"));
        }else if(action.equals(Constants.ACTION_PLUGIN_AUTH)) {
            showPluginPermissionDialog(intent.getStringExtra("device_id"), intent.getStringExtra("plugin_package"), intent.getStringExtra("plugin_name"));
        }else if(action.equals(Constants.ACTION_UPDATE_UI_STOP)) {
            runOnUiThread(()->{
                changeUI(Constants.SERVER_OFF);
            });
        }
    }

    private void setUp_settingsListener() {
        CardView card1 = findScreenView(R.id.sett_card1);
        CardView card2 = findScreenView(R.id.sett_card2);
        CardView card3 = findScreenView(R.id.sett_card3);
        CardView card6 = findScreenView(R.id.sett_card6);
        CardView card7 = findScreenView(R.id.sett_card7);
        CardView card8 = findScreenView(R.id.sett_card8);
        CardView card9 = findScreenView(R.id.sett_card9);
        CardView card10 = findScreenView(R.id.sett_card10);
        CardView card11 = findScreenView(R.id.sett_card11);
        CompoundButton settHFCheck = findScreenView(R.id.sett_hideF_checkBox);
        CompoundButton settRMCheck = findScreenView(R.id.sett_resMod_checkBox);
        CompoundButton settAppsCheck = findScreenView(R.id.sett_apps_checkBox);
        CompoundButton settSslCheck = findScreenView(R.id.sett_ssl_checkBox);
        CompoundButton settPluginDevCheck = findScreenView(R.id.sett_plugin_debug_checkBox);
        TextView settPort = findScreenView(R.id.sett_subtitle8);
        View settResetRoot = findScreenView(R.id.sett_reset_root);
        findScreenView(R.id.sett_choose_root).setOnClickListener(view -> card1.performClick());
        ImageButton sett_plugin_debug_link = findScreenView(R.id.sett_plugin_debug_link);


        // App Root Settings
        settResetRoot.setOnClickListener(view -> {
            if(utils.isExternalStorageMounted()) {
                String[] options = {"Internal Storage","SD Card"};
                String[] storages = {Environment.getExternalStorageDirectory().getAbsolutePath(),utils.getSDCardRoot()};
                com.google.android.material.dialog.MaterialAlertDialogBuilder builder = new com.google.android.material.dialog.MaterialAlertDialogBuilder(MainActivity.this);
                builder.setTitle(R.string.shared_root_title);
                storageChoice = Math.max(0, Math.min(options.length - 1, storageChoice));
                builder.setSingleChoiceItems(options, storageChoice, (dialog, which) -> {
                    storageChoice = which;
                });
                builder.setPositiveButton("Set", (dialog, which) -> {
                    dialog.dismiss();
                    String selectedRoot = storages[storageChoice];
                    if (selectedRoot == null || !new File(selectedRoot).isDirectory() || !new File(selectedRoot).canRead()) {
                        showSnackbar("Selected storage is unavailable.");
                        return;
                    }
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

        // Inspect remembered browsers before removing one or all.
        card6.setOnClickListener(v -> RememberedDevicesDialog.show(this, deviceManager, () -> {
            if (settRemDev != null) settRemDev.setText(rememberedDeviceCount());
        }));

        // Theme Chooser Setting
        card7.setOnClickListener(v -> {
            ThemePickerDialog.show(this, utils.loadInt(Constants.WEB_INTERFACE_THEME, 0), selected -> {
                currentTheme = selected;
                utils.saveInt(Constants.WEB_INTERFACE_THEME, currentTheme);
                settTheme.setText(themesData.getDisplayItem(currentTheme));
            });
        });

        // Sharex Port Settings
        settPort.setText("Port: "+utils.loadInt(Constants.SERVER_PORT,Constants.SERVER_PORT_DEFAULT));
        card8.setOnClickListener(v-> {
            AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
            builder.setTitle("ShareX Port:");
            builder.setMessage("Please enter a port number between 1024 and 65534. The next port is used for plugin WebSockets.");
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
                    if(port >= 1024 && port <= 65534) {
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
        card11.setOnClickListener(v -> settPluginDevCheck.setChecked(!settPluginDevCheck.isChecked()));
        settPluginDevCheck.setChecked(utils.loadSetting(Constants.PLUGIN_DEV));
        settPluginDevCheck.setOnCheckedChangeListener((buttonView, isChecked) -> {
            new com.akansh.fileserversuit.server.PluginDevelopment(this).setEnabled(isChecked);
            restartServer();
            if (isChecked) showSnackbar(getString(R.string.plugin_development_enabled));
        });

        sett_plugin_debug_link.setOnClickListener(v -> showPluginDevelopmentConnection());


    }

    private void showPluginDevelopmentConnection() {
        if (!utils.loadSetting(Constants.PLUGIN_DEV)) {
            showSnackbar(getString(R.string.plugin_development_enable_first));
            return;
        }
        if (!utils.isServiceRunning(ServerService.class) || url.isEmpty()) {
            showSnackbar("Start ShareX first!");
            return;
        }
        com.akansh.fileserversuit.server.PluginDevelopment development =
                new com.akansh.fileserversuit.server.PluginDevelopment(this);
        String connection = url + "#sharex-dev-token=" + development.token();
        TextView details = new TextView(this);
        int padding = getResources().getDimensionPixelSize(R.dimen.dp_19);
        details.setPadding(padding, padding, padding, padding);
        details.setText(getString(R.string.plugin_development_instructions, connection));
        details.setTextIsSelectable(true);
        new AlertDialog.Builder(this)
                .setTitle(R.string.plugin_development_connection)
                .setView(details)
                .setPositiveButton(R.string.plugin_development_copy, (dialog, which) -> {
                    ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                    clipboard.setPrimaryClip(ClipData.newPlainText("ShareX development connection", connection));
                    showSnackbar(getString(R.string.plugin_development_copied));
                })
                .setNeutralButton(R.string.plugin_development_rotate, (dialog, which) -> {
                    development.setEnabled(true);
                    restartServer();
                    showSnackbar(getString(R.string.plugin_development_rotated));
                })
                .setNegativeButton(android.R.string.cancel, null).show();
    }

    private void updateGraphVisibility() {
        View section = findScreenView(R.id.transfer_activity_section);
        boolean sharing = Boolean.TRUE.equals(displayedSharingState);
        if (section != null) section.setVisibility(sharing ? View.VISIBLE : View.GONE);
        View graph = findScreenView(R.id.transfer_graph);
        if (graph != null) graph.setVisibility(utils.loadSetting(Constants.SHOW_TRANSFER_GRAPH)
                ? View.VISIBLE : View.GONE);
    }

    private void updateRootLabel() {
        if (settDRoot == null || utils == null) return;
        settDRoot.setText(getString(R.string.settings_root_advanced_summary, serverRoot));
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
        TextView address = findScreenView(R.id.connection_address);
        if (address != null) address.setText(R.string.sharing_address_stopped);
        deviceManager.clearTmp();

    }

    public void pushLog(String log, boolean scroll) {
        if (log != null) Log.d(Constants.LOG_TAG, log);
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
        if (!checkStoragePermissions()) {
            showSnackbar(getString(R.string.permissions_storage_needed));
            return;
        }
        if(!utils.isServiceRunning(ServerService.class)) {
            Intent intent = new Intent(MainActivity.this, ServerService.class);
            ContextCompat.startForegroundService(this, intent);
        }
    }

    public void toggleQRView() {
        if(qr_view.getVisibility()==View.GONE) {
            mainPager.setVisibility(View.INVISIBLE);
            qr_view.setVisibility(View.VISIBLE);
            findScreenView(R.id.bottom_nav).setVisibility(View.GONE);
            if(url.length()>0) {
                TextView scan_url=findScreenView(R.id.scan_url);
                scan_url.setText(url);
                TextView address=findScreenView(R.id.connection_address);
                if(address!=null) address.setText(url);
                ImageView qr_view=findScreenView(R.id.qr_img);
                GenerateQR generateQR=new GenerateQR(qr_view, this, this);
                generateQR.execute(url);
            }
        } else {
            hideQRCode();
            navigateToDestination(R.id.home, false);
        }
        ssl_note.setVisibility(utils.loadSetting(Constants.SSL) ? View.VISIBLE : View.GONE);
    }

    private void hideQRCode() {
        qr_view.setVisibility(View.GONE);
        mainPager.setVisibility(View.VISIBLE);
        bottomNavigation.setVisibility(View.VISIBLE);
        ((ImageView) findScreenView(R.id.qr_img)).setImageResource(R.drawable.ic_logo);
        scan_url.setText(R.string.qr_stopped);
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
            ((TransferGraphView) findScreenView(R.id.transfer_graph)).addSample(sending, receiving);
            lastSampleTime = now; lastSent = snapshot.sent; lastReceived = snapshot.received;
            ((TextView) findScreenView(R.id.send_speed)).setText(formatBytes((long) sending) + "/s");
            ((TextView) findScreenView(R.id.receive_speed)).setText(formatBytes((long) receiving) + "/s");
            findScreenView(R.id.transfer_graph).setContentDescription("Sending " + formatBytes((long) sending)
                    + " per second, receiving " + formatBytes((long) receiving) + " per second");
        }
        ((TextView) findScreenView(R.id.session_totals)).setText(formatBytes(snapshot.sent) + " sent \u00b7 " + formatBytes(snapshot.received) + " received");
        ((TextView) findScreenView(R.id.activity_state)).setText(snapshot.activeCount > 0
                ? snapshot.activeCount + (snapshot.activeCount == 1 ? " active transfer" : " active transfers") : "Waiting for a transfer");
        View transfer = findScreenView(R.id.active_transfer_card);
        ProgressBar progress = findScreenView(R.id.progressBar);
        TextView description = findScreenView(R.id.transfer_progress_label);
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
        SharingStatusView statusDial = findScreenView(R.id.sharing_status_dial);
        if (statusDial != null) statusDial.setPrivateMode(enabled);
        if (privateModeSwitch != null && privateModeSwitch.isChecked() != enabled) privateModeSwitch.setChecked(enabled);
        ((TextView) findScreenView(R.id.mode_description)).setText(enabled
                ? R.string.private_mode_explanation : R.string.folder_mode_explanation);
        findScreenView(R.id.file_selection_panel).setVisibility(enabled ? View.VISIBLE : View.GONE);
        findScreenView(R.id.choose_folder_btn).setVisibility(enabled ? View.GONE : View.VISIBLE);
        findScreenView(R.id.top_panel).setVisibility(View.GONE);
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
        TextView summary = findScreenView(R.id.selected_files_summary);
        summary.setText(paths.isEmpty() ? getString(R.string.no_files_selected)
                : getResources().getQuantityString(R.plurals.files_selected, paths.size(), paths.size()));
        findScreenView(R.id.manage_files_btn).setVisibility(paths.isEmpty() ? View.GONE : View.VISIBLE);
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
        findScreenView(R.id.select_files_btn).setEnabled(false);
        findScreenView(R.id.select_media_btn).setEnabled(false);
        ((TextView) findScreenView(R.id.selected_files_summary)).setText("Preparing selected files...");
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
                findScreenView(R.id.select_files_btn).setEnabled(true);
                findScreenView(R.id.select_media_btn).setEnabled(true);
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
        state.putInt(EXTRA_DESTINATION, MainPagerAdapter.destinationForPosition(mainPager.getCurrentItem()));
        state.putBoolean("show_qr", qr_view.getVisibility() == View.VISIBLE);
    }

    private void changeUI(int code) {
        boolean sharing = code == Constants.SERVER_ON;
        serverBtn.setSelected(sharing);
        ((SharingStatusView) findScreenView(R.id.sharing_status_dial)).setRunning(sharing);
        if (displayedSharingState == null || displayedSharingState != sharing) {
            if (sharing) ((TransferGraphView) findScreenView(R.id.transfer_graph)).clearSamples();
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
        TextView drawerStatus = ((NavigationView) findScreenView(R.id.navigationView)).getHeaderView(0).findViewById(R.id.drawer_server_status);
        if (drawerStatus != null) drawerStatus.setText(sharing ? "Sharing is running" : "Sharing is off");
        View light = findScreenView(R.id.sharing_light);
        ((TextView) findScreenView(R.id.sharing_light_label)).setText(sharing ? "LIVE" : "READY WHEN YOU ARE");
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
        TextView status = findScreenView(R.id.sharing_status);
        TextView statusHint = findScreenView(R.id.sharing_status_hint);
        if (status != null) {
            status.setText(sharing ? R.string.sharing_active : R.string.sharing_off);
            status.setTextColor(getColor(R.color.color_white));
        }
        if (statusHint != null) statusHint.setText(sharing ? R.string.sharing_active_hint : R.string.sharing_home_hint);
        serverBtn.setContentDescription(getString(sharing ? R.string.app_stop_txt : R.string.app_start_txt));
        TextView address = findScreenView(R.id.connection_address);
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
        DrawerLayout drawerLayout=findScreenView(R.id.root_container);
        Snackbar snackbar = Snackbar.make(drawerLayout, msg, Snackbar.LENGTH_LONG);
        snackbar.setBackgroundTint(getColor(R.color.surface_container));
        snackbar.setTextColor(getColor(R.color.txt_color));
        if (bottomNavigation != null && bottomNavigation.getVisibility() == View.VISIBLE
                && !(bottomNavigation instanceof com.google.android.material.navigationrail.NavigationRailView)) snackbar.setAnchorView(bottomNavigation);
        snackbar.show();
    }

    private String rememberedDeviceCount() {
        int count = deviceManager.getRemDevices();
        return getResources().getQuantityString(R.plurals.remembered_devices_count, count, count);
    }

    public void showAuthDialog(final String device_id) { showAuthDialog(device_id, ""); }

    public void showAuthDialog(final String device_id, final String deviceName) {
        if (isAuthDialogOpened || isFinishing() || isDestroyed()) return;
        isAuthDialogOpened = true;
        try {
            androidx.appcompat.app.AlertDialog dialog = WebAccessDialog.show(this,
                    utils.loadSetting(Constants.PRIVATE_MODE), utils.loadSetting(Constants.RESTRICT_MODIFY), type -> {
                        deviceManager.addDevice(device_id, type, deviceName);
                        if (settRemDev != null) settRemDev.setText(rememberedDeviceCount());
                    });
            dialog.setOnDismissListener(ignored -> isAuthDialogOpened = false);
        } catch (Exception e) {
            isAuthDialogOpened = false;
            Log.e(Constants.LOG_TAG, "Unable to show browser approval", e);
        }
    }

    private void showPluginPermissionDialog(String deviceId, String pluginPackage, String pluginName) {
        if (deviceId == null || pluginPackage == null || pluginName == null || isFinishing() || isDestroyed()) return;
        String request = deviceId + "|" + pluginPackage;
        if (!pluginPermissionDialogs.add(request)) return;
        String message = getString(R.string.plugin_permission_request, pluginName, pluginPackage);
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.plugin_permission_title)
                .setMessage(message)
                .setNegativeButton(R.string.web_access_deny, (dialog, which) ->
                        new com.akansh.fileserversuit.server.PluginAccessManager(this).resolve(deviceId, pluginPackage, false, false))
                .setNeutralButton(R.string.plugin_permission_allow_once, (dialog, which) ->
                        new com.akansh.fileserversuit.server.PluginAccessManager(this).resolve(deviceId, pluginPackage, true, false))
                .setPositiveButton(R.string.plugin_permission_allow_always, (dialog, which) ->
                        new com.akansh.fileserversuit.server.PluginAccessManager(this).resolve(deviceId, pluginPackage, true, true))
                .setOnDismissListener(dialog -> pluginPermissionDialogs.remove(request))
                .show();
    }
}
