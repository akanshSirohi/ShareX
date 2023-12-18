package com.akansh.plugins;

import android.app.Activity;
import android.content.Context;
import android.graphics.text.LineBreaker;
import android.os.Build;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.akansh.fileserversuit.R;
import com.akansh.fileserversuit.Utils;

import java.util.ArrayList;
import java.util.Iterator;

public class InstalledPluginsAdapter extends RecyclerView.Adapter<InstalledPluginsAdapter.ItemViewHolder> {

    Context ctx;
    LayoutInflater inflater;
    ArrayList<Plugin> pluginArrayList;

    InstalledPluginsActionListener listener;

    public interface InstalledPluginsActionListener {
        void onUninstallPlugin(Plugin plugin);
    }


    public InstalledPluginsAdapter(Context ctx, ArrayList<Plugin> pluginArrayList) {
        inflater = LayoutInflater.from(ctx);
        this.ctx = ctx;
        this.pluginArrayList = pluginArrayList;
    }

    public class ItemViewHolder extends RecyclerView.ViewHolder {

        public TextView plugin_title, plugin_version, plugin_author, plugin_description;
        public Button plugin_uninstall_btn, plugin_update_btn;

        public ItemViewHolder(@NonNull View itemView) {
            super(itemView);
            plugin_title = itemView.findViewById(R.id.plugin_title);
            plugin_version = itemView.findViewById(R.id.plugin_version);
            plugin_author = itemView.findViewById(R.id.plugin_author);
            plugin_description = itemView.findViewById(R.id.plugin_description);
            plugin_update_btn = itemView.findViewById(R.id.plugin_update_btn);
            plugin_uninstall_btn = itemView.findViewById(R.id.plugin_uninstall_btn);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                plugin_description.setJustificationMode(LineBreaker.JUSTIFICATION_MODE_INTER_WORD);
            }

            plugin_update_btn.setOnClickListener(v -> {
                Plugin plugin = pluginArrayList.get(getAdapterPosition());
                Toast.makeText(ctx, plugin.getPlugin_name(), Toast.LENGTH_LONG).show();
            });

            plugin_uninstall_btn.setOnClickListener(v -> {
                if(listener != null) {
                    listener.onUninstallPlugin(pluginArrayList.get(getAdapterPosition()));
                }
            });

            plugin_description.setOnClickListener(v -> {
                Plugin plugin = pluginArrayList.get(getAdapterPosition());
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
        View v = inflater.inflate(R.layout.plugin_list_item, parent, false);
        return new InstalledPluginsAdapter.ItemViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ItemViewHolder holder, int position) {
        Plugin plugin = this.pluginArrayList.get(position);
        holder.plugin_title.setText(plugin.getPlugin_name());
        holder.plugin_author.setText(plugin.getPlugin_author());
        holder.plugin_version.setText(plugin.getPlugin_version());
        holder.plugin_description.setText(plugin.getPlugin_description());
        holder.plugin_description.setText(plugin.getPlugin_description());

        if(plugin.getPlugin_description().length() > 300) {
            holder.plugin_description.setEllipsize(TextUtils.TruncateAt.END);
            holder.plugin_description.setMaxLines(3);
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

    @Override
    public int getItemCount() {
        return pluginArrayList.size();
    }

    public void setListener(InstalledPluginsActionListener listener) {
        this.listener = listener;
    }
}