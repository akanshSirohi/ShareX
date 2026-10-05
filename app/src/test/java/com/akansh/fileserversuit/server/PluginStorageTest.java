package com.akansh.fileserversuit.server;

import java.io.File;
import org.junit.Test;
import static org.junit.Assert.*;

public class PluginStorageTest {
    @Test public void pluginStorageRejectsCrossNamespacePaths() {
        File directory = new File("plugins/dev.sample/json_files");
        assertEquals(new File(directory, "notes.json"), JsonDBHandler.safeFile(directory, "notes.json"));
        for (String name : new String[]{"../other.json", "/other.json", "C:\\other.json", "..", "folder/name.json"}) {
            assertThrows(IllegalArgumentException.class, () -> JsonDBHandler.safeFile(directory, name));
        }
    }
}
