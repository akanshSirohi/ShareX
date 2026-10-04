package com.akansh.fileserversuit.ui;

import java.io.File;
import java.io.IOException;

public final class StorageFolder {
    private StorageFolder() {}
    public static File resolve(String documentId, File primary, File removable) throws IOException {
        int separator = documentId == null ? -1 : documentId.indexOf(':');
        if (separator < 0) throw new IOException("Choose a local storage folder");
        String volume = documentId.substring(0, separator);
        File base = "primary".equals(volume) ? primary : removable;
        if (base == null || (!"primary".equals(volume) && !volume.equalsIgnoreCase(base.getName()))) {
            throw new IOException("Storage is unavailable");
        }
        base = base.getCanonicalFile();
        String relative = documentId.substring(separator + 1);
        if (relative.startsWith("/") || relative.contains("\\")) throw new IOException("Invalid folder path");
        File selected = new File(base, relative).getCanonicalFile();
        if (!selected.equals(base) && !selected.getPath().startsWith(base.getPath() + File.separator)) {
            throw new IOException("Folder must be inside the selected storage");
        }
        if (!selected.isDirectory() || !selected.canRead()) throw new IOException("Folder is unavailable");
        return selected;
    }
}
