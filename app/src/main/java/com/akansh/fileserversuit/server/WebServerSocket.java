package com.akansh.fileserversuit.server;

import org.nanohttpd.protocols.http.IHTTPSession;
import org.nanohttpd.protocols.http.NanoHTTPD;
import org.nanohttpd.protocols.websockets.CloseCode;
import org.nanohttpd.protocols.websockets.NanoWSD;
import org.nanohttpd.protocols.websockets.WebSocket;
import org.nanohttpd.protocols.websockets.WebSocketFrame;

import java.io.IOException;

public class WebServerSocket extends NanoWSD {

    public WebServerSocket(int port) {
        super(port);
    }

    @Override
    protected WebSocket openWebSocket(IHTTPSession ihttpSession) {
        return new WsdSocket(ihttpSession);
    }

    private static class WsdSocket extends WebSocket {
        public WsdSocket(IHTTPSession handshakeRequest) {
            super(handshakeRequest);
        }

        @Override
        protected void onOpen() {

        }

        @Override
        protected void onClose(CloseCode closeCode, String s, boolean b) {

        }

        @Override
        protected void onMessage(WebSocketFrame webSocketFrame) {
            try {
                send(webSocketFrame.getTextPayload() + " to you");
            } catch (Exception e) {
                // handle
            }
        }

        @Override
        protected void onPong(WebSocketFrame webSocketFrame) {

        }

        @Override
        protected void onException(IOException e) {

        }
    }
}
