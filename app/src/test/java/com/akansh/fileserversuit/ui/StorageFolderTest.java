package com.akansh.fileserversuit.ui;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import java.io.File;
import java.io.IOException;
import static org.junit.Assert.*;

public class StorageFolderTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();
    @Test public void folderSelectionAlwaysStartsFromStorageRoot() throws Exception {
        File root = temporary.newFolder("primary");
        File first = new File(root, "Download");
        File second = new File(root, "Pictures");
        assertTrue(first.mkdir()); assertTrue(second.mkdir());
        assertEquals(first.getCanonicalFile(), StorageFolder.resolve("primary:Download", root, null));
        assertEquals(second.getCanonicalFile(), StorageFolder.resolve("primary:Pictures", root, null));
        assertEquals(root.getCanonicalFile(), StorageFolder.resolve("primary:", root, null));
        File sd = temporary.newFolder("ABCD-1234");
        assertEquals(sd.getCanonicalFile(), StorageFolder.resolve("ABCD-1234:", root, sd));
    }
    @Test public void rejectsMissingVolumesAndPathsOutsideStorage() throws Exception {
        File root = temporary.newFolder("primary");
        for (String id : new String[]{"primary:../", "primary:/", "unknown:", "primary:missing", "malformed"}) {
            try { StorageFolder.resolve(id, root, null); fail("Must reject " + id); }
            catch (IOException expected) { }
        }
    }
}
