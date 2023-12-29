package com.akansh.plugins.ui;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.akansh.fileserversuit.common.Constants;
import com.akansh.fileserversuit.R;
import com.akansh.fileserversuit.common.Utils;
import com.akansh.fileserversuit.server.ServerService;
import com.akansh.fileserversuit.ui.MainActivity;
import com.akansh.plugins.common.InstallStatus;
import com.akansh.plugins.InstalledPluginsAdapter;
import com.akansh.plugins.common.Plugin;
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
    ArrayList<Plugin> installedPluginsList;
    PluginsDBHelper pluginsDBHelper;
    InstalledPluginsActionListener installedPluginsActionListener;

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
        pluginsDBHelper = new PluginsDBHelper(ctx);
        installedPluginsList = pluginsDBHelper.getInstalledPlugins();
        installedPluginsAdapter = new InstalledPluginsAdapter(ctx, installedPluginsList);
        installedPluginsAdapter.setListener(new InstalledPluginsAdapter.InstalledPluginsActionListener() {
            @Override
            public void onUninstallPlugin(Plugin plugin) {
                if(installedPluginsActionListener != null) {
                    installedPluginsActionListener.onPluginUninstallStarted();
                }
                PluginsManager pluginsManager = new PluginsManager(activity, ctx, new Utils(ctx));
                boolean res = pluginsManager.uninstallPlugin(plugin.getPlugin_uid());
                if(res) {
                    installedPluginsAdapter.removeItem(plugin.getPlugin_uid());
                    if(installedPluginsAdapter.getItemCount() == 0) {
                        empty_plugins_list.setVisibility(View.VISIBLE);
                        if(installedPluginsActionListener != null) {
                            installedPluginsActionListener.onPluginUninstalled();
                        }
                    }
                }
            }

            @Override
            public void onUpdatePlugin(Plugin plugin) {
                pluginsManager.downloadPlugin(plugin.getPlugin_package_name(), InstallStatus.UPDATE);
                if(installedPluginsActionListener != null) {
                    installedPluginsActionListener.onPluginUpdateStarted();
                }
            }

            @Override
            public void onPluginStatusChange(String uid, boolean status) {
                pluginsDBHelper.changeStatus(uid, status);
            }

            @Override
            public void onPluginExternalLinkClick(String uid) {
                Utils utils = new Utils(ctx);
                if(utils.isServiceRunning(ServerService.class)) {
                    Log.d(Constants.LOG_TAG, utils.loadString(Constants.SERVER_URL) + "/SharexApp/" + uid);
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(utils.loadString(Constants.SERVER_URL) + "/SharexApp/" + uid));
                    if (intent.resolveActivity(ctx.getPackageManager()) != null) {
                        startActivity(intent);
                    } else {
                        Toast.makeText(ctx, "No browser available to open the url!", Toast.LENGTH_LONG).show();
                    }
                }else{
                    Toast.makeText(ctx, "Start ShareX first!", Toast.LENGTH_LONG).show();
                }
            }
        });
        pluginsListView.setAdapter(installedPluginsAdapter);
        if(installedPluginsList.size() == 0) {
            empty_plugins_list.setVisibility(View.VISIBLE);
        }else{
            final Handler handler = new Handler();
            handler.postDelayed(() -> activity.runOnUiThread(this::checkPluginsUpdates), 3000);
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

    public void checkPluginsUpdates() {
        File filePath = new File(pluginsManager.getPlugins_dir(), Constants.APPS_CONFIG);
        if(filePath.exists()) {
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
            } catch (Exception e) {
            }
        }
    }

    public void updatePluginsList() {
        installedPluginsList = pluginsDBHelper.getInstalledPlugins();
        installedPluginsAdapter.updateInstalledPluginsList(installedPluginsList);
    }

    public boolean isPluginsEmpty() {
        return installedPluginsList.isEmpty();
    }

    public void setInstalledPluginsActionListener(InstalledPluginsActionListener installedPluginsActionListener) {
        this.installedPluginsActionListener = installedPluginsActionListener;
    }

    public interface InstalledPluginsActionListener {
        void onPluginUpdateStarted();
        void onPluginUninstalled();
        void onPluginUninstallStarted();
    }
}
