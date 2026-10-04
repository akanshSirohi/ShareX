package com.akansh.fileserversuit.server;

import android.util.Log;

import com.akansh.fileserversuit.common.Constants;
import com.akansh.fileserversuit.common.SocketActions;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;

public final class WebServerSocket {
    private final int port;
    private final String appPackageName;
    private final java.util.function.Predicate<io.netty.handler.codec.http.HttpRequest> authorization;
    private final io.netty.handler.ssl.SslContext ssl;
    private final Map<String, SocketUser> socketUsers = new ConcurrentHashMap<>();
    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private Channel channel;

    public WebServerSocket(int port, String appPackageName, java.util.function.Predicate<io.netty.handler.codec.http.HttpRequest> authorization, io.netty.handler.ssl.SslContext ssl) {
        this.port = port;
        this.appPackageName = appPackageName;
        this.authorization = authorization;
        this.ssl = ssl;
    }

    public synchronized void start() throws Exception {
        if (channel != null && channel.isActive()) return;
        bossGroup = new NioEventLoopGroup(1);
        workerGroup = new NioEventLoopGroup();
        try {
            channel = new ServerBootstrap().group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override protected void initChannel(SocketChannel socket) {
                            if (ssl != null) socket.pipeline().addLast(ssl.newHandler(socket.alloc()));
                            socket.pipeline().addLast(new HttpServerCodec());
                            socket.pipeline().addLast(new HttpObjectAggregator(64 * 1024));
                            socket.pipeline().addLast(new io.netty.channel.SimpleChannelInboundHandler<io.netty.handler.codec.http.FullHttpRequest>() {
                                @Override protected void channelRead0(io.netty.channel.ChannelHandlerContext context, io.netty.handler.codec.http.FullHttpRequest request) {
                                    if (!authorization.test(request)) {
                                        context.writeAndFlush(new io.netty.handler.codec.http.DefaultFullHttpResponse(io.netty.handler.codec.http.HttpVersion.HTTP_1_1,
                                                io.netty.handler.codec.http.HttpResponseStatus.UNAUTHORIZED)).addListener(io.netty.channel.ChannelFutureListener.CLOSE);
                                        return;
                                    }
                                    io.netty.handler.codec.http.DefaultHttpRequest identity = new io.netty.handler.codec.http.DefaultHttpRequest(request.protocolVersion(), request.method(), request.uri());
                                    identity.headers().set(request.headers());
                                    context.channel().attr(io.netty.util.AttributeKey.<io.netty.handler.codec.http.HttpRequest>valueOf("sharexIdentity")).set(identity);
                                    context.fireChannelRead(request.retain());
                                }
                            });
                            socket.pipeline().addLast(new WebSocketServerProtocolHandler("/", null, true, 10 * 1024 * 1024));
                            socket.pipeline().addLast(new WebSocketHandler());
                        }
                    }).bind(new InetSocketAddress(port)).sync().channel();
        } catch (Exception e) {
            stop();
            throw e;
        }
    }

    private final class WebSocketHandler extends io.netty.channel.SimpleChannelInboundHandler<WebSocketFrame> {
        private WSDSocket socket;

        @Override public void userEventTriggered(io.netty.channel.ChannelHandlerContext context, Object event) throws Exception {
            if (event == WebSocketServerProtocolHandler.ServerHandshakeStateEvent.HANDSHAKE_COMPLETE) {
                socket = new WSDSocket(context, appPackageName);
                socket.setWsdSocketListener(createListener());
            }
            super.userEventTriggered(context, event);
        }

        @Override protected void channelRead0(io.netty.channel.ChannelHandlerContext context, WebSocketFrame frame) {
            io.netty.handler.codec.http.HttpRequest identity = context.channel().attr(io.netty.util.AttributeKey.<io.netty.handler.codec.http.HttpRequest>valueOf("sharexIdentity")).get();
            if (identity == null || !authorization.test(identity)) { context.close(); return; }
            if (frame instanceof TextWebSocketFrame && socket != null) socket.onMessage(((TextWebSocketFrame) frame).text());
        }

        @Override public void channelInactive(io.netty.channel.ChannelHandlerContext context) throws Exception {
            if (socket != null) socket.onClose();
            super.channelInactive(context);
        }
    }

    private WSDSocket.WsdSocketListener createListener() {
        return new WSDSocket.WsdSocketListener() {
            @Override public void onNewUser(SocketUser user, WSDSocket socket) {
                user.setWsdSocket(socket);
                socketUsers.put(user.getUuid(), user);
                try {
                    JSONObject update = new JSONObject();
                    update.put("action", SocketActions.USER_ARRIVE);
                    update.put("user", user.getJSONObject());
                    for (SocketUser connected : socketUsers.values()) {
                        if (connected.getPlugin_package().equals(socket.package_name) && !connected.getUuid().equals(socket.uuid)) connected.getWsdSocket().send(update.toString());
                    }
                } catch (Exception e) { Log.d(Constants.LOG_TAG, "User Arrive Error: " + e.getMessage()); }
            }

            @Override public void onUpdateUserData(String data, WSDSocket socket) {
                SocketUser user = socketUsers.get(socket.uuid);
                if (user != null) user.updatePublic_data(data);
            }

            @Override public void onAllUsersRequest(WSDSocket socket) {
                try {
                    JSONObject response = new JSONObject();
                    response.put("action", SocketActions.RETURN_ALL_USERS);
                    response.put("all_users", getAllUsersByPackage(socket.package_name));
                    socket.send(response.toString());
                } catch (Exception ignored) { }
            }

            @Override public void onRemoveUser(String uuid) {
                if (uuid == null) return;
                socketUsers.remove(uuid);
                try {
                    JSONObject response = new JSONObject();
                    response.put("action", SocketActions.USER_LEFT);
                    response.put("uuid", uuid);
                    for (SocketUser user : socketUsers.values()) user.getWsdSocket().send(response.toString());
                } catch (Exception ignored) { }
            }

            @Override public void onSendMessageToOther(String receiver, String message, String senderPackage) {
                SocketUser user = socketUsers.get(receiver);
                if (user == null || !user.getPlugin_package().equals(senderPackage)) return;
                try {
                    JSONObject response = new JSONObject();
                    response.put("action", SocketActions.MSG_ARRIVE);
                    response.put("message", message);
                    user.getWsdSocket().send(response.toString());
                } catch (Exception e) { Log.d(Constants.LOG_TAG, "Message Error: " + e.getMessage()); }
            }

            @Override public void onGetPublicDataOfUser(String uuid, WSDSocket requester) {
                SocketUser user = socketUsers.get(uuid);
                if (user == null || !user.getPlugin_package().equals(requester.package_name)) return;
                try {
                    JSONObject response = new JSONObject();
                    response.put("action", SocketActions.RETURN_PUBLIC_DATA_OF_USER);
                    response.put("public_data", new JSONObject(user.getPublic_data()));
                    requester.send(response.toString());
                } catch (Exception ignored) { }
            }
        };
    }

    private JSONArray getAllUsersByPackage(String packageName) {
        JSONArray users = new JSONArray();
        for (SocketUser user : socketUsers.values()) if (user.getPlugin_package().equals(packageName)) users.put(user.getJSONObject());
        return users;
    }

    public synchronized void closeAllConnections() {
        if (channel != null) channel.close().syncUninterruptibly();
    }

    public synchronized void stop() {
        if (channel != null) {
            channel.close().syncUninterruptibly();
            channel = null;
        }
        socketUsers.clear();
        if (workerGroup != null) {
            workerGroup.shutdownGracefully(0, 5, TimeUnit.SECONDS).syncUninterruptibly();
            workerGroup = null;
        }
        if (bossGroup != null) {
            bossGroup.shutdownGracefully(0, 5, TimeUnit.SECONDS).syncUninterruptibly();
            bossGroup = null;
        }
    }
}
