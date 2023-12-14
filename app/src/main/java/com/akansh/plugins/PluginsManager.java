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

    public void installPlugin(String plugin_file) {
        ZipUtils zipUtils = new ZipUtils();
        try (PluginsDBHelper pluginsDBHelper = new PluginsDBHelper(ctx)) {
            UUID uniqueId = UUID.randomUUID();
            String plugin_uid = uniqueId.toString();
            File tmp_plugin_zip_dest = new File(plugins_dir, plugin_file);
            File new_plugin_dir = new File(plugins_dir, plugin_uid);
            boolean res = copyAssetFile(plugin_file, tmp_plugin_zip_dest.getAbsolutePath());
            if(res) {
                zipUtils.extractZip(tmp_plugin_zip_dest.getAbsolutePath(), new_plugin_dir.getAbsolutePath());
                tmp_plugin_zip_dest.delete();
            }else{
                Log.d(Constants.LOG_TAG,"Error: PluginsDBHelper: Copy Error!");
            }
        }catch (Exception e) {
            Log.d(Constants.LOG_TAG,"Error: PluginsDBHelper");
        }
    }
}
