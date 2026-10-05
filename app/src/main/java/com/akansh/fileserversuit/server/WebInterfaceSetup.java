package com.akansh.fileserversuit.server;

import android.app.Activity;
import android.content.Context;
import android.util.Log;
import com.akansh.fileserversuit.common.Constants;
import com.akansh.fileserversuit.common.Utils;
import org.json.JSONObject;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class WebInterfaceSetup {
    // Enable to replace the bundled web UI on every app launch, even at the same version.
    public static final boolean debugWebUI = false;
    private final Context ctx;
    private final Activity activity;
    private final Utils utils;
    private static final String INSTALLED_MARKER = ".installed";
    public SetupListeners setupListeners;

    public WebInterfaceSetup(String packageName, Context ctx, Activity activity, Utils utils) {
        this.ctx = ctx;
        this.activity = activity;
        this.utils = utils;
    }

    public boolean isInstalled() {
        return isInstallationCurrent(new File(ctx.getApplicationInfo().dataDir), Constants.NEW_DIR);
    }

    public boolean needsSetup() {
        return shouldInstall(new File(ctx.getApplicationInfo().dataDir), Constants.NEW_DIR, debugWebUI);
    }

    static boolean shouldInstall(File dataDir, String assetFolder, boolean debugWebUI) {
        return debugWebUI || !isInstallationCurrent(dataDir, assetFolder);
    }

    private static boolean isInstallationCurrent(File dataDir, String assetFolder) {
        File installed = new File(dataDir, assetFolder);
        File marker = new File(installed, INSTALLED_MARKER);
        if (!marker.isFile() || !new File(installed, "index.html").isFile()) return false;
        try {
            return assetFolder.equals(new String(Files.readAllBytes(marker.toPath()), StandardCharsets.UTF_8));
        } catch (IOException e) {
            return false;
        }
    }

    public void setup() {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            File dataDir = new File(ctx.getApplicationInfo().dataDir);
            boolean updating = hasPreviousVersion(dataDir);
            activity.runOnUiThread(() -> setupListeners.onSetupStarted(updating));
            boolean status = install(dataDir);
            activity.runOnUiThread(() -> setupListeners.onSetupCompeted(status));
            executor.shutdown();
        });
    }

    // Delete old versions before extraction. A completion marker makes interrupted installs retryable.
    boolean install(File dataDir) {
        File installed = new File(dataDir, Constants.NEW_DIR);
        try {
            File[] children = dataDir.listFiles();
            if (children != null) for (File child : children) {
                if (isVersionDirectory(child)) {
                    utils.deleteDirectory(child);
                    if (child.exists()) throw new IOException("Unable to remove old web UI: " + child.getName());
                }
            }
            return extractArchive(Constants.WEB_INTERFACE_DIR, installed);
        } catch (Exception e) {
            Log.e(Constants.LOG_TAG, "Unable to install web UI", e);
            utils.deleteDirectory(installed);
            return false;
        }
    }

    private boolean isVersionDirectory(File file) {
        return file.isDirectory() && file.getName().matches("sharex_(web_)?v[0-9]+(_[0-9]+)*(\\.pending|\\.backup)?");
    }

    private boolean hasPreviousVersion(File parent) {
        File[] children = parent.listFiles();
        if (children != null) for (File child : children) if (isVersionDirectory(child)) return true;
        return false;
    }

    public interface SetupListeners {
        void onSetupCompeted(boolean status);
        void onSetupStarted(boolean updating);
    }

    public boolean extractArchive(String assetFolder, File destination) {
        try (InputStream archive = ctx.getAssets().open(assetFolder + ".zip")) {
            extract(archive, destination, assetFolder);
            return true;
        } catch (Exception e) {
            Log.e(Constants.LOG_TAG, "Unable to extract web UI assets", e);
            utils.deleteDirectory(destination);
            return false;
        }
    }

    static void extract(InputStream archive, File destination, String assetFolder) throws Exception {
        if (destination.exists()) throw new IOException("Web UI destination must be empty");
        if (!destination.mkdirs()) throw new IOException("Unable to create web UI directory");
        String root = destination.getCanonicalPath() + File.separator;
        Set<String> entries = new HashSet<>();
        try (ZipInputStream input = new ZipInputStream(new BufferedInputStream(archive))) {
            ZipEntry entry;
            byte[] buffer = new byte[64 * 1024];
            while ((entry = input.getNextEntry()) != null) {
                String name = entry.getName();
                File target = new File(destination, name);
                String canonical = target.getCanonicalPath();
                if (name.contains("\\") || name.startsWith("/") || name.contains(":")
                        || !canonical.startsWith(root) || !entries.add(canonical)
                        || name.equals(INSTALLED_MARKER)) throw new IOException("Invalid web UI archive entry");
                if (entry.isDirectory()) {
                    if (!target.isDirectory() && !target.mkdirs()) throw new IOException("Unable to create archive directory");
                } else {
                    File parent = target.getParentFile();
                    if (!parent.isDirectory() && !parent.mkdirs()) throw new IOException("Unable to create archive directory");
                    try (OutputStream output = new FileOutputStream(target)) {
                        int count;
                        while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
                    }
                }
                input.closeEntry();
            }
        }
        File index = new File(destination, "index.html");
        File manifest = new File(destination, "build-info.json");
        if (!index.isFile() || !manifest.isFile()
                || !assetFolder.equals(new JSONObject(new String(Files.readAllBytes(manifest.toPath()),
                StandardCharsets.UTF_8)).optString("assetFolder"))) throw new IOException("Web UI archive is incomplete or stale");
        Files.write(new File(destination, INSTALLED_MARKER).toPath(), assetFolder.getBytes(StandardCharsets.UTF_8));
    }
}
