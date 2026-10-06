package com.akansh.sharex.server;

import com.akansh.sharex.common.Utils;

import org.json.JSONObject;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.*;

public class FileOperationsTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();
    private volatile boolean restricted;
    private final Utils utils = new Utils() {
        @Override public boolean loadSetting(String key) { return restricted; }
    };

    @Test public void recursiveCopyPreservesOriginalAndIncludesEmptyFiles() throws Exception {
        File root = temporary.newFolder("storage");
        File source = new File(root, "documents");
        File nested = new File(source, "notes");
        assertTrue(nested.mkdirs());
        Files.writeString(new File(nested, "résumé & notes.txt").toPath(), "hello");
        assertTrue(new File(nested, "empty.txt").createNewFile());
        File destination = new File(root, "destination");
        assertTrue(destination.mkdir());
        FileOperations operations = new FileOperations(utils);
        try {
            JSONObject result = await(operations, operations.start("copy", Arrays.asList(source, nested), destination, root));
            assertEquals("completed", result.getString("state"));
            assertEquals(1, result.getInt("count"));
            assertEquals("hello", Files.readString(new File(destination, "documents/notes/résumé & notes.txt").toPath()));
            assertTrue(new File(destination, "documents/notes/empty.txt").exists());
            assertTrue(source.exists());
        } finally { operations.shutdown(); }
    }

    @Test public void copiesGetDistinctNamesAndMovesNeverOverwrite() throws Exception {
        File root = temporary.newFolder("storage");
        File source = new File(root, "report.txt");
        Files.writeString(source.toPath(), "original");
        File destination = new File(root, "destination");
        assertTrue(destination.mkdir());
        Files.writeString(new File(destination, "report.txt").toPath(), "existing");
        FileOperations operations = new FileOperations(utils);
        try {
            assertEquals("completed", await(operations, operations.start("copy", Collections.singletonList(source), destination, root)).getString("state"));
            assertEquals("original", Files.readString(new File(destination, "report (copy).txt").toPath()));
            assertEquals("failed", await(operations, operations.start("move", Collections.singletonList(source), destination, root)).getString("state"));
            assertEquals("existing", Files.readString(new File(destination, "report.txt").toPath()));
            assertTrue(source.exists());
        } finally { operations.shutdown(); }
    }

    @Test public void moveRemovesSourceOnlyAfterSuccessfulMove() throws Exception {
        File root = temporary.newFolder("storage");
        File source = new File(root, "notes.txt");
        Files.writeString(source.toPath(), "notes");
        File destination = new File(root, "destination");
        assertTrue(destination.mkdir());
        FileOperations operations = new FileOperations(utils);
        try {
            assertEquals("completed", await(operations, operations.start("move", Collections.singletonList(source), destination, root)).getString("state"));
            assertFalse(source.exists());
            assertEquals("notes", Files.readString(new File(destination, "notes.txt").toPath()));
        } finally { operations.shutdown(); }
    }

    @Test public void rejectsSelfDescendantsAndRestrictedOperations() throws Exception {
        File root = temporary.newFolder("storage");
        File folder = new File(root, "folder");
        File child = new File(folder, "child");
        assertTrue(child.mkdirs());
        FileOperations operations = new FileOperations(utils);
        try {
            assertThrows(IllegalArgumentException.class, () -> operations.start("copy", Collections.singletonList(folder), child, root));
            restricted = true;
            assertThrows(SecurityException.class, () -> operations.start("move", Collections.singletonList(child), root, root));
            assertTrue(child.exists());
        } finally { operations.shutdown(); }
    }

    @Test public void rejectsPathsOutsideStorageBeforeStartingAJob() throws Exception {
        File root = temporary.newFolder("storage");
        File outside = temporary.newFolder("outside");
        File source = new File(root, "notes.txt");
        Files.writeString(source.toPath(), "notes");
        FileOperations operations = new FileOperations(utils);
        try {
            assertThrows(SecurityException.class, () -> operations.start("copy", Collections.singletonList(source), outside, root));
            assertThrows(SecurityException.class, () -> operations.start("move", Collections.singletonList(outside), root, root));
            assertTrue(source.exists());
            assertEquals(0, outside.listFiles().length);
        } finally { operations.shutdown(); }
    }

    private JSONObject await(FileOperations operations, JSONObject started) throws Exception {
        long deadline = System.currentTimeMillis() + 5000;
        JSONObject result;
        do {
            result = operations.status(started.getString("id"));
            if (result.getString("state").equals("completed") || result.getString("state").equals("failed")) return result;
            Thread.sleep(10);
        } while (System.currentTimeMillis() < deadline);
        fail("Operation timed out");
        return result;
    }
}
