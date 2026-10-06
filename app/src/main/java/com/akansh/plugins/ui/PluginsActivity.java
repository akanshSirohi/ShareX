package com.akansh.plugins.ui;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import android.app.ProgressDialog;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import com.akansh.sharex.common.Constants;
import com.akansh.sharex.R;
import com.akansh.sharex.common.EdgeToEdge;
import com.akansh.sharex.common.Utils;
import com.akansh.plugins.common.PluginInstallStatus;
import com.akansh.plugins.PluginsManager;
import com.akansh.plugins.common.PluginsManagerPluginStatusListener;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

public class PluginsActivity extends AppCompatActivity {

    TabLayout tabLayout;
    ViewPager2 viewPager;

    private final String[] titles = new String[]{"Installed", "Store"};

    InstalledPlugins installedPlugins;
    PluginsStore pluginsStore;
    private PluginsManager pluginsManager;
    ProgressDialog progress;
    boolean catalogFetchInProgress;
    private final androidx.activity.result.ActivityResultLauncher<String[]> zipPicker = registerForActivityResult(
            new androidx.activity.result.contract.ActivityResultContracts.OpenDocument(), uri -> {
                if (uri == null) return;
                new androidx.appcompat.app.AlertDialog.Builder(this)
                        .setTitle(R.string.plugin_zip_warning_title)
                        .setMessage(R.string.plugin_zip_warning)
                        .setNegativeButton(android.R.string.cancel, null)
                        .setPositiveButton(R.string.plugin_zip_trust_install, (dialog, which) -> {
                            showLoading(getString(R.string.plugin_zip_installing), new Utils(this));
                            new Thread(() -> {
                                PluginInstallStatus result = getPluginsManager().installPluginZip(uri);
                                runOnUiThread(() -> {
                                    if (isFinishing() || isDestroyed()) return;
                                    hideLoading();
                                    Toast.makeText(this, result.message, Toast.LENGTH_LONG).show();
                                    if (!result.error) {
                                        InstalledPlugins installed = activeInstalledPlugins();
                                        if (installed.getView() != null) { installed.updatePluginsList(); installed.checkPluginsEmpty(); }
                                        if (activePluginsStore().getView() != null) activePluginsStore().updatePluginStore();
                                        viewPager.setCurrentItem(0, false);
                                    }
                                });
                            }, "ShareX-PluginImport").start();
                        }).show();
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_plugins);
        EdgeToEdge.apply(this, findViewById(R.id.plugins_root));
        findViewById(R.id.plugins_back).setOnClickListener(view -> finish());
        findViewById(R.id.plugins_install_zip).setOnClickListener(view ->
                zipPicker.launch(new String[]{"application/zip", "application/x-zip-compressed", "application/octet-stream"}));
        findViewById(R.id.plugins_development).setOnClickListener(view -> startActivity(
                new android.content.Intent(this, com.akansh.sharex.ui.MainActivity.class)
                        .addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP | android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        .putExtra("open_settings", true)));
        tabLayout = findViewById(R.id.tabLayout);
        viewPager = findViewById(R.id.viewPager);
        findViewById(R.id.plugins_refresh).setOnClickListener(view -> {
            if (viewPager.getCurrentItem() != 1) viewPager.setCurrentItem(1, false);
            else refreshCatalog();
        });
        // Setup Plugins
        Utils utils = new Utils(this);
        pluginsManager = new PluginsManager(this, this, utils);
        installedPlugins = new InstalledPlugins(getApplicationContext(), this, pluginsManager);
        configureInstalledPlugins(installedPlugins);
        pluginsStore = new PluginsStore(getApplicationContext(), this, pluginsManager);
        configurePluginsStore(pluginsStore);
        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                InstalledPlugins installed = activeInstalledPlugins();
                if(position == 0) {
                    installed.checkPluginsEmpty();
                } else if (position == 1) {
                    refreshCatalog();
                }
                super.onPageSelected(position);
            }
        });

        pluginsManager.setPluginsManagerPluginStatusListener(new PluginsManagerPluginStatusListener() {
            @Override
            public void onPluginUpdateDownload(boolean res, String packageName) {
                if (isFinishing() || isDestroyed()) return;
                if(res) {
                    PluginInstallStatus pluginInstallStatus = pluginsManager.installPlugin(packageName);
                    Toast.makeText(PluginsActivity.this, pluginInstallStatus.message, Toast.LENGTH_LONG).show();
                    if(!pluginInstallStatus.error) {
                        activeInstalledPlugins().updatePluginsList();
                        activePluginsStore().updatePluginStore();
                    }
                }
                hideLoading();
            }

            @Override
            public void onNewPluginDownload(boolean res, String packageName) {
                if (isFinishing() || isDestroyed()) return;
                if(res) {
                    PluginInstallStatus pluginInstallStatus = pluginsManager.installPlugin(packageName);
                    Toast.makeText(PluginsActivity.this, pluginInstallStatus.message, Toast.LENGTH_LONG).show();
                    if(!pluginInstallStatus.error) {
                        activeInstalledPlugins().updatePluginsList();
                        activePluginsStore().updatePluginStore();
                    }
                }else{
                    Toast.makeText(PluginsActivity.this, "Plugin download failed!", Toast.LENGTH_LONG).show();
                }
                hideLoading();
            }
        });

        // Initialize fragment instances before attaching the pager. ViewPager2 can
        // request its initial fragments as soon as its adapter is installed.
        viewPager.setAdapter(new ViewPagerFragmentStateAdapter(this));
        new TabLayoutMediator(tabLayout, viewPager, true, false,
                (tab, position) -> tab.setText(titles[position])).attach();
    }

    void openStore() {
        viewPager.setCurrentItem(1, false);
    }

    private void refreshCatalog() {
        if (catalogFetchInProgress || isFinishing() || isDestroyed()) return;
        catalogFetchInProgress = true;
        findViewById(R.id.plugins_catalog_progress).setVisibility(View.VISIBLE);
        findViewById(R.id.plugins_refresh).setEnabled(false);
        pluginsManager.fetchPluginAppsFile(true, success -> {
            catalogFetchInProgress = false;
            if (isFinishing() || isDestroyed()) return;
            findViewById(R.id.plugins_catalog_progress).setVisibility(View.INVISIBLE);
            findViewById(R.id.plugins_refresh).setEnabled(true);
            activePluginsStore().updatePluginStore();
            activeInstalledPlugins().checkPluginsUpdates();
            if (!success) Toast.makeText(this, R.string.plugin_store_refresh_failed, Toast.LENGTH_LONG).show();
        });
    }

    @Override
    protected void onDestroy() {
        hideLoading();
        super.onDestroy();
    }

    void configureInstalledPlugins(InstalledPlugins fragment) {
        fragment.configure(getApplicationContext(), this, getPluginsManager());
        fragment.setInstalledPluginsActionListener(new InstalledPlugins.InstalledPluginsActionListener() {
            @Override
            public void onPluginUpdateStarted() {
                showLoading("Updating plugin, please wait...", new Utils(PluginsActivity.this));
            }

            @Override
            public void onPluginUninstallStarted() {
                showLoading("Uninstalling plugin, please wait...", new Utils(PluginsActivity.this));
            }

            @Override
            public void onPluginUninstalled() {
                activePluginsStore().updatePluginStore();
                hideLoading();
            }
        });
    }

    void configurePluginsStore(PluginsStore fragment) {
        fragment.configure(getApplicationContext(), this, getPluginsManager());
        fragment.setPluginsStoreActionListener(() ->
                showLoading("Installing plugin, please wait...", new Utils(PluginsActivity.this)));
    }

    private PluginsStore activePluginsStore() {
        Fragment fragment = getSupportFragmentManager().findFragmentByTag("f1");
        return fragment instanceof PluginsStore ? (PluginsStore) fragment : pluginsStore;
    }

    private InstalledPlugins activeInstalledPlugins() {
        Fragment fragment = getSupportFragmentManager().findFragmentByTag("f0");
        return fragment instanceof InstalledPlugins ? (InstalledPlugins) fragment : installedPlugins;
    }

    public PluginsManager getPluginsManager() {
        if (pluginsManager == null) {
            Utils utils = new Utils(this);
            pluginsManager = new PluginsManager(this, this, utils);
        }
        return pluginsManager;
    }

    public class ViewPagerFragmentStateAdapter extends FragmentStateAdapter {

        public ViewPagerFragmentStateAdapter(@NonNull FragmentActivity fragmentActivity) {
            super(fragmentActivity);
        }

        @NonNull
        @Override
        public Fragment createFragment(int position) {
            switch (position) {
                case 0:
                    return installedPlugins;
                case 1:
                    return pluginsStore;
            }
            return installedPlugins;
        }

        @Override
        public int getItemCount() {
            return titles.length;
        }
    }

    void showLoading(String msg, Utils utils) {
        if(progress != null && progress.isShowing()) {
            return;
        }
        progress = new ProgressDialog(PluginsActivity.this);
        try {
            progress.setTitle(utils.getSpannableFont(getResources().getString(R.string.app_name)));
            progress.setMessage(utils.getSpannableFont(msg));
            progress.setProgressStyle(ProgressDialog.STYLE_SPINNER);
            progress.setIndeterminate(true);
            progress.setProgress(0);
            progress.setCancelable(false);
            progress.setCanceledOnTouchOutside(false);
            progress.show();
        } catch (Exception e) {
            Log.d(Constants.LOG_TAG, e.toString());
        }
    }

    void hideLoading() {
        if(progress != null && progress.isShowing()) {
            progress.cancel();
        }
    }
}
