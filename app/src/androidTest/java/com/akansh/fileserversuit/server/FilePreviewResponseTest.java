package com.akansh.fileserversuit.server;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.akansh.fileserversuit.common.Utils;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.stream.ChunkedWriteHandler;

import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class FilePreviewResponseTest {
    @Test
    public void previewIsInlineAndDownloadIsAttachmentEvenWithLegacySetting() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        SharedPreferences settings = context.getSharedPreferences(context.getPackageName(), Context.MODE_PRIVATE);
        boolean hadSetting = settings.contains("force_download");
        boolean oldSetting = settings.getBoolean("force_download", false);
        boolean privateMode = settings.getBoolean("private_mode", false);
        settings.edit().putBoolean("force_download", true).putBoolean("private_mode", false).commit();
        File root = Files.createTempDirectory(context.getCacheDir().toPath(), "preview-test-").toFile();
        Utils utils = new Utils(context);
        WebRequestHandler handler = new WebRequestHandler(context, utils, new ServerUtils(context));
        handler.setRoot(root.getAbsolutePath());
        try {
            for (String name : new String[]{"image.png", "video.mp4", "audio.mp3", "document.pdf"}) {
                Files.write(new File(root, name).toPath(), new byte[]{1, 2, 3, 4, 5});
                assertHeader(handler, "/ShareX?action=previewFile&location=" + name, null, 200, "inline");
                assertHeader(handler, "/ShareX?action=openFile&location=" + name, null, 200, "inline");
                String data = URLEncoder.encode("[\"" + name + "\"]", StandardCharsets.UTF_8.name());
                assertHeader(handler, "/ShareX?action=downloadFiles&filename=files&data=" + data, null, 200, "attachment");
            }
            HttpResponse partial = assertHeader(handler, "/ShareX?action=previewFile&location=audio.mp3", "bytes=1-3", 206, "inline");
            assertEquals("bytes 1-3/5", partial.headers().get("Content-Range"));
            assertEquals("3", partial.headers().get("Content-Length"));
        } finally {
            handler.shutdown(); utils.deleteDirectory(root);
            SharedPreferences.Editor restore = settings.edit().putBoolean("private_mode", privateMode);
            if (hadSetting) restore.putBoolean("force_download", oldSetting); else restore.remove("force_download");
            restore.commit();
        }
    }

    private HttpResponse assertHeader(WebRequestHandler handler, String uri, String range, int status, String disposition) {
        EmbeddedChannel channel = new EmbeddedChannel(new ChunkedWriteHandler(), handler.newChannelHandler());
        try {
            DefaultFullHttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, uri);
            if (range != null) request.headers().set("Range", range);
            channel.writeInbound(request);
            channel.runPendingTasks();
            HttpResponse response = channel.readOutbound();
            assertNotNull(response);
            assertEquals(status, response.status().code());
            assertTrue(response.headers().get("Content-Disposition").startsWith(disposition + ";"));
            assertEquals("bytes", response.headers().get("Accept-Ranges"));
            return response;
        } finally { channel.finishAndReleaseAll(); }
    }
}
