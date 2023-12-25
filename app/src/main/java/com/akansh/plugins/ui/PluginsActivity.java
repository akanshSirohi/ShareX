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
import android.widget.Toast;

import com.akansh.fileserversuit.common.Constants;
import com.akansh.fileserversuit.R;
import com.akansh.fileserversuit.common.Utils;
import com.akansh.plugins.PluginInstallStatus;
import com.akansh.plugins.common.PluginsManager;
import com.akansh.plugins.PluginsManagerPluginStatusListener;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

public class PluginsActivity extends AppCompatActivity {

    TabLayout tabLayout;
    ViewPager2 viewPager;

    private String[] titles = new String[]{"Installed","Store"};

    InstalledPlugins installedPlugins;
    PluginsStore pluginsStore;
    ProgressDialog progress;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_plugins);
        tabLayout = findViewById(R.id.tabLayout);
        viewPager = findViewById(R.id.viewPager);
        viewPager.setAdapter(new ViewPagerFragmentStateAdapter(this));
        new TabLayoutMediator(tabLayout, viewPager, (tab, position) -> tab.setText(titles[position])).attach();

        // Setup Plugins
        Utils utils = new Utils(this);
        PluginsManager pluginsManager = new PluginsManager(this, this, utils);
        installedPlugins = new InstalledPlugins(getApplicationContext(), this, pluginsManager);
        installedPlugins.setInstalledPluginsActionListener(new InstalledPlugins.InstalledPluginsActionListener() {
            @Override
            public void onPluginUpdateStarted() {
                showLoading("Updating plugin, please wait...", utils);
            }

            @Override
            public void onPluginUninstallStarted() {
                showLoading("Uninstalling plugin, please wait...", utils);
            }

            @Override
            public void onPluginUninstalled() {
                pluginsStore.updatePluginStore();
                hideLoading();
            }
        });
        pluginsStore = new PluginsStore(getApplicationContext(), this, pluginsManager);
        pluginsStore.setPluginsStoreActionListener(new PluginsStore.PluginsStoreActionListener() {
            @Override
            public void onPluginInstallStarted() {
                showLoading("Installing plugin, please wait...", utils);
            }
        });
        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
            if(position == 0) {
                installedPlugins.checkPluginsEmpty();
            }
            super.onPageSelected(position);
            }
        });

        pluginsManager.setPluginsManagerPluginStatusListener(new PluginsManagerPluginStatusListener() {
            @Override
            public void onPluginUpdateDownload(boolean res, String packageName) {
                if(res) {
                    PluginInstallStatus pluginInstallStatus = pluginsManager.installPlugin(packageName);
                    Toast.makeText(PluginsActivity.this, pluginInstallStatus.message, Toast.LENGTH_LONG).show();
                    if(!pluginInstallStatus.error) {
                        installedPlugins.updatePluginsList();
                        pluginsStore.updatePluginStore();
                    }
                }
                hideLoading();
            }

            @Override
            public void onNewPluginDownload(boolean res, String packageName) {
                if(res) {
                    PluginInstallStatus pluginInstallStatus = pluginsManager.installPlugin(packageName);
                    Toast.makeText(PluginsActivity.this, pluginInstallStatus.message, Toast.LENGTH_LONG).show();
                    if(!pluginInstallStatus.error) {
                        installedPlugins.updatePluginsList();
                        pluginsStore.updatePluginStore();
                    }
                }else{
                    Toast.makeText(PluginsActivity.this, "Plugin download failed!", Toast.LENGTH_LONG).show();
                }
                hideLoading();
            }
        });
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