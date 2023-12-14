package com.akansh.plugins;

import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class PluginsDBHelper extends SQLiteOpenHelper {
    public static final String DATABASE_NAME="SharexPlugins.db";
    public static final String TABLE_NAME="PLUGINS";
    public static final String UID = "UID";
    public static final String NAME = "NAME";
    public static final String DESCRIPTION = "DESCRIPTION";
    public static final String AUTHOR = "AUTHOR";
    public static final String VERSION = "VERSION";
    public static final String VERSION_CODE = "VERSION_CODE";
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
                "DESCRIPTION TEXT NOT NULL," +
                "AUTHOR TEXT NOT NULL," +
                "VERSION TEXT NOT NULL," +
                "VERSION_CODE INT NOT NULL" +
            ")",TABLE_NAME)
        );
    }

    public boolean insertPlugin(Plugin plugin) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues contentValues = new ContentValues();
        contentValues.put(UID, plugin.getPlugin_uid());
        contentValues.put(NAME, plugin.getPlugin_name());
        contentValues.put(DESCRIPTION, plugin.getPlugin_description());
        contentValues.put(AUTHOR, plugin.getPlugin_author());
        contentValues.put(VERSION, plugin.getPlugin_version());
        contentValues.put(VERSION_CODE, plugin.getPlugin_version_code());
        long result = db.insert(TABLE_NAME, null, contentValues);
        return result != -1;
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS "+TABLE_NAME);
        db.execSQL(String.format(
            "CREATE TABLE IF NOT EXISTS %s (" +
                "ID INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL," +
                "UID TEXT NOT NULL," +
                "NAME TEXT NOT NULL," +
                "DESCRIPTION TEXT NOT NULL," +
                "AUTHOR TEXT NOT NULL," +
                "VERSION TEXT NOT NULL," +
                "VERSION_CODE INT NOT NULL" +
            ")",TABLE_NAME)
        );
    }
}
