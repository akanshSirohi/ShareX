package com.akansh.fileserversuit.server;

import io.netty.handler.codec.http.DefaultHttpRequest;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpVersion;
import org.junit.Test;
import static org.junit.Assert.*;

public class WebOriginTest {
    @Test public void crossOriginAndSiblingSiteRequestsAreRejected() {
        DefaultHttpRequest request = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/ShareX?action=delFiles");
        request.headers().set("Host", "192.168.1.2:6060");
        request.headers().set("Sec-Fetch-Site", "same-origin");
        request.headers().set("Origin", "http://192.168.1.2:6060");
        assertTrue(WebRequestHandler.sameOrigin(request));
        request.headers().set("Origin", "http://attacker.example");
        assertFalse(WebRequestHandler.sameOrigin(request));
        request.headers().remove("Origin");
        request.headers().set("Sec-Fetch-Site", "cross-site");
        assertFalse(WebRequestHandler.sameOrigin(request));
        request.headers().set("Sec-Fetch-Site", "same-site");
        assertFalse(WebRequestHandler.sameOrigin(request));
        request.headers().set("Sec-Fetch-Site", "none");
        assertTrue(WebRequestHandler.sameOrigin(request));
    }
}
