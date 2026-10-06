package com.akansh.sharex.server;

import com.akansh.sharex.common.Constants;
import org.junit.Test;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import java.io.*;
import java.nio.file.Files;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import static org.junit.Assert.*;

public class WebInterfaceArchiveTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    @Test public void launchSkipsCurrentVersionUnlessWebDebugIsEnabled() throws Exception {
        File root = temporary.newFolder("launch");
        String version = "sharex_web_v4_8";
        assertTrue(WebInterfaceSetup.shouldInstall(root, version, false));
        File installed = new File(root, version);
        assertTrue(installed.mkdir());
        Files.writeString(new File(installed, "index.html").toPath(), "installed content");
        File marker = new File(installed, ".installed");
        // An interrupted installation has no marker and must retry.
        assertTrue(WebInterfaceSetup.shouldInstall(root, version, false));
        Files.writeString(marker.toPath(), version);
        assertFalse(WebInterfaceSetup.shouldInstall(root, version, false));
        assertTrue(WebInterfaceSetup.shouldInstall(root, version, true));
        assertTrue(WebInterfaceSetup.shouldInstall(root, "sharex_web_v4_9", false));
        assertEquals("installed content", Files.readString(new File(installed, "index.html").toPath()));
        Files.writeString(marker.toPath(), "sharex_web_v4_7");
        assertTrue(WebInterfaceSetup.shouldInstall(root, version, false));
        Files.writeString(marker.toPath(), version);
        assertTrue(new File(installed, "index.html").delete());
        assertTrue(WebInterfaceSetup.shouldInstall(root, version, false));
    }

    @Test public void actualPackagedArchiveExtractsWithAllRuntimeAssetsAndCompletionMarker() throws Exception {
        File destination = new File(temporary.getRoot(), "installed");
        File archive = new File("src/main/assets", Constants.WEB_INTERFACE_DIR + ".zip");
        try (InputStream input = new FileInputStream(archive)) {
            WebInterfaceSetup.extract(input, destination, Constants.WEB_INTERFACE_DIR);
        }
        assertTrue(new File(destination, "index.html").isFile());
        assertTrue(new File(destination, "_next/static/chunks").isDirectory());
        assertTrue(new File(destination, "players/movi.wasm").isFile());
        assertTrue(new File(destination, "players/pdf/build/pdf.worker.min.mjs").isFile());
        assertTrue(new File(destination, "sharex-logo.png").isFile());
        assertEquals(Constants.WEB_INTERFACE_DIR, Files.readString(new File(destination, ".installed").toPath()));
    }

    @Test public void unsafeIncompleteAndStaleArchivesNeverMarkInstallationComplete() throws Exception {
        for (String entry : new String[] { "../escaped.txt", "/escaped.txt", "C:/escaped.txt", "foo\\bar", ".installed", "index.html" }) {
            File destination = new File(temporary.getRoot(), "invalid-" + java.util.UUID.randomUUID());
            try {
                WebInterfaceSetup.extract(new ByteArrayInputStream(zip(entry)), destination, Constants.WEB_INTERFACE_DIR);
                fail("Archive should be rejected: " + entry);
            } catch (IOException expected) {
                assertFalse(new File(destination, ".installed").exists());
                assertFalse(new File(temporary.getRoot(), "escaped.txt").exists());
            }
        }
        File destination = new File(temporary.getRoot(), "stale");
        try (InputStream input = new FileInputStream(new File("src/main/assets", Constants.WEB_INTERFACE_DIR + ".zip"))) {
            try {
                WebInterfaceSetup.extract(input, destination, "sharex_web_v999_0");
                fail("Manifest version should be rejected");
            } catch (IOException expected) {
                assertFalse(new File(destination, ".installed").exists());
            }
        }
    }

    private byte[] zip(String entry) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            zip.putNextEntry(new ZipEntry(entry));
            zip.write(new byte[] { 1 });
            zip.closeEntry();
        }
        return bytes.toByteArray();
    }
}
