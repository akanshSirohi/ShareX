package com.akansh.t_history;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;

public class HistoryDBHelper extends SQLiteOpenHelper {

    public static final String DATABASE_NAME="FSX_Hist.db";
    public static final String TABLE_NAME="T_HIST";

    public static final String FILE_NAME="FILE_NAME";
    public static final String ITEM_TYPE="ITEM_TYPE";
    public static final String SIZE="SIZE";
    public static final String DATE="DATE_";
    public static final String TIME="TIME_";
    public static final String TYPE="TYPE";
    public static final String PATH="PATH";
    public static final String TIMESTAMP="TSTAMP";
    private static final int DATABASE_VERSION = 4;


    public HistoryDBHelper(Context context) {
        super(context,DATABASE_NAME,null,DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS "+TABLE_NAME+" (ID INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,FILE_NAME TEXT NOT NULL,ITEM_TYPE TEXT NOT NULL,SIZE TEXT NOT NULL,DATE_ TEXT NOT NULL,TIME_ TEXT NOT NULL,TYPE TEXT NOT NULL,PATH TEXT NOT NULL,TSTAMP TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS "+TABLE_NAME);
        db.execSQL("CREATE TABLE IF NOT EXISTS "+TABLE_NAME+" (ID INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,FILE_NAME TEXT NOT NULL,ITEM_TYPE TEXT NOT NULL,SIZE TEXT NOT NULL,DATE_ TEXT NOT NULL,TIME_ TEXT NOT NULL,TYPE TEXT NOT NULL,PATH TEXT NOT NULL,TSTAMP TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
    }

    public boolean insertData(HashMap<String,String> values) {
        SQLiteDatabase db=this.getWritableDatabase();
        ContentValues contentValues=new ContentValues();
        contentValues.put(FILE_NAME,values.get("file_name"));
        contentValues.put(ITEM_TYPE,Integer.parseInt(values.get("item_type")));
        contentValues.put(SIZE,values.get("size"));
        contentValues.put(DATE,values.get("date"));
        contentValues.put(TIME,values.get("time"));
        contentValues.put(TYPE,values.get("type"));
        contentValues.put(PATH,values.get("path"));
        long result;
        if(!checkHistoryExistByPath(values.get("path"))) {
            result = db.insert(TABLE_NAME, null, contentValues);
        }else{
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
            String newTimeStamp = dateFormat.format(new Date());
            contentValues.put(TIMESTAMP, newTimeStamp);
            result = db.update(TABLE_NAME, contentValues, "PATH=?", new String[] { values.get("path") });
        }
        if(result == -1) {
            return false;
        }else{
            return true;
        }
    }

    public boolean restoreData(HashMap<String,String> values) {
        SQLiteDatabase db=this.getWritableDatabase();
        ContentValues contentValues=new ContentValues();
        contentValues.put("ID",Integer.parseInt(values.get("uid")));
        contentValues.put(FILE_NAME,values.get("file_name"));
        contentValues.put(ITEM_TYPE,Integer.parseInt(values.get("item_type")));
        contentValues.put(SIZE,values.get("size"));
        contentValues.put(DATE,values.get("date"));
        contentValues.put(TIME,values.get("time"));
        contentValues.put(TYPE,values.get("type"));
        contentValues.put(PATH,values.get("path"));
        contentValues.put(TIMESTAMP,values.get("timestamp"));
        long result=db.insert(TABLE_NAME,null,contentValues);
        if(result == -1) {
            return false;
        }else{
            return true;
        }
    }

    public Cursor getData() {
        SQLiteDatabase db=this.getReadableDatabase();
        Cursor res=db.rawQuery("SELECT * FROM "+TABLE_NAME+" ORDER BY TSTAMP DESC", null);
        return res;
    }

    private boolean checkHistoryExistByPath(String file_path) {
        SQLiteDatabase db=this.getReadableDatabase();
        Cursor res=db.rawQuery("SELECT ID FROM "+TABLE_NAME+" WHERE PATH='"+file_path+"'", null);
        return res.getCount() > 0;
    }

    public long getItemCount() {
        SQLiteDatabase db=this.getReadableDatabase();
        long count = DatabaseUtils.queryNumEntries(db,TABLE_NAME);
        return count;
    }

    public void delete() {
        SQLiteDatabase db=this.getWritableDatabase();
        db.delete(TABLE_NAME,null,null);
    }

    public void deleteOne(String path) {
        SQLiteDatabase db=this.getWritableDatabase();
        db.execSQL("DELETE FROM "+TABLE_NAME+" WHERE PATH='"+path+"'");
    }
}
