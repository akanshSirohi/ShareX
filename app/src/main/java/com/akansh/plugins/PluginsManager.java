package com.akansh.plugins;

import android.app.Activity;
import android.content.Context;
import android.util.Base64;
import android.util.Log;

import androidx.annotation.NonNull;

import com.akansh.fileserversuit.Constants;
import com.akansh.fileserversuit.Utils;
import com.akansh.fileserversuit.ZipUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.json.JSONException;
import org.json.JSONObject;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class PluginsManager {
    Activity activity;
    Context ctx;
    Utils utils;
    File plugins_dir;

    PluginsManagerListener pluginsManagerListener;

    public PluginsManager(Activity activity, Context ctx, Utils utils) {
        this.activity = activity;
        this.ctx = ctx;
        this.utils = utils;
        plugins_dir=new File(String.format("/data/data/%s/plugins",activity.getPackageName()));
        if(!plugins_dir.exists()) {
            plugins_dir.mkdir();
        }
    }

    public File getPlugins_dir() {
        return plugins_dir;
    }

    public void setPluginsManagerListener(PluginsManagerListener pluginsManagerListener) {
        this.pluginsManagerListener = pluginsManagerListener;
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
            if(tmp_plugin_zip_dest.exists()) {
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
                                    return new PluginInstallStatus("Plugin Updated Successfully!", false, PluginInstallStatus.Status.UPDATE);
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
                                    return new PluginInstallStatus("Plugin Installed Successfully!", false, PluginInstallStatus.Status.INSTALL);
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
            Log.d(Constants.LOG_TAG,"Error: PluginsDBHelper (Install)");
        }
        return new PluginInstallStatus("Unable To Parse Plugin!",true, PluginInstallStatus.Status.UNKNOWN);
    }


    public boolean uninstallPlugin(String uid) {
        try (PluginsDBHelper pluginsDBHelper = new PluginsDBHelper(ctx)) {
            boolean res = utils.deleteDirectory(new File(plugins_dir, uid));
            if(res) {
                pluginsDBHelper.deletePluginEntry(uid);
            }
            return res;
        }catch (Exception e) {
            Log.d(Constants.LOG_TAG,"Error: PluginsDBHelper (Uninstall)");
        }
        return false;
    }

    public void fetchPluginsFile() {
        new Thread(() -> {
            String appListJsonUrl = "https://api.github.com/repos/akanshSirohi/ShareX-Plugins/contents/" + Constants.APPS_CONFIG;
            OkHttpClient client = new OkHttpClient();
            Request request = new Request.Builder()
                    .url(appListJsonUrl)
                    .build();
            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    if(pluginsManagerListener != null) {
                        activity.runOnUiThread(() -> pluginsManagerListener.onServerPluginsFetchUpdate(false, null));
                    }
                }

                @Override
                public void onResponse(Call call, Response response) {
                    try {
                        String resp = response.body().string();
                        JSONObject pluginConfigObject = new JSONObject(resp);
                        String encoded_resp = pluginConfigObject.getString("content");
                        encoded_resp = encoded_resp.replace("\n","");
                        byte[] decodedBytes = Base64.decode(encoded_resp, Base64.DEFAULT);

                        // decodedString contains the contents of the apps.json file
                        String decodedString = new String(decodedBytes, StandardCharsets.UTF_8);
                        File appsConfig = new File(plugins_dir, Constants.APPS_CONFIG);

                        // Delete if exists to update to new version
                        if(appsConfig.exists()) {
                            appsConfig.delete();
                        }
                        // Save or update file to local storage
                        appsConfig.createNewFile();
                        FileOutputStream fOut = new FileOutputStream(appsConfig);
                        OutputStreamWriter myOutWriter = new OutputStreamWriter(fOut);
                        myOutWriter.append(decodedString);
                        myOutWriter.close();
                        fOut.flush();
                        fOut.close();
                        if(pluginsManagerListener != null) {
                            activity.runOnUiThread(() -> pluginsManagerListener.onServerPluginsFetchUpdate(true, appsConfig));
                        }
                    }catch (Exception e) {
                        if(pluginsManagerListener != null) {
                            activity.runOnUiThread(() -> pluginsManagerListener.onServerPluginsFetchUpdate(false, null));
                        }
                    }
                }
            });
        }).start();
    }

    public void downloadPlugin(String packageName) {
        new Thread(() -> {
            String base_url = String.format("https://github.com/akanshSirohi/ShareX-Plugins/raw/master/%s/dist/%s.zip", packageName, packageName);
            OkHttpClient client = new OkHttpClient();
            Request request = new Request.Builder()
                    .url(base_url)
                    .build();
            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(@NonNull Call call, @NonNull IOException e) {
                    Log.d(Constants.LOG_TAG, "Plugin Download Failed!");
                }

                @Override
                public void onResponse(@NonNull Call call, @NonNull Response response) {
                    try {
                        File outputFile = new File(plugins_dir, packageName + ".zip");
                        if(outputFile.exists()) {
                            outputFile.delete();
                        }
                        FileOutputStream outputStream = new FileOutputStream(outputFile);
                        if (response.body() != null) {
                            outputStream.write(response.body().bytes());
                            outputStream.close();
                            activity.runOnUiThread(() -> pluginsManagerListener.onPluginUpdateDownload(true, packageName));
                        }else{
                            activity.runOnUiThread(() -> pluginsManagerListener.onPluginUpdateDownload(false, packageName));
                        }
                    }catch (Exception e){
                        Log.d(Constants.LOG_TAG, "Plugin Download Failed!");
                        if(pluginsManagerListener != null) {
                            activity.runOnUiThread(() -> pluginsManagerListener.onPluginUpdateDownload(false, packageName));
                        }
                    }
                }
            });
        }).start();
    }

    public interface PluginsManagerListener {
        void onServerPluginsFetchUpdate(boolean res, File path);
        void onPluginUpdateDownload(boolean res, String packageName);
    }
}
