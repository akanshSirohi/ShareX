package com.akansh.plugins;

import android.app.Activity;
import android.content.Context;
import android.util.Log;

import com.akansh.fileserversuit.Constants;
import com.akansh.fileserversuit.Utils;
import com.akansh.fileserversuit.ZipUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;

import org.json.JSONException;
import org.json.JSONObject;

public class PluginsManager {
    Activity activity;
    Context ctx;
    Utils utils;
    File plugins_dir;

    public PluginsManager(Activity activity, Context ctx, Utils utils) {
        this.activity = activity;
        this.ctx = ctx;
        this.utils = utils;
        plugins_dir=new File(String.format("/data/data/%s/plugins",activity.getPackageName()));
    }

    public void init() {
        if(!plugins_dir.exists()) {
            plugins_dir.mkdir();
        }
    }

    // Temp Function, Will Remove Later
    public boolean copyAssetFile(String assetFilePath, String destinationFilePath) {
        try {
            InputStream in = ctx.getAssets().open(assetFilePath);
            OutputStream out = new FileOutputStream(destinationFilePath);
            byte[] buf = new byte[1024];
            int len;
            while ((len = in.read(buf)) > 0)
                out.write(buf, 0, len);
            in.close();
            out.close();
            return true;
        }catch (Exception e) {
            return false;
        }
    }

    public PluginInstallStatus installPlugin(String plugin_file) {
        ZipUtils zipUtils = new ZipUtils();
        try (PluginsDBHelper pluginsDBHelper = new PluginsDBHelper(ctx)) {
            File tmp_plugin_zip_dest = new File(plugins_dir, plugin_file);
            boolean res = copyAssetFile(plugin_file, tmp_plugin_zip_dest.getAbsolutePath());
            if(res) {
                String config_string = zipUtils.readFileFromZip("config.json",tmp_plugin_zip_dest.getAbsolutePath());
                if(config_string != null) {
                    try {
                        JSONObject pluginConfigObject = new JSONObject(config_string);
                        String package_name = pluginConfigObject.getString("package");
                        String installed_plugin_uid = pluginsDBHelper.getPluginUIDByPackageName(package_name);
                        int installed_plugin_version_code = pluginsDBHelper.getPluginVersionCodeByPackageName(package_name);

                        // Read All Config Data From JSON
                        String new_plugin_name = pluginConfigObject.getString("name");
                        String new_plugin_description = pluginConfigObject.getString("description");
                        String new_plugin_author = pluginConfigObject.getString("author");
                        String new_plugin_version = pluginConfigObject.getString("version");
                        int new_plugin_version_code = pluginConfigObject.getInt("versionCode");

                        if(new_plugin_version_code > installed_plugin_version_code) {
                            if (installed_plugin_uid != null && installed_plugin_uid.length() > 0) {
                                // Plugin Already Exist And Update It
                                utils.deleteDirectory(new File(plugins_dir, installed_plugin_uid));
                                File update_plugin_dir = new File(plugins_dir, installed_plugin_uid);
                                boolean extraction_status = zipUtils.extractZip(tmp_plugin_zip_dest.getAbsolutePath(), update_plugin_dir.getAbsolutePath(), true);
                                if (extraction_status) {
                                    // Update Plugin In DB
                                    Plugin plugin = new Plugin(installed_plugin_uid, new_plugin_name, package_name, new_plugin_description, new_plugin_author, new_plugin_version, new_plugin_version_code);
                                    pluginsDBHelper.updatePlugin(plugin);
                                    return new PluginInstallStatus("Plugin Updated", false, PluginInstallStatus.Status.UPDATE);
                                }
                            } else {
                                // Plugin Not Exist And Install New
                                UUID uniqueId = UUID.randomUUID();
                                String plugin_uid = uniqueId.toString();
                                File new_plugin_dir = new File(plugins_dir, plugin_uid);
                                boolean extraction_status = zipUtils.extractZip(tmp_plugin_zip_dest.getAbsolutePath(), new_plugin_dir.getAbsolutePath(), true);
                                if (extraction_status) {
                                    // Register Plugin In DB
                                    Plugin plugin = new Plugin(plugin_uid, new_plugin_name, package_name, new_plugin_description, new_plugin_author, new_plugin_version, new_plugin_version_code);
                                    pluginsDBHelper.insertPlugin(plugin);
                                    return new PluginInstallStatus("Plugin Installed", false, PluginInstallStatus.Status.INSTALL);
                                }
                            }
                        }else{
                            tmp_plugin_zip_dest.delete();
                            return new PluginInstallStatus("Plugin Already Installed",false, PluginInstallStatus.Status.INSTALL);
                        }
                    } catch (JSONException e) {
                        Log.d(Constants.LOG_TAG,"Error: PluginsDBHelper: Plugin Parse Error!");
                    }
                }else{
                    Log.d(Constants.LOG_TAG,"Error: PluginsDBHelper: Plugin Parse Error!");
                }
                tmp_plugin_zip_dest.delete();
            }else{
                Log.d(Constants.LOG_TAG,"Error: PluginsDBHelper: Copy Error!");
            }
        }catch (Exception e) {
            Log.d(Constants.LOG_TAG,"Error: PluginsDBHelper");
        }
        return new PluginInstallStatus("Unable To Parse Plugin!",true, PluginInstallStatus.Status.UNKNOWN);
    }
}
