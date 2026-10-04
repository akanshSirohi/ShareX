package com.akansh.fileserversuit.server;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPInputStream;

import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.util.ReferenceCountUtil;

import static org.junit.Assert.*;

public class JsonContentCompressorTest {
    @Test public void jsonNegotiatesGzipAndRoundTripsUnicode() throws Exception {
        String body = "{\"name\":\"照片 & <notes>.jpg\",\"items\":[1,2,3]}";
        byte[] compressed = encode("gzip", "application/json; charset=utf-8", body, "gzip");
        try (GZIPInputStream input = new GZIPInputStream(new ByteArrayInputStream(compressed))) {
            assertEquals(body, new String(input.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    @Test public void noCompressionWhenGzipIsRejected() throws Exception {
        assertEquals("[]", new String(encode("gzip;q=0, identity", "application/json", "[]", null), StandardCharsets.UTF_8));
    }

    @Test public void rangeAndZipPayloadsAreNotRecompressed() throws Exception {
        assertEquals("file bytes", new String(encode("gzip", "application/octet-stream", "file bytes", null), StandardCharsets.UTF_8));
        assertEquals("zip bytes", new String(encode("gzip", "application/zip", "zip bytes", null), StandardCharsets.UTF_8));
    }

    @Test public void jsonHeadersPreventCachingDeviceListings() {
        WebResponse response = WebResponse.json(200, "[]");
        assertEquals("application/json; charset=utf-8", response.headers.get("Content-Type"));
        assertEquals("no-store", response.headers.get("Cache-Control"));
        assertEquals("Accept-Encoding", response.headers.get("Vary"));
    }

    private byte[] encode(String accept, String type, String body, String encoding) throws Exception {
        EmbeddedChannel channel = new EmbeddedChannel(new JsonContentCompressor());
        try {
            DefaultFullHttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/ShareX");
            request.headers().set(HttpHeaderNames.ACCEPT_ENCODING, accept);
            channel.writeInbound(request);
            ReferenceCountUtil.release(channel.readInbound());
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            DefaultFullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK, Unpooled.wrappedBuffer(bytes));
            response.headers().set(HttpHeaderNames.CONTENT_TYPE, type);
            response.headers().setInt(HttpHeaderNames.CONTENT_LENGTH, bytes.length);
            channel.writeOutbound(response);
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            Object message;
            boolean foundHeaders = false;
            while ((message = channel.readOutbound()) != null) {
                try {
                    if (message instanceof HttpResponse) {
                        assertEquals(encoding, ((HttpResponse) message).headers().get(HttpHeaderNames.CONTENT_ENCODING));
                        foundHeaders = true;
                    }
                    if (message instanceof HttpContent) {
                        io.netty.buffer.ByteBuf content = ((HttpContent) message).content();
                        byte[] chunk = new byte[content.readableBytes()];
                        content.readBytes(chunk);
                        output.write(chunk);
                    }
                } finally { ReferenceCountUtil.release(message); }
            }
            assertTrue(foundHeaders);
            return output.toByteArray();
        } finally { channel.finishAndReleaseAll(); }
    }
}
