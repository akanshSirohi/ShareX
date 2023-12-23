package com.akansh.plugins.ui;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;
import android.widget.Toast;

import com.akansh.fileserversuit.Constants;
import com.akansh.fileserversuit.R;
import com.akansh.fileserversuit.Utils;
import com.akansh.plugins.PluginInstallStatus;
import com.akansh.plugins.PluginsManager;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

import java.io.File;

public class PluginsActivity extends AppCompatActivity {

    TabLayout tabLayout;
    ViewPager2 viewPager;

    private String[] titles = new String[]{"Installed","Store"};

    InstalledPlugins installedPlugins;
    PluginsStore pluginsStore;

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
        pluginsStore = new PluginsStore(getApplicationContext(), this, pluginsManager);
        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
            if(position == 0) {
                installedPlugins.checkPluginsEmpty();
            }
            super.onPageSelected(position);
            }
        });

        ImageButton check_updates_btn = findViewById(R.id.check_updates_btn);

        pluginsManager.setPluginsManagerListener(new PluginsManager.PluginsManagerListener() {
            @Override
            public void onServerPluginsFetchUpdate(boolean res) {
                if(res) {
                    if(!installedPlugins.isPluginsEmpty()) {
                        installedPlugins.checkPluginsUpdates();
                    }
                }
            }

            @Override
            public void onPluginUpdateDownload(boolean res, String packageName) {
                if(res) {
                    PluginInstallStatus pluginInstallStatus = pluginsManager.installPlugin(packageName + ".zip");
                    Toast.makeText(PluginsActivity.this, pluginInstallStatus.message, Toast.LENGTH_LONG).show();
                    if(!pluginInstallStatus.error) {
                        installedPlugins.updatePluginsList();
                    }
                }
            }
        });

        check_updates_btn.setOnClickListener(v -> {
            if(!installedPlugins.isPluginsEmpty()) {
                pluginsManager.fetchPluginsFile();
            }else{
                Toast.makeText(PluginsActivity.this, "No installed plugins found!", Toast.LENGTH_LONG).show();
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
}