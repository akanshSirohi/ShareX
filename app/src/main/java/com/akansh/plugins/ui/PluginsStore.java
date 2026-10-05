package com.akansh.plugins.ui;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.util.Log;
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
    RecyclerView storePluginsList;

    public PluginsStore() {
        // Required by FragmentManager when restoring this screen.
    }

    public PluginsStore(Context ctx, Activity activity, PluginsManager pluginsManager) {
        configure(ctx, activity, pluginsManager);
    }

    void configure(Context ctx, Activity activity, PluginsManager pluginsManager) {
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
        ensureDependencies();
        View view = inflater.inflate(R.layout.plugins_store_layout, container, false);
        storePluginsList = view.findViewById(R.id.storePluginsList);
        View emptyState = view.findViewById(R.id.store_empty_state);
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
        emptyState.setVisibility(storePluginsListItems.isEmpty() ? View.VISIBLE : View.GONE);
        return view;
    }

    private void ensureDependencies() {
        if (getActivity() != null && ctx == null) {
            activity = getActivity();
            ctx = activity.getApplicationContext();
            ((PluginsActivity) activity).configurePluginsStore(this);
        }
    }

    public ArrayList<Plugin> getPluginsListFromJson(File filePath) {
        if(filePath.exists()) {
            StringBuilder stringBuilder = new StringBuilder();
            ArrayList<Plugin> storeListingPlugins = new ArrayList<>();
            String line;
            try (PluginsDBHelper pluginsDBHelper = new PluginsDBHelper(ctx);
                 BufferedReader in = new BufferedReader(new FileReader(filePath))) {
                ArrayList<String> installed_packages = pluginsDBHelper.getInstalledPluginsPackages();
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
                Log.e(Constants.LOG_TAG, "Unable to read plugin catalog", e);
            }
        }
        return new ArrayList<>();
    }

    public void updatePluginStore() {
        ensureDependencies();
        if (pluginsManager == null) return;
        File filePath = new File(pluginsManager.getPlugins_dir(), Constants.APPS_CONFIG);
        storePluginsListItems = getPluginsListFromJson(filePath);
        if (getView() != null && storePluginsAdapter != null) {
            storePluginsAdapter.updateStorePluginsList(storePluginsListItems);
            getView().findViewById(R.id.store_empty_state)
                    .setVisibility(storePluginsListItems.isEmpty() ? View.VISIBLE : View.GONE);
        }
    }

    @Override
    public void onDestroyView() {
        storePluginsList = null;
        storePluginsAdapter = null;
        super.onDestroyView();
    }

    public interface PluginsStoreActionListener {
        void onPluginInstallStarted();
    }
}
