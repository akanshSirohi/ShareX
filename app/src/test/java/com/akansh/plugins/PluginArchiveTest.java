package com.akansh.plugins;

import org.junit.Test;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.zip.*;
import static org.junit.Assert.*;

public class PluginArchiveTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();
    private static final String CONFIG = "{\"name\":\"Starter\",\"package\":\"sharex.starter.plugin\",\"description\":\"Sample\",\"author\":\"ShareX\",\"version\":\"1.0.0\",\"versionCode\":1}";

    private File archive(String config, String extra) throws Exception {
        File archive = temporary.newFile();
        try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(archive))) {
            for (String name : new String[]{"config.json", "index.html", extra}) {
                zip.putNextEntry(new ZipEntry(name));
                zip.write((name.equals("config.json") ? config : "Sample content").getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        return archive;
    }

    @Test public void extractsStaticPluginIncludingNextAssets() throws Exception {
        File archive = archive(CONFIG, "_next/static/app.js");
        assertEquals("sharex.starter.plugin", PluginArchive.inspect(archive).getString("package"));
        File destination = temporary.newFolder();
        PluginArchive.extract(archive, destination);
        assertTrue(new File(destination, "index.html").isFile());
        assertTrue(new File(destination, "_next/static/app.js").isFile());
    }

    @Test public void rejectsTraversalAbsoluteWindowsPathsAndMalformedPackages() throws Exception {
        for (String name : new String[]{"../escape", "/escape", "C:/escape", "folder\\escape", "folder/../escape"}) {
            File archive = archive(CONFIG, name);
            assertThrows(IOException.class, () -> PluginArchive.inspect(archive));
        }
        assertThrows(IOException.class, () -> PluginArchive.inspect(archive(CONFIG.replace("sharex.starter.plugin", "../outside"), "app.js")));
    }

    @Test public void rejectsZipWithoutRootMetadataAndEntryPoint() throws Exception {
        File archive = temporary.newFile();
        try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(archive))) {
            zip.putNextEntry(new ZipEntry("nested/index.html")); zip.write(1); zip.closeEntry();
        }
        assertThrows(IOException.class, () -> PluginArchive.inspect(archive));
    }
}
