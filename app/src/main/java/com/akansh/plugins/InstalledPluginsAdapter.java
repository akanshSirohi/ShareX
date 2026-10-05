package com.akansh.plugins;

import android.content.Context;
import android.graphics.text.LineBreaker;
import android.os.Build;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.Switch;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.AppCompatImageButton;
import androidx.recyclerview.widget.RecyclerView;

import com.akansh.fileserversuit.R;
import com.akansh.plugins.common.Plugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;

public class InstalledPluginsAdapter extends RecyclerView.Adapter<InstalledPluginsAdapter.ItemViewHolder> {

    Context ctx;
    ArrayList<Plugin> pluginArrayList;

    InstalledPluginsActionListener listener;

    HashMap<String, Integer> apps_config =  new HashMap<>();

    public void setApps_config(HashMap<String, Integer> apps_config) {
        this.apps_config = apps_config;
    }

    public InstalledPluginsAdapter(Context ctx, ArrayList<Plugin> pluginArrayList) {
        this.ctx = ctx;
        this.pluginArrayList = pluginArrayList;
    }

    public class ItemViewHolder extends RecyclerView.ViewHolder {

        public TextView plugin_title, plugin_version, plugin_author, plugin_description;
        public Button plugin_uninstall_btn, plugin_update_btn;
        public AppCompatImageButton plugin_external_link;
        private Switch plugin_enabled;

        public ItemViewHolder(@NonNull View itemView) {
            super(itemView);
            plugin_title = itemView.findViewById(R.id.plugin_title);
            plugin_version = itemView.findViewById(R.id.plugin_version);
            plugin_author = itemView.findViewById(R.id.plugin_author);
            plugin_description = itemView.findViewById(R.id.plugin_description);
            plugin_update_btn = itemView.findViewById(R.id.plugin_update_btn);
            plugin_uninstall_btn = itemView.findViewById(R.id.plugin_uninstall_btn);
            plugin_enabled = itemView.findViewById(R.id.plugin_enabled);
            plugin_external_link = itemView.findViewById(R.id.plugin_external_link);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                plugin_description.setJustificationMode(LineBreaker.JUSTIFICATION_MODE_INTER_WORD);
            }

            plugin_enabled.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if(listener != null) {
                    int position = getBindingAdapterPosition();
                    if (position == RecyclerView.NO_POSITION) return;
                    listener.onPluginStatusChange(pluginArrayList.get(position).getPlugin_uid(),isChecked);
                }
            });

            plugin_update_btn.setOnClickListener(v -> {
                if(listener != null) {
                    int position = getBindingAdapterPosition();
                    if (position != RecyclerView.NO_POSITION) listener.onUpdatePlugin(pluginArrayList.get(position));
                }
            });

            plugin_uninstall_btn.setOnClickListener(v -> {
                if(listener != null) {
                    int position = getBindingAdapterPosition();
                    if (position != RecyclerView.NO_POSITION) listener.onUninstallPlugin(pluginArrayList.get(position));
                }
            });

            plugin_external_link.setOnClickListener(v -> {
                int position = getBindingAdapterPosition();
                if (listener != null && position != RecyclerView.NO_POSITION) {
                    listener.onPluginExternalLinkClick(pluginArrayList.get(position).getPlugin_uid());
                }
            });

            plugin_description.setOnClickListener(v -> {
                int position = getBindingAdapterPosition();
                if (position == RecyclerView.NO_POSITION) return;
                Plugin plugin = pluginArrayList.get(position);
                if(plugin.getPlugin_description().length() > 300) {
                    if(plugin_description.getEllipsize() == TextUtils.TruncateAt.END) {
                        plugin_description.setEllipsize(null);
                        plugin_description.setMaxLines(100);
                    }else{
                        plugin_description.setEllipsize(TextUtils.TruncateAt.END);
                        plugin_description.setMaxLines(3);
                    }
                }
            });
        }
    }

    @NonNull
    @Override
    public ItemViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.plugin_list_item, parent, false);
        return new InstalledPluginsAdapter.ItemViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ItemViewHolder holder, int position) {
        Plugin plugin = this.pluginArrayList.get(position);
        holder.plugin_title.setText(plugin.getPlugin_name());
        holder.plugin_author.setText(plugin.getPlugin_author());
        holder.plugin_version.setText(plugin.getPlugin_version());
        holder.plugin_description.setText(plugin.getPlugin_description());
        holder.plugin_enabled.setChecked(plugin.isPlugin_enabled());

        holder.plugin_description.setEllipsize(TextUtils.TruncateAt.END);
        holder.plugin_description.setMaxLines(3);

        if(apps_config.containsKey(plugin.getPlugin_package_name()) && apps_config.get(plugin.getPlugin_package_name()) != plugin.getPlugin_version_code()) {
            holder.plugin_update_btn.setVisibility(View.VISIBLE);
        }else{
            holder.plugin_update_btn.setVisibility(View.GONE);
        }
    }

    public void removeItem(String uid) {
        Iterator<Plugin> iterator = pluginArrayList.iterator();
        while (iterator.hasNext()) {
            Plugin obj = iterator.next();
            if (obj.getPlugin_uid().equals(uid)) {
                iterator.remove();
            }
        }
        notifyDataSetChanged();
    }

    public void updateInstalledPluginsList(ArrayList<Plugin> updatedPluginsList) {
        this.pluginArrayList = updatedPluginsList;
        notifyDataSetChanged();
    }

    @Override
    public int getItemCount() {
        return pluginArrayList.size();
    }

    public void setListener(InstalledPluginsActionListener listener) {
        this.listener = listener;
    }

    public interface InstalledPluginsActionListener {
        void onUninstallPlugin(Plugin plugin);
        void onUpdatePlugin(Plugin plugin);
        void onPluginStatusChange(String uid, boolean status);
        void onPluginExternalLinkClick(String uid);
    }
}
