package com.akansh.plugins.ui;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.akansh.fileserversuit.common.Constants;
import com.akansh.fileserversuit.R;
import com.akansh.plugins.common.InstallStatus;
import com.akansh.plugins.common.Plugin;
import com.akansh.plugins.PluginsDBHelper;
import com.akansh.plugins.PluginsManager;
import com.akansh.plugins.StorePluginsAdapter;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;

public class PluginsStore extends Fragment {

    Context ctx;
    Activity activity;
    PluginsManager pluginsManager;
    StorePluginsAdapter storePluginsAdapter;
    ArrayList<Plugin> storePluginsListItems = new ArrayList<>();
    PluginsStoreActionListener pluginsStoreActionListener;

    public PluginsStore(Context ctx, Activity activity, PluginsManager pluginsManager) {
        this.ctx = ctx;
        this.activity = activity;
        this.pluginsManager = pluginsManager;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    public void setPluginsStoreActionListener(PluginsStoreActionListener pluginsStoreActionListener) {
        this.pluginsStoreActionListener = pluginsStoreActionListener;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.plugins_store_layout, container, false);
        RecyclerView storePluginsList = view.findViewById(R.id.storePluginsList);
        storePluginsList.setLayoutManager(new LinearLayoutManager(ctx));
        File filePath = new File(pluginsManager.getPlugins_dir(), Constants.APPS_CONFIG);
        if (filePath.exists()) {
            storePluginsListItems = getPluginsListFromJson(filePath);
        }
        storePluginsAdapter = new StorePluginsAdapter(ctx, storePluginsListItems);
        storePluginsAdapter.setStorePluginsActionListener(package_name -> {
            pluginsManager.downloadPlugin(package_name, InstallStatus.INSTALL);
            if(pluginsStoreActionListener != null) {
                pluginsStoreActionListener.onPluginInstallStarted();
            }
        });
        storePluginsList.setAdapter(storePluginsAdapter);
        return view;
    }

    public ArrayList<Plugin> getPluginsListFromJson(File filePath) {
        if(filePath.exists()) {
            StringBuilder stringBuilder = new StringBuilder();
            PluginsDBHelper pluginsDBHelper = new PluginsDBHelper(ctx);
            ArrayList<String> installed_packages = pluginsDBHelper.getInstalledPluginsPackages();
            ArrayList<Plugin> storeListingPlugins = new ArrayList<>();
            String line;
            BufferedReader in;
            try {
                in = new BufferedReader(new FileReader(filePath));
                while ((line = in.readLine()) != null) stringBuilder.append(line);
                String apps_json = stringBuilder.toString();
                JSONArray jsonArray = new JSONArray(apps_json);
                for (int i = 0; i < jsonArray.length(); i++) {
                    JSONObject jsonObject = jsonArray.getJSONObject(i);
                    String name = jsonObject.getString("name");
                    String description = jsonObject.getString("description");
                    String package_name = jsonObject.getString("package");
                    String author = jsonObject.getString("author");
                    String version = jsonObject.getString("version");
                    int version_code = jsonObject.getInt("versionCode");
                    Plugin store_plugin = new Plugin(null, name, package_name, description, author, version, version_code);
                    store_plugin.setPlugin_installed(installed_packages.contains(package_name));
                    storeListingPlugins.add(store_plugin);
                }
                return storeListingPlugins;
            } catch (Exception e) {
                return null;
            }
        }else{
            return null;
        }
    }

    public void updatePluginStore() {
        File filePath = new File(pluginsManager.getPlugins_dir(), Constants.APPS_CONFIG);
        storePluginsListItems = getPluginsListFromJson(filePath);
        storePluginsAdapter.updateStorePluginsList(storePluginsListItems);
    }

    public interface PluginsStoreActionListener {
        void onPluginInstallStarted();
    }
}
