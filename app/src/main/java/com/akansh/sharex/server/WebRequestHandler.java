package com.akansh.sharex.server;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Environment;
import android.util.Log;

import com.akansh.sharex.common.Constants;
import com.akansh.sharex.common.Utils;
import com.akansh.sharex.transfer_history.HistoryDBManager;

import org.json.JSONArray;

import java.io.File;
import java.io.FileInputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelProgressiveFuture;
import io.netty.channel.ChannelProgressiveFutureListener;
import io.netty.channel.ChannelProgressivePromise;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.DefaultHttpContent;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.handler.codec.http.multipart.Attribute;
import io.netty.handler.codec.http.multipart.DefaultHttpDataFactory;
import io.netty.handler.codec.http.multipart.FileUpload;
import io.netty.handler.codec.http.multipart.InterfaceHttpData;
import io.netty.handler.codec.http.multipart.HttpPostRequestDecoder;
import io.netty.handler.stream.ChunkedNioFile;

final class WebRequestHandler {
    private final Context context;
    private final Utils utils;
    private final ServerUtils serverUtils;
    private final FileOperations fileOperations;
    private final WebPasswordSettings passwordSettings;
    private final PluginAccessManager pluginAccess;
    private final LoginLimiter loginLimiter = new LoginLimiter();
    private volatile String root = Environment.getExternalStorageDirectory().getAbsolutePath();
    private volatile String pluginDevDir;
    private volatile boolean allowHiddenMedia = true;
    private volatile String currentParent = "";
    private final ExecutorService zipExecutor = new ThreadPoolExecutor(2, 4, 60, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(8), r -> {
                Thread thread = new Thread(r, "ShareX-Zip");
                thread.setDaemon(true);
                return thread;
            }, new ThreadPoolExecutor.AbortPolicy());

    WebRequestHandler(Context context, Utils utils, ServerUtils serverUtils) {
        this.context = context;
        this.utils = utils;
        this.serverUtils = serverUtils;
        this.fileOperations = new FileOperations(utils);
        this.passwordSettings = new WebPasswordSettings(context);
        this.pluginAccess = new PluginAccessManager(context);
    }

    void setRoot(String root) { this.root = root; }
    void setAllowHiddenMedia(boolean value) { allowHiddenMedia = value; }
    void setPluginDevDir(String value) { pluginDevDir = value; }
    void reportProgress(int value) {
        Intent intent = new Intent(Constants.BROADCAST_SERVICE_TO_ACTIVITY);
        intent.putExtra("action", Constants.ACTION_PROGRESS);
        intent.putExtra("value", value);
        intent.setPackage(context.getPackageName());
        context.sendBroadcast(intent);
    }

    ChannelInboundHandlerAdapter newChannelHandler() {
        return new RequestChannelHandler();
    }

    private final class RequestChannelHandler extends ChannelInboundHandlerAdapter {
        private HttpRequest request;
        private java.io.ByteArrayOutputStream passwordBody;
        private HttpPostRequestDecoder decoder;
        private File uploadDirectory;
        private long uploadId;
        private final Set<FileUpload> uploadParts = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());

        private void trackUploadBytes() {
            try {
                while (decoder.hasNext()) {
                    InterfaceHttpData part = decoder.next();
                    if (part instanceof FileUpload) uploadParts.add((FileUpload) part);
                }
            } catch (HttpPostRequestDecoder.EndOfDataDecoderException ignored) { }
            InterfaceHttpData partial = decoder.currentPartialHttpData();
            if (partial instanceof FileUpload) uploadParts.add((FileUpload) partial);
            long bytes = 0;
            for (FileUpload part : uploadParts) bytes += part.length();
            TransferStats.progress(uploadId, bytes);
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object message) {
            try {
                if (message instanceof HttpRequest) {
                    request = (HttpRequest) message;
                    String path = pathOf(request);
                    boolean login = path.equals("/ShareX/password") && HttpMethod.POST.equals(request.method());
                    if (login) {
                        if (!sameOrigin(request) || !approved(request, false)) {
                            reply(ctx, WebResponse.text(403, "Browser approval required"));
                            return;
                        }
                        passwordBody = new java.io.ByteArrayOutputStream();
                    } else if (protectedPath(path) && !isAuthRequest(request) && !isRequestAuthorized(request)) {
                        reply(ctx, unauthorizedResponse(request, ctx.pipeline().get("ssl") != null));
                        return;
                    }
                    if (passwordBody != null && message instanceof io.netty.handler.codec.http.HttpContent) {
                        readPassword(ctx, (io.netty.handler.codec.http.HttpContent)message);
                        return;
                    }
                    if (HttpMethod.POST.equals(request.method()) && pathOf(request).equals("/ShareX/uploadFile")) {
                        beginUpload(request);
                        if (message instanceof LastHttpContent) {
                            decoder.offer((io.netty.handler.codec.http.HttpContent) message);
                    trackUploadBytes();
                            finishUpload(ctx);
                        }
                        return;
                    }
                    if (message instanceof LastHttpContent) routeAndReply(ctx, request);
                } else if (message instanceof io.netty.handler.codec.http.HttpContent && passwordBody != null) {
                    readPassword(ctx, (io.netty.handler.codec.http.HttpContent)message);
                } else if (message instanceof io.netty.handler.codec.http.HttpContent && decoder != null) {
                    decoder.offer((io.netty.handler.codec.http.HttpContent) message);
                    trackUploadBytes();
                    if (message instanceof LastHttpContent) finishUpload(ctx);
                } else if (message instanceof LastHttpContent && request != null) {
                    routeAndReply(ctx, request);
                }
            } catch (SecurityException e) {
                cleanupUpload();
                reply(ctx, WebResponse.text(403, "Access denied"));
            } catch (HttpPostRequestDecoder.ErrorDataDecoderException | IllegalArgumentException e) {
                cleanupUpload();
                reply(ctx, WebResponse.text(400, "Malformed request"));
            } catch (Exception e) {
                cleanupUpload();
                Log.e(Constants.LOG_TAG, "HTTP request failed", e);
                reply(ctx, WebResponse.text(500, "Request failed"));
            } finally {
                if (message instanceof io.netty.util.ReferenceCounted) ((io.netty.util.ReferenceCounted) message).release();
            }
        }

        private void readPassword(ChannelHandlerContext ctx, io.netty.handler.codec.http.HttpContent content) throws Exception {
            if (passwordBody.size() + content.content().readableBytes() > 2048) {
                passwordBody = null;
                reply(ctx, WebResponse.text(413, "Password request too large"));
                return;
            }
            byte[] bytes = new byte[content.content().readableBytes()];
            content.content().getBytes(content.content().readerIndex(), bytes);
            passwordBody.write(bytes);
            if (content instanceof LastHttpContent) {
                byte[] body = passwordBody.toByteArray(); passwordBody = null;
                String address = ((java.net.InetSocketAddress)ctx.channel().remoteAddress()).getAddress().getHostAddress();
                reply(ctx, passwordLogin(request, body, address, ctx.pipeline().get("ssl") != null));
                java.util.Arrays.fill(body, (byte)0);
            }
        }

        private void beginUpload(HttpRequest httpRequest) throws Exception {
            boolean privateMode = utils.loadSetting(Constants.PRIVATE_MODE);
            if (privateMode) {
                uploadDirectory = new File(Environment.getExternalStorageDirectory(), "ShareX");
            } else {
                QueryStringDecoder query = new QueryStringDecoder(httpRequest.uri(), StandardCharsets.UTF_8);
                List<String> locations = query.parameters().get("location");
                String location = locations == null || locations.isEmpty() ? currentParent : locations.get(0);
                uploadDirectory = resolveInside(new File(root), location);
            }
            if (!uploadDirectory.isDirectory() && !uploadDirectory.mkdirs()) throw new IllegalArgumentException("Invalid upload folder");
            DefaultHttpDataFactory factory = new DefaultHttpDataFactory(true);
            File decoderDirectory = new File(context.getCacheDir(), "http-upload");
            if (!decoderDirectory.exists() && !decoderDirectory.mkdirs()) throw new IllegalStateException("Unable to create upload cache");
            factory.setBaseDir(decoderDirectory.getAbsolutePath());
            factory.setDeleteOnExit(false);
            factory.setMaxLimit(10L * 1024 * 1024 * 1024);
            decoder = new HttpPostRequestDecoder(factory, httpRequest, StandardCharsets.UTF_8);
            decoder.setDiscardThreshold(1024 * 1024);
            uploadId = TransferStats.begin(true, 0, "files");
        }

        private void finishUpload(ChannelHandlerContext ctx) throws Exception {
            if (!isAuthorized(request)) throw new SecurityException("Session expired");
            int count = 0;
            for (InterfaceHttpData item : decoder.getBodyHttpDatas()) {
                if (!(item instanceof io.netty.handler.codec.http.multipart.HttpData)) continue;
                io.netty.handler.codec.http.multipart.HttpData data = (io.netty.handler.codec.http.multipart.HttpData) item;
                if (!(data instanceof FileUpload)) continue;
                FileUpload upload = (FileUpload) data;
                if (!upload.isCompleted()) continue;
                String name = safeUploadName(upload.getFilename());
                File destination = new File(uploadDirectory, name);
                if (!destination.createNewFile()) destination = uniqueDestination(uploadDirectory, name);
                File partial = File.createTempFile(".sharex-upload-", ".part", uploadDirectory);
                try {
                    try (FileInputStream input = new FileInputStream(upload.getFile());
                         java.io.FileOutputStream output = new java.io.FileOutputStream(partial)) {
                        byte[] buffer = new byte[64 * 1024];
                        int read;
                        while ((read = input.read(buffer)) != -1) output.write(buffer, 0, read);
                    }
                } catch (Exception e) {
                    partial.delete();
                    destination.delete();
                    throw e;
                }
                if (!partial.renameTo(destination)) {
                    partial.delete();
                    destination.delete();
                    throw new IllegalStateException("Unable to store uploaded file");
                }
                String mime = utils.getMimeType(destination);
                if (mime != null && mime.startsWith("image")) utils.verifyImage(destination);
                else if (mime != null && mime.startsWith("video")) utils.verifyVideo(destination);
                pushHistory(destination, Constants.ITEM_TYPE_RECEIVED);
                sendLog("msg", "msg", "File received: " + name);
                count++;
            }
            String location = "storage/" + new File(root).toPath().relativize(uploadDirectory.toPath());
            sendLog("msg", "msg", "Total files received " + count + " and stored in " + location + " directory");
            TransferStats.finish(uploadId, true);
            cleanupUpload();
            reply(ctx, WebResponse.text(200, count + " Files Uploaded Successsfully!"));
        }

        private void cleanupUpload() {
            TransferStats.finish(uploadId, false);
            uploadId = 0;
            uploadParts.clear();
            if (decoder != null) {
                decoder.destroy();
                decoder = null;
            }
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) {
            cleanupUpload();
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            cleanupUpload();
            ctx.close();
        }
    }

    private void routeAndReply(ChannelHandlerContext ctx, HttpRequest request) throws Exception {
        if (protectedPath(pathOf(request)) && !isAuthRequest(request) && !isRequestAuthorized(request)) {
            reply(ctx, unauthorizedResponse(request, ctx.pipeline().get("ssl") != null));
            return;
        }
        if (ctx.pipeline().get("readTimeout") != null) ctx.pipeline().remove("readTimeout");
        if (!HttpMethod.GET.equals(request.method()) && !HttpMethod.HEAD.equals(request.method())) {
            reply(ctx, WebResponse.text(405, "Method not allowed"));
            return;
        }
        QueryStringDecoder query = new QueryStringDecoder(request.uri(), StandardCharsets.UTF_8);
        if (isPluginPermissionRequest(request)) {
            reply(ctx, pluginPermissionState(request, query.parameters(), ctx.pipeline().get("ssl") != null));
            return;
        }
        if (isAuthRequest(request)) {
            reply(ctx, authorizeBrowser(request, ctx.pipeline().get("ssl") != null));
            return;
        }
        WebResponse response = route(query.path(), query.parameters(), request.headers().get(HttpHeaderNames.RANGE), request.headers().get(HttpHeaderNames.USER_AGENT));
        if (protectedPath(query.path())) response.header("Cache-Control", "no-store");
        if (HttpMethod.HEAD.equals(request.method())) {
            long length = response.file != null ? response.length : response.bytes != null ? response.bytes.length
                    : response.body == null ? 0 : response.body.getBytes(StandardCharsets.UTF_8).length;
            WebResponse head = WebResponse.text(response.status, "");
            head.headers.putAll(response.headers);
            head.header("Content-Length", String.valueOf(length));
            reply(ctx, head);
            return;
        }
        reply(ctx, response);
    }

    private WebResponse route(String uri, Map<String, List<String>> params, String range, String userAgent) throws Exception {
        if (uri.equals("/ShareX")) {
            String action = param(params, "action");
            switch (action) {
                case "listFiles": {
                    String loc = param(params, "location");
                    boolean privateMode = utils.loadSetting(Constants.PRIVATE_MODE);
                    currentParent = privateMode ? "" : loc;
                    File directory = resolveInside(new File(root), loc);
                    if (!privateMode && !directory.isDirectory()) return WebResponse.text(404, "Can't read this location!");
                    return WebResponse.json(200, serverUtils.getFilesList(directory.getAbsolutePath(), allowHiddenMedia, root));
                }
                case "openFile": {
                    File file = resolveRequestedFile(param(params, "location"));
                    if (range == null) sendLog("msg", "msg", "Sending file: " + file.getName());
                    return serverUtils.serveFile(file.getPath(), true, range);
                }
                case "previewFile":
                case "viewImage":
                    return serverUtils.serveFile(resolveRequestedFile(param(params, "location")).getPath(), false, range);
                case "thumbImage": {
                    File file = resolveRequestedFile(param(params, "location"));
                    if (!file.isFile()) return WebResponse.text(404, "Thumbnail not found");
                    return serverUtils.serveThumbnail(file.getAbsolutePath());
                }
                case "delFiles":
                    if (utils.loadSetting(Constants.RESTRICT_MODIFY) || utils.loadSetting(Constants.PRIVATE_MODE)) return WebResponse.text(403, "File deletion is restricted!");
                    JSONArray deleteItems = new JSONArray(param(params, "data"));
                    StringBuilder deleted = new StringBuilder();
                    for (int i = 0; i < deleteItems.length(); i++) {
                        File file = resolveInside(new File(root), deleteItems.getString(i));
                        if (file.equals(new File(root).getCanonicalFile())) return WebResponse.text(403, "Cannot delete storage root");
                        if (!file.exists()) return WebResponse.text(404, "File not found");
                        utils.deleteFileOrDir(file);
                        deleted.append('\'').append(file.getName()).append("' ");
                    }
                    sendLog("msg", "msg", "Deleted File/Folder: " + deleted);
                    return WebResponse.text(200, deleteItems.length() + " files deleted successfully!;");
                case "downloadFiles":
                    return downloadFiles(new JSONArray(param(params, "data")), param(params, "filename"), range);
                case "renF":
                    if (utils.loadSetting(Constants.RESTRICT_MODIFY) || utils.loadSetting(Constants.PRIVATE_MODE)) return WebResponse.text(403, "File/Folder modification restricted!");
                    File old = resolveInside(new File(root), param(params, "old_n"));
                    if (old.equals(new File(root).getCanonicalFile())) return WebResponse.text(403, "Cannot rename storage root");
                    String newName = safeUploadName(param(params, "new_n"));
                    File parent = resolveInside(new File(root), param(params, "parent"));
                    File renamed = new File(parent, newName);
                    if (!old.exists() || renamed.exists() || !old.renameTo(renamed)) return WebResponse.text(409, "Unable to rename file or folder");
                    sendLog("msg", "msg", "File/folder renamed \"" + old.getName() + "\" to \"" + renamed.getName() + "\"");
                    return WebResponse.text(200, "File/Folder Renamed Successfully!;");
                case "newF":
                    if (utils.loadSetting(Constants.RESTRICT_MODIFY) || utils.loadSetting(Constants.PRIVATE_MODE)) return WebResponse.text(403, "File/Folder modification restricted!");
                    File newFolder = new File(resolveInside(new File(root), param(params, "parent")), safeUploadName(param(params, "name")));
                    if (!newFolder.mkdirs()) return WebResponse.text(409, "Unable to create folder");
                    sendLog("msg", "msg", "New folder created at " + newFolder.getAbsolutePath());
                    return WebResponse.text(200, "Folder Created Successfully!;");
                case "listFolders": {
                    if (utils.loadSetting(Constants.PRIVATE_MODE)) return WebResponse.text(403, "Folder browsing is restricted");
                    File directory = resolveInside(new File(root), param(params, "location"));
                    if (!directory.isDirectory()) return WebResponse.text(404, "Folder not found");
                    org.json.JSONObject listing = serverUtils.getFilesList(directory.getPath(), allowHiddenMedia, root);
                    JSONArray folders = new JSONArray();
                    JSONArray entries = listing.getJSONArray("items");
                    for (int i = 0; i < entries.length(); i++) {
                        if (entries.getJSONObject(i).getBoolean("directory")) folders.put(entries.getJSONObject(i));
                    }
                    return WebResponse.json(200, new org.json.JSONObject().put("items", folders));
                }
                case "transferFiles": {
                    if (utils.loadSetting(Constants.PRIVATE_MODE) || utils.loadSetting(Constants.RESTRICT_MODIFY)) return WebResponse.text(403, "File changes are restricted");
                    JSONArray requested = new JSONArray(param(params, "data"));
                    File allowedRoot = new File(root).getCanonicalFile();
                    File destination = resolveInside(allowedRoot, param(params, "destination"));
                    List<File> sources = new ArrayList<>();
                    for (int i = 0; i < requested.length(); i++) sources.add(resolveInside(allowedRoot, requested.getString(i)));
                    try {
                        return WebResponse.json(202, fileOperations.start(param(params, "mode"), sources, destination, allowedRoot));
                    } catch (java.util.concurrent.RejectedExecutionException busy) {
                        return WebResponse.text(503, "File operations are busy. Try again shortly.");
                    }
                }
                case "fileOperationStatus": {
                    org.json.JSONObject job = fileOperations.status(param(params, "id"));
                    return job == null ? WebResponse.text(404, "Operation not found") : WebResponse.json(200, job);
                }
                case "listApps": return WebResponse.json(200, serverUtils.getAppsList());
                case "getApp": {
                    String pkg = param(params, "pkg");
                    PackageManager manager = context.getPackageManager();
                    String appName = manager.getApplicationLabel(manager.getApplicationInfo(pkg, PackageManager.GET_META_DATA)).toString();
                    String appPath = manager.getApplicationInfo(pkg, PackageManager.GET_META_DATA).sourceDir;
                    sendLog("msg", "msg", "Sending App: " + appName);
                    return serverUtils.serveApp(appName + ".apk", appPath, pkg, range);
                }
                case "getInfo": return WebResponse.text(200, serverUtils.getInfo());
                case "getState": return WebResponse.json(200, serverUtils.getPortalState());
                case "getUploadLocation": return WebResponse.text(200, utils.loadSetting(Constants.PRIVATE_MODE) ? "storage/ShareX" : "storage" + normalizedParent());
                case "getPrivateMode": return WebResponse.text(200, String.valueOf(utils.loadSetting(Constants.PRIVATE_MODE)));
                case "getInstalledPlugins": return WebResponse.json(200, new JSONArray(serverUtils.getPluginsList()));
                default: return WebResponse.text(200, "");
            }
        }
        if (uri.startsWith("/ShareX/thumbnail/app/")) {
            String pkg = uri.substring(uri.lastIndexOf('/') + 1);
            if (!pkg.matches("[A-Za-z0-9_.]+")) return WebResponse.text(400, "Invalid package name");
            File icon = new File(Environment.getExternalStorageDirectory(), "ShareX/.thumbs/" + pkg + ".png");
            return staticFile(icon, false);
        }
        if (uri.startsWith("/SharexApp/")) return pluginFile(uri);
        return staticAsset(uri);
    }

    private static boolean protectedPath(String path) {
        return path.equals("/ShareX") || path.startsWith("/ShareX/") || path.startsWith("/SharexApp/");
    }
    private boolean isRequestAuthorized(HttpRequest request) {
        String path = pathOf(request);
        if (path.startsWith("/SharexApp/")) {
            com.akansh.plugins.common.Plugin plugin = pluginForPath(path);
            return plugin != null && pluginAccess.isGranted(browserId(request), plugin.getPlugin_package_name());
        }
        return isAuthorized(request);
    }
    private boolean isPluginPermissionRequest(HttpRequest request) {
        QueryStringDecoder query = new QueryStringDecoder(request.uri(), StandardCharsets.UTF_8);
        return HttpMethod.GET.equals(request.method()) && query.path().equals("/ShareX")
                && "pluginPermission".equals(param(query.parameters(), "action"));
    }
    private com.akansh.plugins.common.Plugin pluginForPath(String path) {
        String uid = Utils.extractPluginUID(path);
        return uid == null ? null : serverUtils.getEnabledPlugin(uid);
    }
    private WebResponse unauthorizedResponse(HttpRequest request, boolean ssl) {
        String path = pathOf(request);
        if (path.startsWith("/SharexApp/")) {
            com.akansh.plugins.common.Plugin plugin = pluginForPath(path);
            if (plugin != null) return pluginPermissionPage(request, plugin, ssl);
            return WebResponse.text(403, "Plugin unavailable").header("Cache-Control", "no-store");
        }
        return WebResponse.text(401, "Authentication required").header("Cache-Control", "no-store");
    }
    private WebResponse pluginPermissionPage(HttpRequest request, com.akansh.plugins.common.Plugin plugin, boolean ssl) {
        String token = cookie(request, "sx_browser");
        boolean newIdentity = WebSecurity.browserId(token).isEmpty();
        if (newIdentity) token = WebSecurity.randomToken();
        String name = plugin.getPlugin_name().replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
        String html = "<!doctype html><html lang=\"en\"><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\"><title>Plugin permission</title>"
                + "<style>body{font:16px system-ui;margin:0;background:#f4f6fa;color:#18202c;min-height:100vh;display:grid;place-items:center}.card{max-width:34rem;margin:1rem;padding:2rem;background:white;border-radius:1.25rem;box-shadow:0 12px 40px #16243a18}h1{font-size:1.5rem}p{line-height:1.55;color:#455166}.state{font-weight:600;color:#2459aa}</style>"
                + "<main class=\"card\"><p>SHAREX PLUGINS</p><h1>Permission needed</h1><p><strong>" + name
                + "</strong> is asking to connect to ShareX for its own plugin messaging and storage.</p><p>This does not grant access to your shared files. Opening ShareX file manager requires separate approval.</p>"
                + "<p class=\"state\" id=\"state\">Waiting for ShareX permission…</p><p>Check your phone and allow or deny this plugin.</p></main>"
                + "<script>const state=document.getElementById('state');async function check(){try{const r=await fetch('/ShareX?action=pluginPermission&package="
                + plugin.getPlugin_package_name() + "',{cache:'no-store',credentials:'same-origin'});const j=await r.json();if(j.state==='granted'){state.textContent='Permission granted. Opening plugin…';location.replace(location.pathname+location.search+location.hash);return}if(j.state==='denied'){state.textContent='Permission denied. Close this page or ask ShareX to allow the plugin.';return}}catch(e){state.textContent='Cannot reach ShareX. Check that sharing is running.'}setTimeout(check,1500)}check();</script></html>";
        WebResponse response = WebResponse.text(200, html).header("Content-Type", "text/html; charset=utf-8").header("Cache-Control", "no-store");
        if (newIdentity) response.header("Set-Cookie", setCookie("sx_browser", token, 365L*24*60*60, ssl));
        return response;
    }
    private WebResponse pluginPermissionState(HttpRequest request, Map<String, List<String>> params, boolean ssl) {
        if (!sameOrigin(request)) return pluginStateJson(403, "denied");
        List<String> requestedPackages = params.get("package");
        String packageName = requestedPackages == null || requestedPackages.size() != 1 ? null : requestedPackages.get(0);
        if (packageName == null || !packageName.matches("[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)*")) return pluginStateJson(400, "denied");
        com.akansh.plugins.common.Plugin plugin = serverUtils.getEnabledPluginByPackage(packageName);
        String device = browserId(request);
        if (plugin == null || device.isEmpty()) return pluginStateJson(403, "denied");
        String state = pluginAccess.state(device, packageName);
        if (state == null && pluginAccess.markPending(device, packageName)) {
            Intent approval = new Intent(Constants.BROADCAST_SERVICE_TO_ACTIVITY);
            approval.setPackage(context.getPackageName());
            approval.putExtra("action", Constants.ACTION_PLUGIN_AUTH);
            approval.putExtra("device_id", device);
            approval.putExtra("plugin_package", packageName);
            approval.putExtra("plugin_name", plugin.getPlugin_name());
            context.sendBroadcast(approval);
            state = "pending";
        } else if (state == null) state = "pending";
        WebResponse response = pluginStateJson(200, state);
        if (cookie(request, "sx_browser").isEmpty()) {
            String token = WebSecurity.randomToken();
            response.header("Set-Cookie", setCookie("sx_browser", token, 365L*24*60*60, ssl));
        }
        return response;
    }
    private static WebResponse pluginStateJson(int status, String state) {
        return WebResponse.text(status, "{\"state\":\"" + state + "\"}")
                .header("Content-Type", "application/json; charset=utf-8")
                .header("Cache-Control", "no-store");
    }
    private static boolean isAuthRequest(HttpRequest request) {
        QueryStringDecoder query = new QueryStringDecoder(request.uri(), StandardCharsets.UTF_8);
        return query.path().equals("/ShareX") && ("auth".equals(param(query.parameters(), "action"))
                || "pluginPermission".equals(param(query.parameters(), "action")))
                && (HttpMethod.GET.equals(request.method()) || HttpMethod.HEAD.equals(request.method()));
    }
    static boolean sameOrigin(HttpRequest request) {
        String site = request.headers().get("Sec-Fetch-Site");
        if (site != null && !"same-origin".equals(site) && !"none".equals(site)) return false;
        String origin = request.headers().get("Origin");
        if (origin == null) return true;
        try {
            java.net.URI uri = java.net.URI.create(origin);
            return ("http".equals(uri.getScheme()) || "https".equals(uri.getScheme()))
                    && uri.getRawAuthority() != null && uri.getRawAuthority().equalsIgnoreCase(request.headers().get("Host"));
        } catch (RuntimeException invalid) { return false; }
    }
    private static String cookie(HttpRequest request, String name) {
        String header = request.headers().get(HttpHeaderNames.COOKIE);
        if (header == null || header.length() > 8192) return "";
        String value = "";
        for (io.netty.handler.codec.http.cookie.Cookie item : io.netty.handler.codec.http.cookie.ServerCookieDecoder.STRICT.decodeAll(header)) {
            if (name.equals(item.name())) { if (!value.isEmpty()) return ""; value = item.value(); }
        }
        return value;
    }
    private static String browserId(HttpRequest request) { return WebSecurity.browserId(cookie(request, "sx_browser")); }
    private boolean approved(HttpRequest request, boolean permanentOnly) {
        String id = browserId(request);
        if (id.isEmpty()) return false;
        try (DeviceManager manager = new DeviceManager(context)) { return manager.isSecureApproved(id, permanentOnly); }
    }
    boolean isAuthorized(HttpRequest request) {
        return sameOrigin(request) && approved(request, false) && passwordAuthenticated(request);
    }
    boolean isSocketAuthorized(HttpRequest request) {
        if (new QueryStringDecoder(request.uri()).path().equals(PluginDevelopment.SOCKET_PATH)) {
            return new PluginDevelopment(context).socketPackage(request) != null;
        }
        String origin = request.headers().get("Origin");
        // Browser WebSockets use the HTTP portal's origin, not the socket port.
        if (origin != null && !origin.equals(utils.loadString(Constants.SERVER_URL))) return false;
        return (approved(request, false) && passwordAuthenticated(request)) || pluginAccess.hasAnyGrant(browserId(request));
    }
    boolean isPluginSocketAuthorized(HttpRequest request, String packageName) {
        if (packageName == null || !packageName.matches("[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)*")) return false;
        com.akansh.plugins.common.Plugin plugin = serverUtils.getEnabledPluginByPackage(packageName);
        if (plugin != null) return "granted".equals(pluginAccess.state(browserId(request), packageName));
        return isAuthorized(request);
    }
    private boolean passwordAuthenticated(HttpRequest request) {
        if (!passwordSettings.enabled()) return true;
        if (passwordSettings.exemptRemembered() && approved(request, true)) return true;
        return WebSecurity.validSession(cookie(request, "sx_password"), browserId(request), passwordSettings.revision(),
                System.currentTimeMillis(), passwordSettings.duration(), passwordSettings.secret());
    }
    private static String setCookie(String name, String value, long seconds, boolean ssl) {
        return name + "=" + value + "; Path=/; Max-Age=" + seconds + "; HttpOnly; SameSite=Strict" + (ssl ? "; Secure" : "");
    }
    private WebResponse authorizeBrowser(HttpRequest request, boolean ssl) {
        if (!sameOrigin(request)) return WebResponse.text(403, "Access denied");
        String id = browserId(request);
        if (id.isEmpty()) {
            String token = WebSecurity.randomToken();
            return WebResponse.text(200, "false").header("Cache-Control", "no-store")
                    .header("Set-Cookie", setCookie("sx_browser", token, 365L*24*60*60, ssl));
        }
        String name = BrowserName.fromUserAgent(request.headers().get(HttpHeaderNames.USER_AGENT));
        try (DeviceManager manager = new DeviceManager(context)) {
            if (manager.isSecureApproved(id, false)) {
                manager.recordConnection(id, name);
                return WebResponse.text(200, passwordAuthenticated(request) ? "true" : "password").header("Cache-Control", "no-store");
            }
            if (manager.isDeviceDenied(id)) return WebResponse.text(200, "denied").header("Cache-Control", "no-store");
        }
        Intent approval = new Intent(Constants.BROADCAST_SERVICE_TO_ACTIVITY);
        approval.setPackage(context.getPackageName());
        approval.putExtra("action", Constants.ACTION_AUTH);
        approval.putExtra("device_id", id);
        approval.putExtra("device_name", name);
        context.sendBroadcast(approval);
        return WebResponse.text(200, "false").header("Cache-Control", "no-store");
    }
    private WebResponse passwordLogin(HttpRequest request, byte[] body, String address, boolean ssl) throws Exception {
        if (!sameOrigin(request) || !approved(request, false)) return WebResponse.text(403, "Browser approval required");
        if (!loginLimiter.allow(address, android.os.SystemClock.elapsedRealtime())) return WebResponse.text(429, "Too many attempts. Try again in a minute.").header("Retry-After", "60");
        String revision = passwordSettings.revision();
        char[] password;
        try { password = new org.json.JSONObject(new String(body, StandardCharsets.UTF_8)).getString("password").toCharArray(); }
        catch (org.json.JSONException malformed) { return WebResponse.text(400, "Invalid password request"); }
        boolean valid;
        try { valid = password.length <= 128 && WebSecurity.verifyPassword(password, passwordSettings.passwordHash()); }
        finally { java.util.Arrays.fill(password, '\0'); }
        if (!valid || !revision.equals(passwordSettings.revision())) return WebResponse.text(401, "Incorrect password").header("Cache-Control", "no-store");
        long duration = passwordSettings.duration();
        String session = WebSecurity.session(browserId(request), revision, System.currentTimeMillis(), duration, passwordSettings.secret());
        return WebResponse.text(200, "true").header("Cache-Control", "no-store")
                .header("Set-Cookie", setCookie("sx_password", session, duration/1000, ssl));
    }

    private WebResponse downloadFiles(JSONArray requested, String zipName, String range) throws Exception {
        if (requested.length() == 0) return WebResponse.text(400, "No files selected");
        File first = resolveRequestedFile(requested.getString(0));
        if (requested.length() == 1 && first.isFile()) {
            sendLog("msg", "msg", "Sending " + first.getName() + " to download...");
            return serverUtils.downloadFile(first.getAbsolutePath(), true, true, range);
        }
        List<WebResponse.ZipEntrySource> sources = new ArrayList<>();
        File allowedRoot = utils.loadSetting(Constants.PRIVATE_MODE) ? null : new File(root).getCanonicalFile();
        boolean privateMode = utils.loadSetting(Constants.PRIVATE_MODE);
        for (int i = 0; i < requested.length(); i++) {
            File source = resolveRequestedFile(requested.getString(i));
            if (!source.exists()) continue;
            boolean covered = false;
            for (int j = sources.size() - 1; j >= 0; j--) {
                File selected = sources.get(j).file.getCanonicalFile();
                if (isInside(selected, source.getCanonicalFile())) {
                    covered = true;
                    break;
                }
                if (isInside(source.getCanonicalFile(), selected)) sources.remove(j);
            }
            if (covered) continue;
            String entryName = source.getName();
            for (int suffix = 1; containsEntryName(sources, entryName); suffix++) entryName = source.getName() + " (" + suffix + ")";
            sources.add(new WebResponse.ZipEntrySource(source, entryName, source.isDirectory()));
            pushHistory(source, Constants.ITEM_TYPE_SENT);
        }
        if (sources.isEmpty()) return WebResponse.text(404, "No files found");
        String safeZipName = zipName.replaceAll("[^A-Za-z0-9_-]", "_");
        sendLog("msg", "msg", "Preparing ZIP download...");
        return WebResponse.zip(sources, allowedRoot, privateMode)
                .header("Content-Type", "application/zip")
                .header("Content-Disposition", "attachment; filename=\"ShareX_zip_" + safeZipName + ".zip\"");
    }

    private boolean containsEntryName(List<WebResponse.ZipEntrySource> sources, String name) {
        for (WebResponse.ZipEntrySource source : sources) if (source.entryName.equals(name)) return true;
        return false;
    }

    private WebResponse pluginFile(String uri) throws Exception {
        boolean debugRoute = uri.equals("/SharexApp/debug") || uri.startsWith("/SharexApp/debug/");
        boolean pluginDebug = utils.loadSetting(Constants.PLUGIN_DEV) && debugRoute;
        if (!utils.loadSetting(Constants.PLUGIN_DEV) && debugRoute) return WebResponse.text(400, "400 Bad Request! Please enable plugin development mode in settings first!");
        String pluginUid = pluginDebug ? "debug" : Utils.extractPluginUID(uri);
        if (pluginUid == null || (!pluginDebug && !serverUtils.getPluginStatus(pluginUid))) return WebResponse.text(403, "403 Access Denied!");
        Matcher matcher = Pattern.compile("\\.([^.]+)$").matcher(uri);
        if (!matcher.find() && uri.endsWith(pluginUid) && !uri.endsWith("/")) return WebResponse.text(302, "").header("Location", uri + "/");
        String[] parts = uri.split(Pattern.quote(pluginUid), 2);
        String pluginUri = parts.length == 1 ? "/" : parts[1];
        File base = new File(utils.getPluginFileProperPath("/", pluginUid));
        File file = resolveInside(base, pluginUri);
        if (file.isDirectory()) file = new File(file, "index.html");
        else if (!file.exists() && new File(file.getPath() + ".html").exists()) file = new File(file.getPath() + ".html");
        if (!file.isFile()) return WebResponse.text(404, "404 Not Found");
        if (file.getName().endsWith(".html")) {
            TemplateEngine engine = new TemplateEngine(context);
            if (pluginDevDir != null) engine.setPlugin_dev_dir(pluginDevDir);
            engine.setPlugin_uid(pluginUid);
            engine.setBase_url(parts.length == 1 ? pluginUid : ".");
            return WebResponse.text(200, engine.renderHtml(file.getAbsolutePath(), TemplateEngine.RENDER_TYPE.PLUGIN)).header("Content-Type", "text/html; charset=utf-8");
        }
        return staticFile(file, false);
    }

    private WebResponse staticAsset(String uri) throws Exception {
        String assetUri = uri.equals("/") ? "index.html" : uri;
        File file = new File(utils.getFileProperPath(assetUri));
        File assetRoot = new File(utils.getFileProperPath(".")).getCanonicalFile();
        if (!isInside(assetRoot, file.getCanonicalFile())) return WebResponse.text(403, "Access denied");
        if (file.isDirectory()) file = new File(file, "index.html");
        if (!isInside(assetRoot, file.getCanonicalFile())) return WebResponse.text(403, "Access denied");
        return staticFile(file, false).header("Cache-Control", "no-cache");
    }

    private WebResponse staticFile(File file, boolean progressEnabled) {
        if (!file.isFile() || !file.canRead()) return WebResponse.text(404, "Not found");
        String mime = utils.getMimeType(file);
        if (file.getName().endsWith(".html")) mime = "text/html; charset=utf-8";
        else if (file.getName().endsWith(".js") || file.getName().endsWith(".mjs")) mime = "text/javascript; charset=utf-8";
        else if (file.getName().endsWith(".css")) mime = "text/css; charset=utf-8";
        else if (file.getName().endsWith(".json")) mime = "application/json; charset=utf-8";
        else if (file.getName().endsWith(".wasm")) mime = "application/wasm";
        if (mime == null) mime = "application/octet-stream";
        return WebResponse.file(200, file, 0, file.length(), progressEnabled).header("Content-Type", mime);
    }

    private File resolveRequestedFile(String location) throws Exception {
        File file;
        if (utils.loadSetting(Constants.PRIVATE_MODE)) {
            file = new File(location);
            if (!file.isAbsolute()) file = new File(root, location);
            file = file.getCanonicalFile();
            if (!serverUtils.isInPrivateFiles(file.getAbsolutePath())) throw new SecurityException("File is not shared");
        } else {
            file = resolveInside(new File(root), location);
        }
        return file;
    }

    private File resolveInside(File base, String relative) throws Exception {
        String clean = relative.replace('\\', '/');
        while (clean.startsWith("/")) clean = clean.substring(1);
        File canonicalBase = base.getCanonicalFile();
        File candidate = new File(canonicalBase, clean).getCanonicalFile();
        if (!isInside(canonicalBase, candidate)) throw new SecurityException("Path escapes storage root");
        return candidate;
    }

    private boolean isInside(File base, File candidate) {
        String basePath = base.getPath();
        String target = candidate.getPath();
        return target.equals(basePath) || target.startsWith(basePath + File.separator);
    }

    private static String param(Map<String, List<String>> params, String key) {
        List<String> values = params.get(key);
        if (values == null || values.isEmpty()) throw new IllegalArgumentException("Missing parameter: " + key);
        return values.get(0);
    }

    private String pathOf(HttpRequest request) {
        return new QueryStringDecoder(request.uri(), StandardCharsets.UTF_8).path();
    }

    private String normalizedParent() {
        String value = currentParent;
        if (value == null || value.isEmpty()) return "/";
        if (!value.startsWith("/")) value = "/" + value;
        return value.replaceAll("/{2,}", "/");
    }

    private String safeUploadName(String name) {
        if (name == null) throw new IllegalArgumentException("Missing filename");
        String leaf = name.replace('\\', '/');
        leaf = leaf.substring(leaf.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}]", "_").trim();
        if (leaf.isEmpty() || leaf.equals(".") || leaf.equals("..")) throw new IllegalArgumentException("Invalid filename");
        return leaf;
    }

    private File uniqueDestination(File directory, String name) throws Exception {
        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name;
        String ext = dot > 0 ? name.substring(dot) : "";
        for (int i = 1; i < 10000; i++) {
            File candidate = new File(directory, base + " (" + i + ")" + ext);
            if (candidate.createNewFile()) return candidate;
        }
        throw new IllegalStateException("Unable to choose upload filename");
    }

    private void pushHistory(File file, int itemType) {
        Calendar calendar = Calendar.getInstance();
        SimpleDateFormat date = new SimpleDateFormat("dd/MM/yyyy", Locale.ENGLISH);
        SimpleDateFormat time = new SimpleDateFormat("hh:mm a", Locale.ENGLISH);
        String mime = utils.getMimeType(file);
        new HistoryDBManager(context).addTransferHistory(itemType, file.getName(), serverUtils.fileSize(file), date.format(calendar.getTime()), time.format(calendar.getTime()), mime == null ? "folder" : mime, file.getAbsolutePath());
    }

    void shutdown() {
        zipExecutor.shutdownNow();
        fileOperations.shutdown();
    }

    private void sendLog(String action, String key, String value) {
        Intent intent = new Intent(Constants.BROADCAST_SERVICE_TO_ACTIVITY);
        intent.putExtra("action", action);
        intent.putExtra(key, value);
        intent.setPackage(context.getPackageName());
        context.sendBroadcast(intent);
    }

    private void reply(ChannelHandlerContext ctx, WebResponse response) {
        if (response == null) response = WebResponse.text(500, "Request failed");
        if (response.zipSources != null) {
            streamZip(ctx, response);
            return;
        }
        if (response.file != null) {
            streamFile(ctx, response);
            return;
        }
        ByteBuf content = response.bytes != null ? Unpooled.wrappedBuffer(response.bytes)
                : Unpooled.copiedBuffer(response.body == null ? "" : response.body, StandardCharsets.UTF_8);
        FullHttpResponse httpResponse = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1,
                HttpResponseStatus.valueOf(response.status), content);
        applyHeaders(httpResponse, response);
        if (!httpResponse.headers().contains(HttpHeaderNames.CONTENT_LENGTH)) {
            httpResponse.headers().setInt(HttpHeaderNames.CONTENT_LENGTH, content.readableBytes());
        }
        httpResponse.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE);
        ctx.writeAndFlush(httpResponse).addListener(ChannelFutureListener.CLOSE);
    }

    private void streamFile(ChannelHandlerContext ctx, WebResponse response) {
        HttpResponse head = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.valueOf(response.status));
        applyHeaders(head, response);
        head.headers().set(HttpHeaderNames.CONTENT_LENGTH, Long.toString(response.length));
        head.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE);
        ctx.write(head);
        if (response.length == 0) {
            ctx.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT).addListener(ChannelFutureListener.CLOSE);
            return;
        }
        try {
            java.io.RandomAccessFile source = new java.io.RandomAccessFile(response.file, "r");
            ChunkedNioFile file = new ChunkedNioFile(source.getChannel(), response.offset, response.length, 64 * 1024);
            ChannelProgressivePromise promise = ctx.newProgressivePromise();
            long transferId = response.reportProgress
                    ? TransferStats.begin(false, response.length, response.file.getName()) : 0;
            promise.addListener(new ChannelProgressiveFutureListener() {
                @Override public void operationProgressed(ChannelProgressiveFuture future, long transferred, long total) {
                    if (transferId != 0) TransferStats.progress(transferId, transferred);
                    if (response.reportProgress && total > 0) serverUtils.sendProgressListenerUpdate((int) Math.min(100, transferred * 100 / total));
                }
                @Override public void operationComplete(ChannelProgressiveFuture future) {
                    TransferStats.finish(transferId, future.isSuccess());
                }
            });
            ctx.write(file, promise).addListener(future -> {
                if (future.isSuccess() && ctx.channel().isActive()) {
                    ctx.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT).addListener(ChannelFutureListener.CLOSE);
                } else {
                    file.close();
                    ctx.close();
                }
            });
            ctx.flush();
        } catch (Exception e) {
            Log.e(Constants.LOG_TAG, "File transfer failed", e);
            ctx.close();
        }
    }

    private void applyHeaders(HttpResponse target, WebResponse source) {
        source.headers.forEach((name, value) -> target.headers().set(name, value));
    }

    private void streamZip(ChannelHandlerContext ctx, WebResponse response) {
        ZipStream stream = new ZipStream(ctx, response);
        try {
            zipExecutor.execute(stream::generate);
        } catch (java.util.concurrent.RejectedExecutionException e) {
            stream.cancel();
            reply(ctx, WebResponse.text(503, "ZIP downloads are busy. Retry shortly."));
            return;
        }
        HttpResponse head = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
        applyHeaders(head, response);
        head.headers().set(HttpHeaderNames.TRANSFER_ENCODING, HttpHeaderValues.CHUNKED);
        head.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE);
        ctx.writeAndFlush(head).addListener(future -> {
            if (!future.isSuccess()) {
                ctx.close();
                return;
            }
            ctx.channel().closeFuture().addListener(ignored -> stream.cancel());
            stream.headersReady();
        });
    }

    private final class ZipStream {
        private static final int CHUNK_SIZE = 64 * 1024;
        private static final Object FINISHED = new Object();
        private final ChannelHandlerContext context;
        private final WebResponse response;
        private final BlockingQueue<Object> chunks = new ArrayBlockingQueue<>(4);
        private final AtomicBoolean cancelled = new AtomicBoolean();
        private final AtomicBoolean generatorFinished = new AtomicBoolean();
        private final AtomicBoolean pumpActive = new AtomicBoolean();
        private final AtomicBoolean headersAreReady = new AtomicBoolean();
        private final AtomicBoolean generationFailed = new AtomicBoolean();
        private long processed;
        private long totalBytes;
        private long networkBytes;
        private final long transferId = TransferStats.begin(false, 0, "archive");

        ZipStream(ChannelHandlerContext context, WebResponse response) {
            this.context = context;
            this.response = response;
        }

        void generate() {
            Set<String> scanStack = new HashSet<>();
            try (java.util.zip.ZipOutputStream zip = new java.util.zip.ZipOutputStream(new QueueOutputStream())) {
                for (WebResponse.ZipEntrySource source : response.zipSources) {
                    totalBytes += measureSource(source.file, scanStack);
                }
                serverUtils.sendProgressListenerUpdate(0);
                byte[] buffer = new byte[CHUNK_SIZE];
                Set<String> directoryStack = new HashSet<>();
                for (WebResponse.ZipEntrySource source : response.zipSources) {
                    if (cancelled.get()) break;
                    writeSource(zip, source.file, safeZipEntry(source.entryName), buffer, directoryStack);
                }
                if (!cancelled.get()) {
                    zip.finish();
                    serverUtils.sendProgressListenerUpdate(100);
                }
            }
            catch (Exception e) {
                if (!cancelled.get()) {
                    generationFailed.set(true);
                    Log.e(Constants.LOG_TAG, "ZIP stream failed", e);
                }
            } finally {
                generatorFinished.set(true);
                offer(FINISHED);
                schedulePump();
            }
        }

        private long measureSource(File input, Set<String> directoryStack) throws Exception {
            if (cancelled.get() || !isZipSourceAllowed(input)) return 0;
            File file = input.getCanonicalFile();
            if (file.isDirectory()) {
                if (!directoryStack.add(file.getPath())) return 0;
                long total = 0;
                try {
                    try (DirectoryStream<Path> children = Files.newDirectoryStream(file.toPath())) {
                        for (Path child : children) total += measureSource(child.toFile(), directoryStack);
                    }
                } finally {
                    directoryStack.remove(file.getPath());
                }
                return total;
            }
            return file.length();
        }

        private void writeSource(java.util.zip.ZipOutputStream zip, File input, String entryName, byte[] buffer,
                                 Set<String> directoryStack) throws Exception {
            if (cancelled.get() || !isZipSourceAllowed(input)) return;
            File file = input.getCanonicalFile();
            if (file.isDirectory()) {
                if (!directoryStack.add(file.getPath())) return;
                String directoryName = entryName.endsWith("/") ? entryName : entryName + "/";
                try {
                    zip.putNextEntry(new java.util.zip.ZipEntry(directoryName));
                    zip.closeEntry();
                    try (DirectoryStream<Path> children = Files.newDirectoryStream(file.toPath())) {
                        for (Path child : children) writeSource(zip, child.toFile(), directoryName + safeZipEntry(child.getFileName().toString()), buffer, directoryStack);
                    }
                } finally {
                    directoryStack.remove(file.getPath());
                }
                return;
            }
            zip.setLevel(isAlreadyCompressed(entryName) ? java.util.zip.Deflater.NO_COMPRESSION : java.util.zip.Deflater.DEFAULT_COMPRESSION);
            zip.putNextEntry(new java.util.zip.ZipEntry(entryName));
            try (FileInputStream inputStream = new FileInputStream(file)) {
                int count;
                while (!cancelled.get() && (count = inputStream.read(buffer)) != -1) {
                    zip.write(buffer, 0, count);
                    processed += count;
                    reportZipProgress();
                }
            }
            zip.closeEntry();
        }

        private boolean isZipSourceAllowed(File file) throws Exception {
            File canonical = file.getCanonicalFile();
            if (response.privateMode) return serverUtils.isInPrivateFiles(canonical.getAbsolutePath());
            return response.allowedRoot != null && isInside(response.allowedRoot, canonical);
        }

        private String safeZipEntry(String name) {
            return name.replace('\\', '_').replaceAll("[\\p{Cntrl}]", "_");
        }

        private void reportZipProgress() {
            if (totalBytes <= 0) serverUtils.sendProgressListenerUpdate(100);
            else serverUtils.sendProgressListenerUpdate((int) Math.min(100, processed * 100 / totalBytes));
        }

        private boolean isAlreadyCompressed(String name) {
            String lower = name.toLowerCase(Locale.ROOT);
            return lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png") || lower.endsWith(".gif")
                    || lower.endsWith(".webp") || lower.endsWith(".mp4") || lower.endsWith(".mkv") || lower.endsWith(".mov")
                    || lower.endsWith(".avi") || lower.endsWith(".apk") || lower.endsWith(".pdf") || lower.endsWith(".zip")
                    || lower.endsWith(".gz") || lower.endsWith(".7z") || lower.endsWith(".rar");
        }

        private void offer(Object value) {
            while (!cancelled.get()) {
                try {
                    if (chunks.offer(value, 100, TimeUnit.MILLISECONDS)) return;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    cancel();
                    return;
                }
            }
        }

        void cancel() {
            TransferStats.finish(transferId, false);
            if (cancelled.compareAndSet(false, true)) {
                chunks.clear();
                schedulePump();
            }
        }

        void headersReady() {
            headersAreReady.set(true);
            schedulePump();
        }

        private void schedulePump() {
            if (!pumpActive.compareAndSet(false, true)) return;
            context.executor().execute(this::pump);
        }

        private void pump() {
            try {
                if (!context.channel().isActive() || cancelled.get()) {
                    cancel();
                    return;
                }
                if (!headersAreReady.get()) return;
                Object chunk = chunks.poll();
                if (chunk instanceof byte[]) {
                    ChannelFuture write = context.writeAndFlush(new DefaultHttpContent(Unpooled.wrappedBuffer((byte[]) chunk)));
                    write.addListener(future -> {
                        if (future.isSuccess()) {
                            networkBytes += ((byte[]) chunk).length;
                            TransferStats.progress(transferId, networkBytes);
                            schedulePump();
                        } else cancel();
                    });
                } else if (chunk == FINISHED || generatorFinished.get() && chunks.isEmpty()) {
                    if (generationFailed.get()) context.close();
                    else context.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT).addListener(future -> {
                        TransferStats.finish(transferId, future.isSuccess());
                        context.close();
                    });
                }
            } finally {
                pumpActive.set(false);
            }
        }

        private final class QueueOutputStream extends java.io.OutputStream {
            private final byte[] buffer = new byte[CHUNK_SIZE];
            private int used;

            @Override public void write(int value) {
                buffer[used++] = (byte) value;
                if (used == buffer.length) flushChunk();
            }

            @Override public void write(byte[] source, int offset, int length) {
                while (length > 0 && !cancelled.get()) {
                    int count = Math.min(length, buffer.length - used);
                    System.arraycopy(source, offset, buffer, used, count);
                    offset += count;
                    length -= count;
                    used += count;
                    if (used == buffer.length) flushChunk();
                }
            }

            @Override public void flush() { if (used > 0) flushChunk(); }
            @Override public void close() { flush(); }

            private void flushChunk() {
                if (used == 0 || cancelled.get()) return;
                byte[] chunk = java.util.Arrays.copyOf(buffer, used);
                used = 0;
                offer(chunk);
                schedulePump();
            }
        }
    }
}
