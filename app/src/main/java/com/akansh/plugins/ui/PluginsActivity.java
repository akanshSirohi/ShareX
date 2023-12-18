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

import com.akansh.fileserversuit.Constants;
import com.akansh.fileserversuit.R;
import com.akansh.fileserversuit.Utils;
import com.akansh.plugins.PluginInstallStatus;
import com.akansh.plugins.PluginsManager;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

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

        installedPlugins = new InstalledPlugins(getApplicationContext(), this);
        pluginsStore = new PluginsStore();
        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                if(position == 0) {
                    installedPlugins.checkPluginsEmpty();
                }
                super.onPageSelected(position);
            }
        });

        Utils utils = new Utils(this);

        // Setup Plugins
//        PluginsManager pluginsManager = new PluginsManager(this, this, utils);
//        pluginsManager.init();
//        PluginInstallStatus pluginInstallStatus = pluginsManager.installPlugin("sharex.test.plugin.zip");
//        Log.d(Constants.LOG_TAG,"Plugin Message: "+pluginInstallStatus.message);
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