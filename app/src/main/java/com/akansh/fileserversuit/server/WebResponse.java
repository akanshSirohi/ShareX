package com.akansh.fileserversuit.server;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;

final class WebResponse {
    final int status;
    final String body;
    final File file;
    final byte[] bytes;
    final long offset;
    final long length;
    final Map<String, String> headers = new LinkedHashMap<>();
    final boolean reportProgress;
    final List<ZipEntrySource> zipSources;
    final File allowedRoot;
    final boolean privateMode;

    private WebResponse(int status, String body, File file, byte[] bytes, long offset, long length, boolean reportProgress, List<ZipEntrySource> zipSources, File allowedRoot, boolean privateMode) {
        this.status = status;
        this.body = body;
        this.file = file;
        this.bytes = bytes;
        this.offset = offset;
        this.length = length;
        this.reportProgress = reportProgress;
        this.zipSources = zipSources;
        this.allowedRoot = allowedRoot;
        this.privateMode = privateMode;
    }

    static WebResponse text(int status, String body) {
        return new WebResponse(status, body == null ? "" : body, null, null, 0, 0, false, null, null, false);
    }

    static WebResponse file(int status, File file, long offset, long length, boolean reportProgress) {
        return new WebResponse(status, null, file, null, offset, length, reportProgress, null, null, false);
    }

    static WebResponse bytes(int status, byte[] bytes) {
        return new WebResponse(status, null, null, bytes, 0, bytes.length, false, null, null, false);
    }

    static WebResponse zip(List<ZipEntrySource> sources, File allowedRoot, boolean privateMode) {
        return new WebResponse(200, null, null, null, 0, 0, true, sources, allowedRoot, privateMode);
    }

    WebResponse header(String name, String value) {
        headers.put(name, value);
        return this;
    }

    static final class ZipEntrySource {
        final File file;
        final String entryName;
        final boolean directory;

        ZipEntrySource(File file, String entryName, boolean directory) {
            this.file = file;
            this.entryName = entryName;
            this.directory = directory;
        }
    }
}
