package com.akansh.sharex.server;

import com.akansh.sharex.common.Constants;
import com.akansh.sharex.common.Utils;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/** Bounded background jobs keep large local copies off the HTTP request threads. */
final class FileOperations {
    private final Utils utils;
    private final ConcurrentHashMap<String, Job> jobs = new ConcurrentHashMap<>();
    private final ThreadPoolExecutor executor = new ThreadPoolExecutor(1, 1, 30, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(8), task -> new Thread(task, "ShareX-FileOperations"),
            new ThreadPoolExecutor.AbortPolicy());

    FileOperations(Utils utils) { this.utils = utils; }

    JSONObject start(String mode, List<File> requested, File destination, File root) throws Exception {
        checkPermission();
        if (!mode.equals("copy") && !mode.equals("move")) throw new IllegalArgumentException("Invalid operation");
        if (requested.isEmpty()) throw new IllegalArgumentException("No files selected");
        requireInside(root, destination);
        if (!destination.isDirectory()) throw new IllegalArgumentException("Destination must be a folder");
        List<File> sources = new ArrayList<>();
        for (File source : requested) {
            requireInside(root, source);
            if (!source.exists() || source.equals(root)) throw new IllegalArgumentException("Invalid source");
            if (source.isDirectory() && inside(source, destination)) throw new IllegalArgumentException("Cannot place a folder inside itself");
            if (mode.equals("move") && source.getParentFile().equals(destination)) throw new IllegalArgumentException("Items are already in this folder");
            boolean covered = false;
            for (File selected : requested) {
                if (!selected.equals(source) && selected.isDirectory() && inside(selected, source)) { covered = true; break; }
            }
            if (!covered && !sources.contains(source)) sources.add(source);
        }
        long cutoff = System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(10);
        jobs.entrySet().removeIf(entry -> entry.getValue().finished > 0 && entry.getValue().finished < cutoff);
        Job job = new Job(mode, sources, destination, root);
        jobs.put(job.id, job);
        try { executor.execute(() -> run(job)); }
        catch (java.util.concurrent.RejectedExecutionException busy) { jobs.remove(job.id); throw busy; }
        return job.json();
    }

    JSONObject status(String id) throws Exception {
        Job job = jobs.get(id);
        return job == null ? null : job.json();
    }

    private void run(Job job) {
        job.state = "running";
        try {
            for (File source : job.sources) job.total += measure(source, job.root);
            for (File source : job.sources) {
                File staging = null;
                try {
                    checkPermission();
                    requireInside(job.root, source);
                    requireInside(job.root, job.destination);
                    if (source.isDirectory() && inside(source, job.destination)) throw new IOException("Destination is inside source");
                    File target = availableDestination(job.destination, source, job.mode);
                    if (job.mode.equals("move")) {
                        // Files.move fails on collisions. Existing content is never overwritten.
                        long size = measure(source, job.root);
                        Files.move(source.toPath(), target.toPath());
                        job.processed.addAndGet(size);
                    } else {
                        staging = new File(job.destination, ".sharex-copy-" + UUID.randomUUID());
                        copy(source, staging, job);
                        checkPermission();
                        Files.move(staging.toPath(), target.toPath());
                    }
                    job.completed.add(source.getName());
                } catch (Exception error) {
                    job.errors.add(source.getName() + ": " + error.getMessage());
                } finally { if (staging != null && staging.exists()) utils.deleteDirectory(staging); }
            }
            job.state = job.errors.isEmpty() ? "completed" : "failed";
        } catch (Exception error) {
            job.errors.add(error.getMessage());
            job.state = "failed";
        } finally { job.finished = System.currentTimeMillis(); }
    }

    private void checkPermission() {
        if (utils.loadSetting(Constants.PRIVATE_MODE) || utils.loadSetting(Constants.RESTRICT_MODIFY)) {
            throw new SecurityException("File changes are restricted on this device");
        }
        if (Thread.currentThread().isInterrupted()) throw new IllegalStateException("Operation stopped");
    }

    private long measure(File source, File root) throws Exception {
        checkPermission();
        requireInside(root, source);
        if (!source.isDirectory()) return source.length();
        long size = 0;
        try (DirectoryStream<Path> children = Files.newDirectoryStream(source.toPath())) {
            for (Path child : children) size += measure(child.toFile(), root);
        }
        return size;
    }

    private void copy(File source, File target, Job job) throws Exception {
        checkPermission();
        requireInside(job.root, source);
        if (source.isDirectory()) {
            if (!target.mkdir()) throw new IOException("Cannot create destination folder");
            try (DirectoryStream<Path> children = Files.newDirectoryStream(source.toPath())) {
                for (Path child : children) copy(child.toFile(), new File(target, child.getFileName().toString()), job);
            }
        } else {
            try (FileInputStream input = new FileInputStream(source);
                 FileOutputStream output = new FileOutputStream(target)) {
                byte[] buffer = new byte[128 * 1024];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    checkPermission();
                    output.write(buffer, 0, count);
                    job.processed.addAndGet(count);
                }
            }
        }
        target.setLastModified(source.lastModified());
    }

    private File availableDestination(File parent, File source, String mode) throws IOException {
        File target = new File(parent, source.getName());
        if (!target.exists()) return target;
        if (mode.equals("move")) throw new IOException("An item with this name already exists at the destination");
        int dot = source.isDirectory() ? -1 : source.getName().lastIndexOf('.');
        String base = dot > 0 ? source.getName().substring(0, dot) : source.getName();
        String extension = dot > 0 ? source.getName().substring(dot) : "";
        for (int index = 1; index < 10000; index++) {
            String suffix = index == 1 ? " (copy)" : " (copy " + index + ")";
            target = new File(parent, base + suffix + extension);
            if (!target.exists()) return target;
        }
        throw new IOException("Cannot choose a destination name");
    }

    private void requireInside(File root, File file) throws Exception {
        if (Files.isSymbolicLink(file.toPath())) throw new IOException("Symbolic links are not supported");
        if (!inside(root.getCanonicalFile(), file.getCanonicalFile())) throw new SecurityException("Path escapes storage root");
    }

    private boolean inside(File parent, File child) {
        return child.equals(parent) || child.getPath().startsWith(parent.getPath() + File.separator);
    }

    void shutdown() { executor.shutdownNow(); }

    private static final class Job {
        final String id = UUID.randomUUID().toString();
        final String mode;
        final List<File> sources;
        final File destination;
        final File root;
        final AtomicLong processed = new AtomicLong();
        final List<String> completed = new CopyOnWriteArrayList<>();
        final List<String> errors = new CopyOnWriteArrayList<>();
        volatile String state = "queued";
        volatile long total;
        volatile long finished;

        Job(String mode, List<File> sources, File destination, File root) {
            this.mode = mode; this.sources = sources; this.destination = destination; this.root = root;
        }

        JSONObject json() throws Exception {
            return new JSONObject().put("id", id).put("mode", mode).put("state", state)
                    .put("processed", processed.get()).put("total", total).put("count", sources.size())
                    .put("completed", new JSONArray(completed)).put("errors", new JSONArray(errors));
        }
    }
}
