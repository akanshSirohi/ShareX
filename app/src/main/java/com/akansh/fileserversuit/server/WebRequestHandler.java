package com.akansh.fileserversuit.server;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Environment;
import android.util.Log;

import com.akansh.fileserversuit.common.Constants;
import com.akansh.fileserversuit.common.Utils;
import com.akansh.fileserversuit.transfer_history.HistoryDBManager;

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
    }

    void setRoot(String root) { this.root = root; }
    void setAllowHiddenMedia(boolean value) { allowHiddenMedia = value; }
    void setPluginDevDir(String value) { pluginDevDir = value; }
    void reportProgress(int value) {
        Intent intent = new Intent(Constants.BROADCAST_SERVICE_TO_ACTIVITY);
        intent.putExtra("action", Constants.ACTION_PROGRESS);
        intent.putExtra("value", value);
        context.sendBroadcast(intent);
    }

    ChannelInboundHandlerAdapter newChannelHandler() {
        return new RequestChannelHandler();
    }

    private final class RequestChannelHandler extends ChannelInboundHandlerAdapter {
        private HttpRequest request;
        private HttpPostRequestDecoder decoder;
        private File uploadDirectory;

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object message) {
            try {
                if (message instanceof HttpRequest) {
                    request = (HttpRequest) message;
                    if (HttpMethod.POST.equals(request.method()) && pathOf(request).equals("/ShareX/uploadFile")) {
                        beginUpload(request);
                        if (message instanceof LastHttpContent) {
                            decoder.offer((io.netty.handler.codec.http.HttpContent) message);
                            finishUpload(ctx);
                        }
                        return;
                    }
                    if (message instanceof LastHttpContent) routeAndReply(ctx, request);
                } else if (message instanceof io.netty.handler.codec.http.HttpContent && decoder != null) {
                    decoder.offer((io.netty.handler.codec.http.HttpContent) message);
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

        private void beginUpload(HttpRequest httpRequest) throws Exception {
            boolean privateMode = utils.loadSetting(Constants.PRIVATE_MODE);
            if (privateMode) {
                uploadDirectory = new File(Environment.getExternalStorageDirectory(), "ShareX");
            } else {
                uploadDirectory = resolveInside(new File(utils.loadRoot()), currentParent);
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
        }

        private void finishUpload(ChannelHandlerContext ctx) throws Exception {
            int count = 0;
            for (InterfaceHttpData item : decoder.getBodyHttpDatas()) {
                if (!(item instanceof io.netty.handler.codec.http.multipart.HttpData)) continue;
                io.netty.handler.codec.http.multipart.HttpData data = (io.netty.handler.codec.http.multipart.HttpData) item;
                if (!(data instanceof FileUpload)) continue;
                FileUpload upload = (FileUpload) data;
                if (!upload.isCompleted() || upload.length() == 0) continue;
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
            String location = utils.loadSetting(Constants.PRIVATE_MODE) ? "storage/ShareX" : "storage" + normalizedParent();
            sendLog("msg", "msg", "Total files received " + count + " and stored in " + location + " directory");
            cleanupUpload();
            reply(ctx, WebResponse.text(200, count + " Files Uploaded Successsfully!"));
        }

        private void cleanupUpload() {
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
        if (ctx.pipeline().get("readTimeout") != null) ctx.pipeline().remove("readTimeout");
        if (!HttpMethod.GET.equals(request.method()) && !HttpMethod.HEAD.equals(request.method())) {
            reply(ctx, WebResponse.text(405, "Method not allowed"));
            return;
        }
        QueryStringDecoder query = new QueryStringDecoder(request.uri(), StandardCharsets.UTF_8);
        WebResponse response = route(query.path(), query.parameters(), request.headers().get(HttpHeaderNames.RANGE));
        if (HttpMethod.HEAD.equals(request.method()) && response.file != null) response = WebResponse.text(response.status, "");
        reply(ctx, response);
    }

    private WebResponse route(String uri, Map<String, List<String>> params, String range) throws Exception {
        if (uri.equals("/ShareX")) {
            String action = param(params, "action");
            switch (action) {
                case "listFiles": {
                    String loc = param(params, "location");
                    currentParent = loc;
                    File directory = resolveInside(new File(root), loc);
                    if (!directory.isDirectory()) return WebResponse.text(404, "Can't read this location!");
                    return WebResponse.text(200, serverUtils.getFilesListCode(directory.getAbsolutePath(), allowHiddenMedia));
                }
                case "openFile": {
                    File file = resolveRequestedFile(param(params, "location"));
                    if (range == null) sendLog("msg", "msg", "Sending file: " + file.getName());
                    return utils.loadSetting(Constants.FORCE_DOWNLOAD)
                            ? serverUtils.downloadFile(file.getPath(), true, true, range)
                            : serverUtils.serveFile(file.getPath(), true, range);
                }
                case "viewImage":
                    return serverUtils.serveFile(resolveRequestedFile(param(params, "location")).getPath(), false, range);
                case "thumbImage": {
                    File file = resolveRequestedFile(param(params, "location"));
                    if (!file.isFile()) return WebResponse.text(404, "Thumbnail not found");
                    return serverUtils.serveThumbnail(file.getAbsolutePath());
                }
                case "delFiles":
                    if (utils.loadSetting(Constants.RESTRICT_MODIFY) || utils.loadSetting(Constants.PRIVATE_MODE)) return WebResponse.text(200, "File deletion is restricted!;");
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
                    if (utils.loadSetting(Constants.RESTRICT_MODIFY) || utils.loadSetting(Constants.PRIVATE_MODE)) return WebResponse.text(200, "File/Folder modification restricted!;");
                    File old = resolveInside(new File(root), param(params, "old_n"));
                    if (old.equals(new File(root).getCanonicalFile())) return WebResponse.text(403, "Cannot rename storage root");
                    String newName = safeUploadName(param(params, "new_n"));
                    File parent = resolveInside(new File(root), param(params, "parent"));
                    File renamed = new File(parent, newName);
                    if (!old.exists() || renamed.exists() || !old.renameTo(renamed)) return WebResponse.text(409, "Unable to rename file or folder");
                    sendLog("msg", "msg", "File/folder renamed \"" + old.getName() + "\" to \"" + renamed.getName() + "\"");
                    return WebResponse.text(200, "File/Folder Renamed Successfully!;");
                case "newF":
                    if (utils.loadSetting(Constants.RESTRICT_MODIFY) || utils.loadSetting(Constants.PRIVATE_MODE)) return WebResponse.text(200, "File/Folder modification restricted!;");
                    File newFolder = new File(resolveInside(new File(root), param(params, "parent")), safeUploadName(param(params, "name")));
                    if (!newFolder.mkdirs()) return WebResponse.text(409, "Unable to create folder");
                    sendLog("msg", "msg", "New folder created at " + newFolder.getAbsolutePath());
                    return WebResponse.text(200, "Folder Created Successfully!;");
                case "listApps": return WebResponse.text(200, serverUtils.getAppsListCode());
                case "getApp": {
                    String pkg = param(params, "pkg");
                    PackageManager manager = context.getPackageManager();
                    String appName = manager.getApplicationLabel(manager.getApplicationInfo(pkg, PackageManager.GET_META_DATA)).toString();
                    String appPath = manager.getApplicationInfo(pkg, PackageManager.GET_META_DATA).sourceDir;
                    sendLog("msg", "msg", "Sending App: " + appName);
                    return serverUtils.serveApp(appName + ".apk", appPath, pkg, range);
                }
                case "getInfo": return WebResponse.text(200, serverUtils.getInfo());
                case "getUploadLocation": return WebResponse.text(200, utils.loadSetting(Constants.PRIVATE_MODE) ? "storage/ShareX" : "storage" + normalizedParent());
                case "getPrivateMode": return WebResponse.text(200, String.valueOf(utils.loadSetting(Constants.PRIVATE_MODE)));
                case "auth": {
                    DeviceManager manager = new DeviceManager(context);
                    String id = param(params, "device_id");
                    if (manager.isDeviceExist(id)) return WebResponse.text(200, "true");
                    if (manager.isDeviceDenied(id)) return WebResponse.text(200, "denied");
                    sendLog(Constants.ACTION_AUTH, "device_id", id);
                    return WebResponse.text(200, "false");
                }
                case "getInstalledPlugins": return WebResponse.text(200, serverUtils.getPluginsList());
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
        boolean pluginDebug = utils.loadSetting(Constants.PLUGIN_DEV) && (uri.contains("/debug") || uri.contains("/debug/"));
        if (!utils.loadSetting(Constants.PLUGIN_DEV) && (uri.endsWith("/debug") || uri.endsWith("/debug/"))) return WebResponse.text(400, "400 Bad Request! Please enable plugin development mode in settings first!");
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
        if (assetUri.equals("/libs/bootstrap/css/theme_bootstrap.min.css")) assetUri = assetUri.replace("theme", new ThemesData().getPrefix(utils.loadInt(Constants.WEB_INTERFACE_THEME, 0)));
        File file = new File(utils.getFileProperPath(assetUri));
        File assetRoot = new File(utils.getFileProperPath(".")).getCanonicalFile();
        if (!isInside(assetRoot, file.getCanonicalFile())) return WebResponse.text(403, "Access denied");
        if (!file.isFile()) return WebResponse.text(404, "Not found");
        if (uri.equals("/")) {
            TemplateEngine engine = new TemplateEngine(context);
            if (pluginDevDir != null) engine.setPlugin_dev_dir(pluginDevDir);
            return WebResponse.text(200, engine.renderHtml(file.getAbsolutePath(), TemplateEngine.RENDER_TYPE.NORMAL)).header("Content-Type", "text/html; charset=utf-8");
        }
        return staticFile(file, false);
    }

    private WebResponse staticFile(File file, boolean progressEnabled) {
        if (!file.isFile() || !file.canRead()) return WebResponse.text(404, "Not found");
        String mime = utils.getMimeType(file);
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

    private String param(Map<String, List<String>> params, String key) {
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
    }

    private void sendLog(String action, String key, String value) {
        Intent intent = new Intent(Constants.BROADCAST_SERVICE_TO_ACTIVITY);
        intent.putExtra("action", action);
        intent.putExtra(key, value);
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
        httpResponse.headers().setInt(HttpHeaderNames.CONTENT_LENGTH, content.readableBytes());
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
            if (response.reportProgress) {
                promise.addListener(new ChannelProgressiveFutureListener() {
                    @Override public void operationProgressed(ChannelProgressiveFuture future, long transferred, long total) {
                        if (total > 0) serverUtils.sendProgressListenerUpdate((int) Math.min(100, transferred * 100 / total));
                    }
                    @Override public void operationComplete(ChannelProgressiveFuture future) { }
                });
            }
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
                        if (future.isSuccess()) schedulePump();
                        else cancel();
                    });
                } else if (chunk == FINISHED || generatorFinished.get() && chunks.isEmpty()) {
                    if (generationFailed.get()) context.close();
                    else context.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT).addListener(ChannelFutureListener.CLOSE);
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

