package com.akansh.plugins.ui;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.akansh.fileserversuit.R;
import com.akansh.fileserversuit.Utils;
import com.akansh.plugins.InstalledPluginsAdapter;
import com.akansh.plugins.Plugin;
import com.akansh.plugins.PluginsDBHelper;
import com.akansh.plugins.PluginsManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.HashMap;

public class InstalledPlugins extends Fragment {

    Context ctx;
    Activity activity;
    ConstraintLayout empty_plugins_list;
    InstalledPluginsAdapter installedPluginsAdapter;
    PluginsManager pluginsManager;

    HashMap<String, Integer> apps_config =  new HashMap<>();

    public InstalledPlugins(Context ctx, Activity activity, PluginsManager pluginsManager) {
        this.ctx = ctx;
        this.activity = activity;
        this.pluginsManager = pluginsManager;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.plugins_installed_layout, container, false);
        RecyclerView pluginsListView = view.findViewById(R.id.installedPluginsList);
        empty_plugins_list = view.findViewById(R.id.empty_plugins_list);
        pluginsListView.setLayoutManager(new LinearLayoutManager(ctx));
        PluginsDBHelper pluginsDBHelper = new PluginsDBHelper(ctx);
        ArrayList<Plugin> installedPluginsList = pluginsDBHelper.getInstalledPlugins();
        installedPluginsAdapter = new InstalledPluginsAdapter(ctx, installedPluginsList);
        installedPluginsAdapter.setApps_config(apps_config);
        installedPluginsAdapter.setListener(new InstalledPluginsAdapter.InstalledPluginsActionListener() {
            @Override
            public void onUninstallPlugin(Plugin plugin) {
                PluginsManager pluginsManager = new PluginsManager(activity, ctx, new Utils(ctx));
                boolean res = pluginsManager.uninstallPlugin(plugin.getPlugin_uid());
                if(res) {
                    installedPluginsAdapter.removeItem(plugin.getPlugin_uid());
                    if(installedPluginsAdapter.getItemCount() == 0) {
                        empty_plugins_list.setVisibility(View.VISIBLE);
                    }
                }
            }

            @Override
            public void onUpdatePlugin(Plugin plugin) {
                // Update Plugin Code...
            }
        });
        pluginsListView.setAdapter(installedPluginsAdapter);
        if(installedPluginsList.size() == 0) {
            empty_plugins_list.setVisibility(View.VISIBLE);
        }
        return view;
    }

    public void checkPluginsEmpty() {
        if(installedPluginsAdapter.getItemCount() == 0) {
            empty_plugins_list.setVisibility(View.VISIBLE);
        }else{
            empty_plugins_list.setVisibility(View.GONE);
        }
    }

    public void checkPluginsUpdates(File filePath) {
        StringBuilder stringBuilder = new StringBuilder();
        String line;
        BufferedReader in;
        try {
            apps_config.clear();
            in = new BufferedReader(new FileReader(filePath));
            while ((line = in.readLine()) != null) stringBuilder.append(line);
            String apps_json = stringBuilder.toString();
            JSONArray jsonArray = new JSONArray(apps_json);
            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject jsonObject = jsonArray.getJSONObject(i);
                String package_name = jsonObject.getString("package");
                int version_code = jsonObject.getInt("versionCode");
                apps_config.put(package_name, version_code);
            }
            installedPluginsAdapter.setApps_config(apps_config);
            installedPluginsAdapter.notifyDataSetChanged();
        }catch (Exception e) {}
    }
}
