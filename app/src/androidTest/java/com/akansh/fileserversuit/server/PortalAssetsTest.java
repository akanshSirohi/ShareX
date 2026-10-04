package com.akansh.fileserversuit.server;

import android.content.Context;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.akansh.fileserversuit.common.Constants;
import com.akansh.fileserversuit.common.Utils;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.netty.buffer.ByteBuf;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.stream.ChunkedWriteHandler;
import io.netty.util.ReferenceCountUtil;

import static org.junit.Assert.*;

/** Exercises packaged assets, Android installation, and the production static-file route together. */
@RunWith(AndroidJUnit4.class)
public class PortalAssetsTest {
    @Test
    public void extractedPortalServesEveryHtmlBundle() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        File installation = new File(context.getApplicationInfo().dataDir, "web-ui-test-" + UUID.randomUUID());
        Utils utils = new Utils(context) {
            @Override public String getFileProperPath(String path) {
                return new File(installation, path.startsWith("/") ? path.substring(1) : path).getAbsolutePath();
            }
        };
        WebRequestHandler handler = new WebRequestHandler(context, utils, new ServerUtils(context));
        try {
            assertTrue("Packaged export must install completely", new WebInterfaceSetup(context.getPackageName(),
                    context, null, utils).extractArchive(Constants.WEB_INTERFACE_DIR, installation));
            assertTrue(new File(installation, ".installed").isFile());
            String html = new String(Files.readAllBytes(new File(installation, "index.html").toPath()), StandardCharsets.UTF_8);
            assertServed(handler, installation, "/", "text/html");
            Set<String> bundles = new LinkedHashSet<>();
            Matcher references = Pattern.compile("(?:src|href)=\"(/_next/[^\"]+)\"").matcher(html);
            while (references.find()) bundles.add(references.group(1));
            assertTrue("Export must reference CSS", bundles.stream().anyMatch(path -> path.endsWith(".css")));
            assertTrue("Export must reference JavaScript", bundles.stream().anyMatch(path -> path.endsWith(".js")));
            for (String bundle : bundles) {
                assertServed(handler, installation, bundle, bundle.endsWith(".css") ? "text/css" : "text/javascript");
            }
            assertServed(handler, installation, "/players/movi.wasm", "application/wasm");
            assertServed(handler, installation, "/players/pdf/build/pdf.worker.min.mjs", "text/javascript");
        } finally {
            handler.shutdown();
            utils.deleteDirectory(installation);
        }
    }

    @Test
    public void updateDeletesOldVersionsBeforeExtractingAndRefreshesSameVersion() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        File root = new File(context.getCacheDir(), "web-update-test-" + UUID.randomUUID());
        Utils utils = new Utils(context);
        assertTrue(root.mkdirs());
        WebInterfaceSetup setup = new WebInterfaceSetup(context.getPackageName(), context, null, utils);
        try {
            File previous = new File(root, "sharex_web_v4_3");
            assertTrue(previous.mkdirs());
            File interrupted = new File(root, Constants.NEW_DIR + ".pending");
            assertTrue(interrupted.mkdirs());
            File unrelated = new File(root, "keep.txt");
            Files.write(unrelated.toPath(), new byte[] { 1 });
            assertTrue(setup.install(root));
            assertFalse(previous.exists());
            assertFalse(interrupted.exists());
            assertTrue(unrelated.isFile());
            File installed = new File(root, Constants.NEW_DIR);
            assertTrue(new File(installed, ".installed").isFile());
            File obsolete = new File(installed, "obsolete.js");
            Files.write(obsolete.toPath(), new byte[] { 2 });
            assertTrue(setup.install(root));
            assertFalse(obsolete.exists());
            assertTrue(new File(installed, "_next/static").isDirectory());
        } finally { utils.deleteDirectory(root); }
    }

    @Test
    public void extractionRejectsTraversalAndIncompleteArchives() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        File root = new File(context.getCacheDir(), "web-invalid-test-" + UUID.randomUUID());
        assertTrue(root.mkdirs());
        Utils utils = new Utils(context);
        try {
            for (String name : new String[] { "../escaped.txt", "/escaped.txt", "C:/escaped.txt", "index.html" }) {
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                try (java.util.zip.ZipOutputStream zip = new java.util.zip.ZipOutputStream(bytes)) {
                    zip.putNextEntry(new java.util.zip.ZipEntry(name));
                    zip.write(new byte[] { 1 });
                    zip.closeEntry();
                }
                File destination = new File(root, "attempt-" + UUID.randomUUID());
                try {
                    WebInterfaceSetup.extract(new java.io.ByteArrayInputStream(bytes.toByteArray()), destination, Constants.NEW_DIR);
                    fail("Invalid archive must fail: " + name);
                } catch (java.io.IOException expected) {
                    assertFalse(new File(destination, ".installed").exists());
                    assertFalse(new File(root, "escaped.txt").exists());
                }
            }
            File failed = new File(root, "missing-archive");
            assertFalse(new WebInterfaceSetup(context.getPackageName(), context, null, utils)
                    .extractArchive("sharex_web_v999_0", failed));
            assertFalse(failed.exists());
        } finally { utils.deleteDirectory(root); }
    }

    private void assertServed(WebRequestHandler handler, File installation, String uri, String mime) throws Exception {
        File source = new File(installation, uri.equals("/") ? "index.html" : uri.substring(1));
        assertTrue("Missing installed bundle: " + uri, source.isFile());
        EmbeddedChannel channel = new EmbeddedChannel(new ChunkedWriteHandler(), handler.newChannelHandler());
        try {
            channel.writeInbound(new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, uri));
            channel.runPendingTasks();
            ByteArrayOutputStream body = new ByteArrayOutputStream();
            HttpResponse response = null;
            Object message;
            while ((message = channel.readOutbound()) != null) {
                try {
                    if (message instanceof HttpResponse) response = (HttpResponse) message;
                    if (message instanceof ByteBuf) {
                        byte[] bytes = new byte[((ByteBuf) message).readableBytes()];
                        ((ByteBuf) message).readBytes(bytes);
                        body.write(bytes);
                    }
                } finally { ReferenceCountUtil.release(message); }
            }
            assertNotNull("No response for " + uri, response);
            assertEquals("HTTP status for " + uri, 200, response.status().code());
            assertTrue("Content type for " + uri, response.headers().get("Content-Type").startsWith(mime));
            assertArrayEquals("Response payload for " + uri, Files.readAllBytes(source.toPath()), body.toByteArray());
        } finally { channel.finishAndReleaseAll(); }
    }
}
