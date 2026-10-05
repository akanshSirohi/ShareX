package com.akansh.plugins;

import org.json.JSONObject;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Validates and extracts portable plugin ZIPs without following archive paths outside the destination. */
public final class PluginArchive {
    public static final long MAX_ZIP_BYTES = 25L * 1024 * 1024;
    private static final long MAX_EXTRACTED_BYTES = 100L * 1024 * 1024;

    private PluginArchive() {}

    public static JSONObject inspect(File archive) throws Exception { return read(archive, null); }
    public static void extract(File archive, File destination) throws Exception { read(archive, destination); }

    private static JSONObject read(File archive, File destination) throws Exception {
        if (archive.length() > MAX_ZIP_BYTES) throw new IOException("Plugin ZIP must be at most 25 MB");
        Set<String> names = new HashSet<>();
        JSONObject config = null;
        boolean index = false;
        long total = 0;
        byte[] buffer = new byte[8192];
        try (ZipInputStream zip = new ZipInputStream(new FileInputStream(archive))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                String name = entry.getName();
                String normalized = name.endsWith("/") ? name.substring(0, name.length() - 1) : name;
                if (normalized.isEmpty() || name.startsWith("/") || name.contains("\\") || name.contains(":")) throw new IOException("Unsafe ZIP entry");
                for (String part : normalized.split("/", -1)) {
                    if (part.isEmpty() || part.equals(".") || part.equals("..")) throw new IOException("Unsafe ZIP entry");
                }
                if (!names.add(normalized) || names.size() > 5000) throw new IOException("Duplicate entries or too many files in ZIP");
                File target = destination == null ? null : new File(destination, name);
                if (target != null && !target.getCanonicalPath().startsWith(destination.getCanonicalPath() + File.separator)) throw new IOException("Unsafe ZIP entry");
                if (entry.isDirectory()) {
                    if (target != null && !target.isDirectory() && !target.mkdirs()) throw new IOException("Cannot create plugin directory");
                    continue;
                }
                if (target != null && !target.getParentFile().isDirectory() && !target.getParentFile().mkdirs()) throw new IOException("Cannot create plugin directory");
                ByteArrayOutputStream metadata = name.equals("config.json") ? new ByteArrayOutputStream() : null;
                try (OutputStream output = target == null ? null : new FileOutputStream(target)) {
                    int count;
                    while ((count = zip.read(buffer)) != -1) {
                        total += count;
                        if (total > MAX_EXTRACTED_BYTES) throw new IOException("Plugin contents must be at most 100 MB");
                        if (output != null) output.write(buffer, 0, count);
                        if (metadata != null) {
                            if (metadata.size() + count > 65536) throw new IOException("Plugin config is too large");
                            metadata.write(buffer, 0, count);
                        }
                    }
                }
                if (metadata != null) config = new JSONObject(metadata.toString(StandardCharsets.UTF_8.name()));
                if (name.equals("index.html")) index = true;
            }
        }
        if (config == null || !index) throw new IOException("ZIP must contain config.json and index.html at its root");
        String packageName = config.getString("package");
        if (packageName.length() > 128 || !packageName.matches("[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)+")
                || packageName.startsWith("dev.")) throw new IOException("Invalid plugin package name");
        for (String field : new String[]{"name", "description", "author", "version"}) config.getString(field);
        if (config.getString("name").trim().isEmpty() || config.getInt("versionCode") < 1) throw new IOException("Invalid plugin name or versionCode");
        return config;
    }
}
