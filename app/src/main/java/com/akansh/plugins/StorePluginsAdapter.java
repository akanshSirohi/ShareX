package com.akansh.plugins;

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
import com.akansh.plugins.common.Plugin;

import java.util.ArrayList;

public class StorePluginsAdapter extends RecyclerView.Adapter<StorePluginsAdapter.ItemViewHolder> {

    Context ctx;
    ArrayList<Plugin> pluginArrayList;
    LayoutInflater inflater;
    StorePluginsActionListener storePluginsActionListener;

    public StorePluginsAdapter(Context ctx, ArrayList<Plugin> pluginArrayList) {
        this.ctx = ctx;
        inflater = LayoutInflater.from(ctx);
        this.pluginArrayList = pluginArrayList;
    }

    @NonNull
    @Override
    public ItemViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = inflater.inflate(R.layout.store_list_item, parent, false);
        return new StorePluginsAdapter.ItemViewHolder(v);
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

        if(plugin.isPlugin_installed()) {
            holder.plugin_install_btn.setText("Installed");
            holder.plugin_install_btn.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_installed, 0,0,0);
        }else{
            holder.plugin_install_btn.setText("Install");
            holder.plugin_install_btn.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_install, 0,0,0);
        }
    }

    public void updateStorePluginsList(ArrayList<Plugin> updatedPluginsList) {
        this.pluginArrayList = updatedPluginsList;
        notifyDataSetChanged();
    }

    @Override
    public int getItemCount() {
        return pluginArrayList.size();
    }

    public class ItemViewHolder extends RecyclerView.ViewHolder {

        public TextView plugin_title, plugin_version, plugin_author, plugin_description;
        public Button plugin_install_btn;

        public ItemViewHolder(@NonNull View itemView) {
            super(itemView);
            plugin_title = itemView.findViewById(R.id.store_title);
            plugin_version = itemView.findViewById(R.id.store_version);
            plugin_author = itemView.findViewById(R.id.store_author);
            plugin_description = itemView.findViewById(R.id.store_description);
            plugin_install_btn = itemView.findViewById(R.id.store_install_btn);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                plugin_description.setJustificationMode(LineBreaker.JUSTIFICATION_MODE_INTER_WORD);
            }

            plugin_install_btn.setOnClickListener(v -> {
                if(storePluginsActionListener != null) {
                    Plugin p = pluginArrayList.get(getAdapterPosition());
                    if(!p.isPlugin_installed()) {
                        storePluginsActionListener.onPluginInstall(p.getPlugin_package_name());
                    }else{
                        Toast.makeText(ctx, "Plugin Already Installed!", Toast.LENGTH_LONG).show();
                    }
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

    public void setStorePluginsActionListener(StorePluginsActionListener storePluginsActionListener) {
        this.storePluginsActionListener = storePluginsActionListener;
    }

    public interface StorePluginsActionListener {
        void onPluginInstall(String package_name);
    }

}
