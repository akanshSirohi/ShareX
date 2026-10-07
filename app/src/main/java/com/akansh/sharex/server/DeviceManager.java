package com.akansh.sharex.server;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import com.akansh.sharex.common.Constants;
import java.util.ArrayList;
import java.util.List;

public class DeviceManager extends SQLiteOpenHelper {
    public static final String DATABASE_NAME = "FSX_DeviceList.db";
    public static final String TABLE_NAME = "D_LIST";
    public static final String DEVICE_ID = "DEVICE_ID";
    public static final String DEVICE_TYPE = "DEVICE_TYPE";
    public static final String DEVICE_NAME = "DEVICE_NAME";
    public static final String LAST_CONNECTED = "LAST_CONNECTED";

    public DeviceManager(Context context) { this(context, DATABASE_NAME); }
    DeviceManager(Context context, String databaseName) { super(context, databaseName, null, 4); }

    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + TABLE_NAME + " (ID INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,"
                + "DEVICE_ID TEXT NOT NULL UNIQUE,DEVICE_TYPE INT NOT NULL,DEVICE_NAME TEXT NOT NULL DEFAULT '',LAST_CONNECTED INTEGER NOT NULL DEFAULT 0,PROOF_VERSION INTEGER NOT NULL DEFAULT 0)");
    }

    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE " + TABLE_NAME + " ADD COLUMN DEVICE_NAME TEXT NOT NULL DEFAULT ''");
            // Last approval decision wins if the old database contains duplicate browser IDs.
            db.execSQL("DELETE FROM " + TABLE_NAME + " WHERE ID NOT IN (SELECT MAX(ID) FROM " + TABLE_NAME + " GROUP BY DEVICE_ID)");
            db.execSQL("CREATE UNIQUE INDEX device_id_unique ON " + TABLE_NAME + "(DEVICE_ID)");
        }
        if (oldVersion < 3) db.execSQL("ALTER TABLE " + TABLE_NAME + " ADD COLUMN LAST_CONNECTED INTEGER NOT NULL DEFAULT 0");
        if (oldVersion < 4) db.execSQL("ALTER TABLE " + TABLE_NAME + " ADD COLUMN PROOF_VERSION INTEGER NOT NULL DEFAULT 0");
    }

    public boolean addDevice(String id, int type) { return addDevice(id, type, ""); }
    public boolean addDevice(String id, int type, String name) {
        if (id == null || id.isEmpty()) return false;
        ContentValues values = new ContentValues();
        values.put(DEVICE_ID, id);
        values.put(DEVICE_TYPE, type);
        values.put("PROOF_VERSION", id.matches("v2:[a-f0-9]{64}") ? 1 : 0);
        values.put(LAST_CONNECTED, System.currentTimeMillis());
        if (name == null || name.isEmpty()) {
            try (Cursor cursor = getReadableDatabase().query(TABLE_NAME, new String[] { DEVICE_NAME },
                    DEVICE_ID + " = ?", new String[] { id }, null, null, null)) {
                if (cursor.moveToFirst()) name = cursor.getString(0);
            }
        }
        values.put(DEVICE_NAME, name == null ? "" : name);
        return getWritableDatabase().insertWithOnConflict(TABLE_NAME, null, values, SQLiteDatabase.CONFLICT_REPLACE) != -1;
    }

    public static final class RememberedDevice {
        public final String id;
        public final String name;
        public final long lastConnected;
        RememberedDevice(String id, String name, long lastConnected) { this.id = id; this.name = name; this.lastConnected = lastConnected; }
    }

    public List<RememberedDevice> getRememberedDevices() {
        List<RememberedDevice> devices = new ArrayList<>();
        try (Cursor cursor = getReadableDatabase().query(TABLE_NAME, new String[] { DEVICE_ID, DEVICE_NAME, LAST_CONNECTED },
                DEVICE_TYPE + " = ?", new String[] { String.valueOf(Constants.DEVICE_TYPE_PERMANENT) }, null, null, "ID DESC")) {
            while (cursor.moveToNext()) devices.add(new RememberedDevice(cursor.getString(0), cursor.getString(1), cursor.getLong(2)));
        }
        return devices;
    }

    public int getRemDevices() {
        try (Cursor cursor = getReadableDatabase().rawQuery("SELECT COUNT(*) FROM " + TABLE_NAME + " WHERE DEVICE_TYPE = ?",
                new String[] { String.valueOf(Constants.DEVICE_TYPE_PERMANENT) })) {
            cursor.moveToFirst();
            return cursor.getInt(0);
        }
    }

    public boolean isDeviceExist(String id) { return matches(id, false); }
    public boolean isSecureApproved(String id, boolean permanentOnly) {
        try (Cursor cursor = getReadableDatabase().query(TABLE_NAME, new String[]{DEVICE_ID},
                DEVICE_ID + " = ? AND PROOF_VERSION = 1 AND DEVICE_TYPE " + (permanentOnly ? " = ?" : " != ?"),
                new String[]{id, String.valueOf(permanentOnly ? Constants.DEVICE_TYPE_PERMANENT : Constants.DEVICE_TYPE_DENIED)}, null, null, null, "1")) {
            return cursor.moveToFirst();
        }
    }
    public boolean isDeviceDenied(String id) { return matches(id, true); }
    private boolean matches(String id, boolean denied) {
        try (Cursor cursor = getReadableDatabase().query(TABLE_NAME, new String[] { DEVICE_ID },
                DEVICE_ID + " = ? AND " + DEVICE_TYPE + (denied ? " = ?" : " != ?"),
                new String[] { id, String.valueOf(Constants.DEVICE_TYPE_DENIED) }, null, null, null, "1")) {
            return cursor.moveToFirst();
        }
    }

    public void updateMissingName(String id, String name) {
        ContentValues values = new ContentValues();
        values.put(DEVICE_NAME, name);
        getWritableDatabase().update(TABLE_NAME, values, DEVICE_ID + " = ? AND " + DEVICE_NAME + " = ''", new String[] { id });
    }

    public void recordConnection(String id, String name) {
        updateMissingName(id, name);
        ContentValues values = new ContentValues();
        values.put(LAST_CONNECTED, System.currentTimeMillis());
        getWritableDatabase().update(TABLE_NAME, values, DEVICE_ID + " = ?", new String[] { id });
    }

    public boolean forgetDevice(String id) {
        return getWritableDatabase().delete(TABLE_NAME, DEVICE_ID + " = ? AND " + DEVICE_TYPE + " = ?",
                new String[] { id, String.valueOf(Constants.DEVICE_TYPE_PERMANENT) }) > 0;
    }
    public void clearRemembered() {
        getWritableDatabase().delete(TABLE_NAME, DEVICE_TYPE + " = ?", new String[] { String.valueOf(Constants.DEVICE_TYPE_PERMANENT) });
    }
    public void clearAll() { getWritableDatabase().delete(TABLE_NAME, null, null); }
    public void clearTmp() {
        getWritableDatabase().delete(TABLE_NAME, DEVICE_TYPE + " IN (?, ?)",
                new String[] { String.valueOf(Constants.DEVICE_TYPE_TEMP), String.valueOf(Constants.DEVICE_TYPE_DENIED) });
    }
}
