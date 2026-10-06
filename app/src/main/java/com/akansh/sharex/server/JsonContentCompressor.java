package com.akansh.sharex.server;

import io.netty.handler.codec.compression.StandardCompressionOptions;
import io.netty.handler.codec.http.HttpContentCompressor;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpResponse;

/** Negotiate and stream gzip for JSON without recompressing file, range, or ZIP downloads. */
final class JsonContentCompressor extends HttpContentCompressor {
    JsonContentCompressor() { super(StandardCompressionOptions.gzip()); }

    @Override
    protected Result beginEncode(HttpResponse response, String acceptEncoding) throws Exception {
        String type = response.headers().get(HttpHeaderNames.CONTENT_TYPE);
        return type != null && type.startsWith("application/json")
                ? super.beginEncode(response, acceptEncoding) : null;
    }
}
