package com.akansh.plugins;

import android.app.Activity;
import android.content.Context;
import android.net.Uri;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.akansh.fileserversuit.common.Utils;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.zip.*;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class PluginZipInstallTest {
    private File archive(Context context, String packageName, String text, boolean unsafe) throws Exception {
        File file = File.createTempFile("plugin-test-", ".zip", context.getCacheDir());
        JSONObject config = new JSONObject().put("package", packageName).put("name", "ZIP test")
                .put("description", "Test").put("author", "ShareX").put("version", "1.0.0").put("versionCode", 1);
        try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(file))) {
            zip.putNextEntry(new ZipEntry("config.json"));
            zip.write(config.toString().getBytes(StandardCharsets.UTF_8)); zip.closeEntry();
            zip.putNextEntry(new ZipEntry("index.html"));
            zip.write(text.getBytes(StandardCharsets.UTF_8)); zip.closeEntry();
            if (unsafe) { zip.putNextEntry(new ZipEntry("../escape")); zip.write(1); zip.closeEntry(); }
        }
        return file;
    }

    @Test public void zipInstallationReplacesSameVersionPreservesDataAndRejectsInvalidArchive() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        java.util.concurrent.atomic.AtomicReference<Activity> reference = new java.util.concurrent.atomic.AtomicReference<>();
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().runOnMainSync(() ->
                reference.set(new Activity() { @Override public String getPackageName() { return context.getPackageName(); } }));
        Activity activity = reference.get();
        Utils utils = new Utils(context);
        PluginsManager manager = new PluginsManager(activity, context, utils);
        String packageName = "sharex.import.test" + System.currentTimeMillis();
        String uid = packageName.replace('.', '-');
        File initial = archive(context, packageName, "First version", false);
        File update = archive(context, packageName, "Replaced version", false);
        File invalid = archive(context, packageName, "Unsafe version", true);
        File data = new File(manager.getPlugins_dir(), "plugin_files/" + packageName);
        File marker = new File(data, "keep.json");
        try {
            assertFalse(manager.installPluginZip(Uri.fromFile(initial)).error);
            assertEquals("First version", Files.readString(new File(manager.getPlugins_dir(), uid + "/index.html").toPath()));
            assertTrue(data.mkdirs());
            Files.writeString(marker.toPath(), "{\"keep\":true}");
            assertFalse(manager.installPluginZip(Uri.fromFile(update)).error);
            assertTrue(marker.isFile());
            assertTrue(manager.installPluginZip(Uri.fromFile(invalid)).error);
            assertEquals("Replaced version", Files.readString(new File(manager.getPlugins_dir(), uid + "/index.html").toPath()));
            try (PluginsDBHelper database = new PluginsDBHelper(context)) { assertEquals(uid, database.getPluginUIDByPackageName(packageName)); }
        } finally {
            manager.uninstallPlugin(uid);
            utils.deleteDirectory(data);
            initial.delete(); update.delete(); invalid.delete();
        }
    }
}
