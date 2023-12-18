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
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.akansh.fileserversuit.Constants;
import com.akansh.fileserversuit.R;
import com.akansh.fileserversuit.Utils;
import com.akansh.plugins.InstalledPluginsAdapter;
import com.akansh.plugins.Plugin;
import com.akansh.plugins.PluginsDBHelper;
import com.akansh.plugins.PluginsManager;

import java.util.ArrayList;
import java.util.Iterator;

public class InstalledPlugins extends Fragment {

    Context ctx;
    Activity activity;

    ConstraintLayout empty_plugins_list;

    InstalledPluginsAdapter installedPluginsAdapter;

    public InstalledPlugins(Context ctx, Activity activity) {
        this.ctx = ctx;
        this.activity = activity;
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
        installedPluginsAdapter.setListener(plugin -> {
            PluginsManager pluginsManager = new PluginsManager(activity, ctx, new Utils(ctx));
            boolean res = pluginsManager.uninstallPlugin(plugin.getPlugin_uid());
            if(res) {
                installedPluginsAdapter.removeItem(plugin.getPlugin_uid());
                if(installedPluginsAdapter.getItemCount() == 0) {
                    empty_plugins_list.setVisibility(View.VISIBLE);
                }
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
}
