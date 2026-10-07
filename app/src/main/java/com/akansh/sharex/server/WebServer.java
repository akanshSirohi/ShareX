package com.akansh.sharex.server;

import android.content.Context;
import android.util.Log;

import com.akansh.sharex.common.Constants;
import com.akansh.sharex.common.Utils;
import com.akansh.sharex.transfer_history.HistoryDBManager;

import java.io.InputStream;
import java.net.InetSocketAddress;
import java.security.KeyStore;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.KeyManagerFactory;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.util.concurrent.DefaultEventExecutorGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.stream.ChunkedWriteHandler;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;

public final class WebServer {
    private final String hostname;
    private final int port;
    private Context context;
    private Utils utils;
    private ServerUtils serverUtils;
    private WebRequestHandler requestHandler;
    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private DefaultEventExecutorGroup requestExecutorGroup;
    private Channel channel;
    private SslContext sslContext;

    WebServer(String hostname, int port) {
        this.hostname = hostname;
        this.port = port;
    }

    synchronized void setContext(Context context) {
        this.context = context.getApplicationContext();
        utils = new Utils(this.context);
        serverUtils = new ServerUtils(this.context);
        requestHandler = new WebRequestHandler(this.context, utils, serverUtils);
        if (utils.loadSetting(Constants.PLUGIN_DEV)) requestHandler.setPluginDevDir(utils.loadPluginDevFolder());
        serverUtils.setSendProgressListener(progress -> requestHandler.reportProgress(progress));
        serverUtils.setUpdateTransferHistoryListener(item -> new HistoryDBManager(this.context).addTransferHistory(
                item.getItem_type(), item.getFile_name(), item.getSize(), item.getDate(), item.getTime(), item.getType(), item.getPath()));
        sslContext = createSslContext();
    }

    public void setRoot(String root) {
        if (requestHandler != null) requestHandler.setRoot(root);
    }

    boolean isAuthorized(io.netty.handler.codec.http.HttpRequest request) { return requestHandler.isSocketAuthorized(request); }
    boolean isPluginSocketAuthorized(io.netty.handler.codec.http.HttpRequest request, String packageName) { return requestHandler.isPluginSocketAuthorized(request, packageName); }
    SslContext getSslContext() { return sslContext; }

    public void setAllowHiddenMedia(boolean allowHiddenMedia) {
        if (requestHandler != null) requestHandler.setAllowHiddenMedia(allowHiddenMedia);
    }

    public synchronized void start() throws Exception {
        if (requestHandler == null) throw new IllegalStateException("Server context was not set");
        if (channel != null && channel.isActive()) return;
        bossGroup = new NioEventLoopGroup(1);
        workerGroup = new NioEventLoopGroup();
        requestExecutorGroup = new DefaultEventExecutorGroup(Math.max(2, Math.min(8, Runtime.getRuntime().availableProcessors())));
        try {
            ServerBootstrap bootstrap = new ServerBootstrap()
                    .group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .option(ChannelOption.SO_REUSEADDR, true)
                    .childOption(ChannelOption.TCP_NODELAY, true)
                    .childOption(ChannelOption.SO_KEEPALIVE, true)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel socket) {
                            if (sslContext != null) socket.pipeline().addLast("ssl", sslContext.newHandler(socket.alloc()));
                            socket.pipeline().addLast("readTimeout", new ReadTimeoutHandler(120, TimeUnit.SECONDS));
                            socket.pipeline().addLast("writeTimeout", new WriteTimeoutHandler(120, TimeUnit.SECONDS));
                            socket.pipeline().addLast("http", new HttpServerCodec(64 * 1024, 8192, 8192));
                            socket.pipeline().addLast("jsonCompression", new JsonContentCompressor());
                            socket.pipeline().addLast("chunked", new ChunkedWriteHandler());
                            socket.pipeline().addLast(requestExecutorGroup, "requests", requestHandler.newChannelHandler());
                        }
                    });
            channel = bootstrap.bind(new InetSocketAddress(hostname, port)).sync().channel();
        } catch (Exception e) {
            stop();
            throw e;
        }
    }

    private SslContext createSslContext() {
        if (utils == null || !utils.loadSetting(Constants.SSL)) return null;
        try (InputStream stream = context.getAssets().open("keystore.bks")) {
            KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
            keyStore.load(stream, "sharex_akansh".toCharArray());
            KeyManagerFactory managerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            managerFactory.init(keyStore, "sharex_akansh".toCharArray());
            return SslContextBuilder.forServer(managerFactory).build();
        } catch (Exception e) {
            Log.e(Constants.LOG_TAG, "SSL Error: " + e.getMessage());
            throw new IllegalStateException("Unable to initialize HTTPS", e);
        }
    }

    public int getListeningPort() {
        return channel == null ? port : ((InetSocketAddress) channel.localAddress()).getPort();
    }

    public String getHostname() {
        return hostname;
    }

    public synchronized void closeAllConnections() {
        if (channel != null) channel.close().syncUninterruptibly();
    }

    public synchronized void stop() {
        if (channel != null) {
            channel.close().syncUninterruptibly();
            channel = null;
        }
        if (requestExecutorGroup != null) {
            requestExecutorGroup.shutdownGracefully(0, 5, TimeUnit.SECONDS).syncUninterruptibly();
            requestExecutorGroup = null;
        }
        if (workerGroup != null) {
            workerGroup.shutdownGracefully(0, 5, TimeUnit.SECONDS).syncUninterruptibly();
            workerGroup = null;
        }
        if (bossGroup != null) {
            bossGroup.shutdownGracefully(0, 5, TimeUnit.SECONDS).syncUninterruptibly();
            bossGroup = null;
        }
        if (requestHandler != null) requestHandler.shutdown();
    }
}
