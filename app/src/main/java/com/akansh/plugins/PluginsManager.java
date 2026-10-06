package com.akansh.plugins;

import android.app.Activity;
import android.content.Context;
import android.util.Base64;
import android.util.AtomicFile;
import android.util.Log;

import androidx.annotation.NonNull;

import com.akansh.sharex.common.Constants;
import com.akansh.sharex.common.Utils;
import com.akansh.sharex.common.ZipUtils;
import com.akansh.plugins.common.Plugin;
import com.akansh.plugins.common.PluginInstallStatus;
import com.akansh.plugins.common.PluginsManagerPluginStatusListener;
import com.akansh.plugins.common.InstallStatus;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

import org.apache.commons.io.FileUtils;
import org.json.JSONException;
import org.json.JSONArray;
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

    PluginsManagerPluginStatusListener pluginsManagerPluginStatusListener;

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

    public void setPluginsManagerPluginStatusListener(PluginsManagerPluginStatusListener pluginsManagerPluginStatusListener) {
        this.pluginsManagerPluginStatusListener = pluginsManagerPluginStatusListener;
    }

    public PluginInstallStatus installPlugin(String install_package) {
        String plugin_file = install_package + ".zip";
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
                                    return new PluginInstallStatus("Plugin Updated Successfully!", false, InstallStatus.UPDATE);
                                }
                            } else {
                                // Plugin Not Exist And Install New
                                String plugin_uid = package_name.replace(".","-");
                                File new_plugin_dir = new File(plugins_dir, plugin_uid);
                                boolean extraction_status = zipUtils.extractZip(tmp_plugin_zip_dest.getAbsolutePath(), new_plugin_dir.getAbsolutePath(), true);
                                if (extraction_status) {
                                    // Register Plugin In DB
                                    Plugin plugin = new Plugin(plugin_uid, new_plugin_name, package_name, new_plugin_description, new_plugin_author, new_plugin_version, new_plugin_version_code);
                                    pluginsDBHelper.insertPlugin(plugin);
                                    return new PluginInstallStatus("Plugin Installed Successfully!", false, InstallStatus.INSTALL);
                                }
                            }
                        }else{
                            tmp_plugin_zip_dest.delete();
                            return new PluginInstallStatus("Plugin Already Installed",false, InstallStatus.INSTALL);
                        }
                    } catch (JSONException e) {
                        Log.d(Constants.LOG_TAG,"Error: PluginsDBHelper: Plugin Parse Error! : "+e.getMessage());
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
        return new PluginInstallStatus("Unable To Parse Plugin!",true, InstallStatus.UNKNOWN);
    }

    /** Installs a selected ZIP only after the UI has obtained the user's trust confirmation. */
    public synchronized PluginInstallStatus installPluginZip(android.net.Uri uri) {
        File archive = null, staged = null, backup = null, destination = null;
        boolean committed = false;
        try {
            archive = File.createTempFile("plugin-import-", ".zip", ctx.getCacheDir());
            try (java.io.InputStream input = ctx.getContentResolver().openInputStream(uri);
                 java.io.OutputStream output = new FileOutputStream(archive)) {
                if (input == null) throw new IOException("Cannot read selected ZIP");
                byte[] bytes = new byte[8192];
                long total = 0;
                int count;
                while ((count = input.read(bytes)) != -1) {
                    total += count;
                    if (total > PluginArchive.MAX_ZIP_BYTES) throw new IOException("Plugin ZIP must be at most 25 MB");
                    output.write(bytes, 0, count);
                }
            }
            JSONObject config = PluginArchive.inspect(archive);
            String packageName = config.getString("package");
            String uid = packageName.replace('.', '-');
            staged = java.nio.file.Files.createTempDirectory(plugins_dir.toPath(), ".import-").toFile();
            PluginArchive.extract(archive, staged);
            destination = new File(plugins_dir, uid);
            boolean updating = destination.exists();
            if (updating) {
                backup = new File(plugins_dir, ".backup-" + java.util.UUID.randomUUID());
                if (!destination.renameTo(backup)) throw new IOException("Cannot prepare plugin update");
            }
            if (!staged.renameTo(destination)) throw new IOException("Cannot install plugin files");
            Plugin plugin = new Plugin(uid, config.getString("name"), packageName, config.getString("description"),
                    config.getString("author"), config.getString("version"), config.getInt("versionCode"));
            try (PluginsDBHelper database = new PluginsDBHelper(ctx)) {
                boolean saved = database.getPluginUIDByPackageName(packageName) == null
                        ? database.insertPlugin(plugin) : database.updatePlugin(plugin);
                if (!saved) throw new IOException("Cannot register plugin");
            }
            committed = true;
            return new PluginInstallStatus(updating ? "Plugin updated from ZIP" : "Plugin installed from ZIP", false,
                    updating ? InstallStatus.UPDATE : InstallStatus.INSTALL);
        } catch (Exception error) {
            return new PluginInstallStatus("Cannot install ZIP: " + error.getMessage(), true, InstallStatus.UNKNOWN);
        } finally {
            if (!committed && destination != null && staged != null && !staged.exists()) utils.deleteDirectory(destination);
            if (backup != null && backup.exists()) {
                if (committed) utils.deleteDirectory(backup);
                else if (!backup.renameTo(destination)) Log.e(Constants.LOG_TAG, "Cannot restore plugin backup");
            }
            if (staged != null && staged.exists()) utils.deleteDirectory(staged);
            if (archive != null) archive.delete();
        }
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

    public void fetchPluginAppsFile() {
        fetchPluginAppsFile(false, null);
    }

    public interface CatalogFetchListener {
        void onCatalogFetched(boolean success);
    }

    public void fetchPluginAppsFile(boolean forceRefresh, CatalogFetchListener listener) {
        new Thread(() -> {
            File appsConfig = new File(plugins_dir, Constants.APPS_CONFIG);
            File tstamp_file = new File(plugins_dir, "last_fetch_timestamp.txt");
            boolean cacheFresh = false;
            try {
                long unixTimestamp = System.currentTimeMillis() / 1000L;
                if (appsConfig.exists() && tstamp_file.exists()) {
                    String tstamp = FileUtils.readFileToString(tstamp_file, "UTF-8");
                    long last_fetch_timestamp = Long.parseLong(tstamp);
                    long age = unixTimestamp - last_fetch_timestamp;
                    cacheFresh = age >= 0 && age < 24 * 60 * 60;
                    // A corrupt cache must not suppress the next fetch.
                    validatePluginCatalog(FileUtils.readFileToString(appsConfig, "UTF-8"));
                }
            }catch (Exception e) {
                cacheFresh = false;
                Log.d(Constants.LOG_TAG, "Unable to read plugin catalog cache", e);
            }
            if (cacheFresh && !forceRefresh) {
                notifyCatalogFetched(listener, true);
                return;
            }
            String appListJsonUrl = "https://api.github.com/repos/akanshSirohi/ShareX-Plugins/contents/" + Constants.APPS_CONFIG;
            OkHttpClient client = new OkHttpClient.Builder()
                    .connectTimeout(10, TimeUnit.SECONDS)
                    .callTimeout(20, TimeUnit.SECONDS)
                    .build();
            Request request = new Request.Builder()
                    .url(appListJsonUrl)
                    .build();
            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    Log.e(Constants.LOG_TAG, "Plugin catalog download failed", e);
                    notifyCatalogFetched(listener, false);
                }

                @Override
                public void onResponse(Call call, Response response) {
                    try (Response catalogResponse = response) {
                        if (!catalogResponse.isSuccessful() || catalogResponse.body() == null) {
                            throw new IOException("Plugin catalog HTTP " + catalogResponse.code());
                        }
                        String resp = response.body().string();
                        JSONObject pluginConfigObject = new JSONObject(resp);
                        String encoded_resp = pluginConfigObject.getString("content");
                        encoded_resp = encoded_resp.replace("\n", "");
                        byte[] decodedBytes = Base64.decode(encoded_resp, Base64.DEFAULT);

                        // decodedString contains the contents of the apps.json file
                        String decodedString = new String(decodedBytes, StandardCharsets.UTF_8);
                        validatePluginCatalog(decodedString);
                        // Preserve the previous catalog if writing the replacement fails.
                        AtomicFile catalog = new AtomicFile(appsConfig);
                        FileOutputStream output = null;
                        try {
                            output = catalog.startWrite();
                            output.write(decodedString.getBytes(StandardCharsets.UTF_8));
                            catalog.finishWrite(output);
                        } catch (Exception e) {
                            catalog.failWrite(output);
                            throw e;
                        }
                        // Only a successful fetch advances the cache timestamp.
                        try (FileWriter writer = new FileWriter(tstamp_file)) {
                            writer.write(String.valueOf(System.currentTimeMillis() / 1000L));
                        }
                        notifyCatalogFetched(listener, true);
                    } catch (Exception e) {
                        Log.e(Constants.LOG_TAG, "Unable to save plugin catalog", e);
                        notifyCatalogFetched(listener, false);
                    }
                }
            });
        }).start();
    }

    private static void validatePluginCatalog(String json) throws JSONException {
        JSONArray plugins = new JSONArray(json);
        for (int i = 0; i < plugins.length(); i++) {
            JSONObject plugin = plugins.getJSONObject(i);
            plugin.getString("name");
            plugin.getString("package");
            plugin.getString("description");
            plugin.getString("author");
            plugin.getString("version");
            plugin.getInt("versionCode");
        }
    }

    private void notifyCatalogFetched(CatalogFetchListener listener, boolean success) {
        if (listener != null) {
            activity.runOnUiThread(() -> {
                if (!activity.isFinishing() && !activity.isDestroyed()) {
                    listener.onCatalogFetched(success);
                }
            });
        }
    }

    public void downloadPlugin(String packageName, InstallStatus status) {
        new Thread(() -> {
//            String base_url = String.format("https://github.com/akanshSirohi/ShareX-Plugins/raw/master/%s/sharex_dist/%s.zip", packageName, packageName);
//            String base_url = String.format("https://raw.githubusercontent.com/akanshSirohi/ShareX-Plugins/master/%s/sharex_dist/%s.zip", packageName, packageName);
            String base_url = String.format("https://api.github.com/repos/akanshSirohi/ShareX-Plugins/contents/%s/sharex_dist/%s.zip", packageName, packageName);
            OkHttpClient client = new OkHttpClient.Builder()
                    .connectTimeout(10, TimeUnit.SECONDS)
                    .build();
            Request request = new Request.Builder()
                    .url(base_url)
                    .addHeader("Accept","application/vnd.github.v3.raw")
                    .build();
            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(@NonNull Call call, @NonNull IOException e) {
                    if(status == InstallStatus.UPDATE) {
                        activity.runOnUiThread(() -> pluginsManagerPluginStatusListener.onPluginUpdateDownload(false, packageName));
                    }else if(status == InstallStatus.INSTALL) {
                        activity.runOnUiThread(() -> pluginsManagerPluginStatusListener.onNewPluginDownload(false, packageName));
                    }
                }

                @Override
                public void onResponse(@NonNull Call call, @NonNull Response response) {
                    try {
                        File outputFile = new File(plugins_dir, packageName + ".zip");
                        if(outputFile.exists()) {
                            outputFile.delete();
                        }
                        FileOutputStream outputStream = new FileOutputStream(outputFile);
                        boolean flg;
                        if (response.body() != null) {
                            outputStream.write(response.body().bytes());
                            outputStream.close();
                            flg = true;
                        }else{
                            flg = false;
                        }
                        if(pluginsManagerPluginStatusListener != null) {
                            if(status == InstallStatus.UPDATE) {
                                activity.runOnUiThread(() -> pluginsManagerPluginStatusListener.onPluginUpdateDownload(flg, packageName));
                            }else if(status == InstallStatus.INSTALL) {
                                activity.runOnUiThread(() -> pluginsManagerPluginStatusListener.onNewPluginDownload(flg, packageName));
                            }
                        }
                    }catch (Exception e){
                        Log.d(Constants.LOG_TAG, "Plugin Download Failed!");
                        if(pluginsManagerPluginStatusListener != null) {
                            if(status == InstallStatus.UPDATE) {
                                activity.runOnUiThread(() -> pluginsManagerPluginStatusListener.onPluginUpdateDownload(false, packageName));
                            }else if(status == InstallStatus.INSTALL) {
                                activity.runOnUiThread(() -> pluginsManagerPluginStatusListener.onNewPluginDownload(false, packageName));
                            }
                        }
                    }
                }
            });
        }).start();
    }
}
