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

import com.akansh.fileserversuit.R;
import com.akansh.plugins.PluginsManager;

public class PluginsStore extends Fragment {

    Context ctx;
    Activity activity;
    PluginsManager pluginsManager;

    public PluginsStore(Context ctx, Activity activity, PluginsManager pluginsManager) {
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
        return inflater.inflate(R.layout.plugins_store_layout, container, false);
    }
}
