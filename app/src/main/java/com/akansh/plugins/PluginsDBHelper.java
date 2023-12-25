package com.akansh.plugins;

import android.annotation.SuppressLint;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import com.akansh.plugins.common.Plugin;

import java.util.ArrayList;

public class PluginsDBHelper extends SQLiteOpenHelper {
    public static final String DATABASE_NAME="SharexPlugins.db";
    public static final String TABLE_NAME="PLUGINS";
    public static final String UID = "UID";
    public static final String NAME = "NAME";
    public static final String PACKAGE_NAME = "PACKAGE_NAME";
    public static final String DESCRIPTION = "DESCRIPTION";
    public static final String AUTHOR = "AUTHOR";
    public static final String VERSION = "VERSION";
    public static final String VERSION_CODE = "VERSION_CODE";
    public static final String ENABLED = "ENABLED";
    private static final int DATABASE_VERSION = 1;

    public PluginsDBHelper(Context context) {
        super(context,DATABASE_NAME,null,DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(String.format(
            "CREATE TABLE IF NOT EXISTS %s (" +
                "ID INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL," +
                "UID TEXT NOT NULL," +
                "NAME TEXT NOT NULL," +
                "PACKAGE_NAME TEXT NOT NULL," +
                "DESCRIPTION TEXT NOT NULL," +
                "AUTHOR TEXT NOT NULL," +
                "VERSION TEXT NOT NULL," +
                "VERSION_CODE INT NOT NULL," +
                "ENABLED INT NOT NULL DEFAULT 1" +
            ")",TABLE_NAME)
        );
    }

    public String getPluginUIDByPackageName(String packageNAme) {
        SQLiteDatabase db=this.getReadableDatabase();
        Cursor cursor=db.rawQuery("SELECT UID FROM "+TABLE_NAME+" WHERE PACKAGE_NAME='"+packageNAme+"'", null);
        if(cursor.getCount() > 0) {
            if (cursor.moveToFirst()) {
                @SuppressLint("Range")
                String uid = cursor.getString(cursor.getColumnIndex(UID));
                cursor.close();
                return uid;
            }
        }
        return null;
    }

    public int getPluginVersionCodeByPackageName(String packageNAme) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT VERSION_CODE FROM "+TABLE_NAME+" WHERE PACKAGE_NAME='"+packageNAme+"'", null);
        if(cursor.getCount() > 0) {
            if (cursor.moveToFirst()) {
                @SuppressLint("Range")
                int version_code = cursor.getInt(cursor.getColumnIndex(VERSION_CODE));
                cursor.close();
                return version_code;
            }
        }
        return -1;
    }

    public boolean insertPlugin(Plugin plugin) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues contentValues = new ContentValues();
        contentValues.put(UID, plugin.getPlugin_uid());
        contentValues.put(NAME, plugin.getPlugin_name());
        contentValues.put(PACKAGE_NAME, plugin.getPlugin_package_name());
        contentValues.put(DESCRIPTION, plugin.getPlugin_description());
        contentValues.put(AUTHOR, plugin.getPlugin_author());
        contentValues.put(VERSION, plugin.getPlugin_version());
        contentValues.put(VERSION_CODE, plugin.getPlugin_version_code());
        long result = db.insert(TABLE_NAME, null, contentValues);
        return result != -1;
    }

    public boolean updatePlugin(Plugin plugin) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues contentValues = new ContentValues();
        contentValues.put(NAME, plugin.getPlugin_name());
        contentValues.put(DESCRIPTION, plugin.getPlugin_description());
        contentValues.put(AUTHOR, plugin.getPlugin_author());
        contentValues.put(VERSION, plugin.getPlugin_version());
        contentValues.put(VERSION_CODE, plugin.getPlugin_version_code());
        int result = db.update(TABLE_NAME, contentValues, "UID=?", new String[] { plugin.getPlugin_uid() });
        return result != -1;
    }

    public boolean changeStatus(String uid,boolean status) {
        SQLiteDatabase db = this.getWritableDatabase();
        int val = status ? 1 : 0;
        ContentValues contentValues = new ContentValues();
        contentValues.put(ENABLED, val);
        int result = db.update(TABLE_NAME, contentValues, "UID=?", new String[] { uid });
        return result != -1;
    }

    public boolean getStatus(String uid) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT ENABLED FROM "+TABLE_NAME+" WHERE UID='"+uid+"'", null);
        if(cursor.getCount() > 0) {
            if (cursor.moveToFirst()) {
                @SuppressLint("Range")
                int enabled = cursor.getInt(cursor.getColumnIndex(ENABLED));
                cursor.close();
                return enabled == 1;
            }
        }
        return true;
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS "+TABLE_NAME);
        db.execSQL(String.format(
            "CREATE TABLE IF NOT EXISTS %s (" +
                "ID INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL," +
                "UID TEXT NOT NULL," +
                "NAME TEXT NOT NULL," +
                "PACKAGE_NAME TEXT NOT NULL," +
                "DESCRIPTION TEXT NOT NULL," +
                "AUTHOR TEXT NOT NULL," +
                "VERSION TEXT NOT NULL," +
                "VERSION_CODE INT NOT NULL," +
                "ENABLED INT NOT NULL DEFAULT 1" +
            ")",TABLE_NAME)
        );
    }

    @SuppressLint("Range")
    public ArrayList<Plugin> getInstalledPlugins() {
        SQLiteDatabase db=this.getReadableDatabase();
        Cursor cursor=db.rawQuery("SELECT * FROM "+TABLE_NAME+" ORDER BY ID DESC", null);
        ArrayList<Plugin> installedPluginsList = new ArrayList<>();
        while(cursor.moveToNext()) {
            Plugin plugin = new Plugin(cursor.getString(cursor.getColumnIndex(UID)),
                    cursor.getString(cursor.getColumnIndex(NAME)),
                    cursor.getString(cursor.getColumnIndex(PACKAGE_NAME)),
                    cursor.getString(cursor.getColumnIndex(DESCRIPTION)),
                    cursor.getString(cursor.getColumnIndex(AUTHOR)),
                    cursor.getString(cursor.getColumnIndex(VERSION)),
                    cursor.getInt(cursor.getColumnIndex(VERSION_CODE))
            );
            plugin.setPlugin_enabled(cursor.getInt(cursor.getColumnIndex(ENABLED)) == 1);
            installedPluginsList.add(plugin);
        }
        return installedPluginsList;
    }

    @SuppressLint("Range")
    public ArrayList<String> getInstalledPluginsPackages() {
        SQLiteDatabase db=this.getReadableDatabase();
        Cursor cursor=db.rawQuery("SELECT PACKAGE_NAME FROM "+TABLE_NAME+" ORDER BY ID DESC", null);
        ArrayList<String> installedList = new ArrayList<>();
        while(cursor.moveToNext()) {
            installedList.add(cursor.getString(cursor.getColumnIndex(PACKAGE_NAME)));
        }
        return installedList;
    }

    public void deletePluginEntry(String uid) {
        SQLiteDatabase db=this.getWritableDatabase();
        db.delete(TABLE_NAME,UID + " = ?",new String[] {String.valueOf(uid)});
    }
}
