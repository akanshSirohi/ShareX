package com.akansh.t_history;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.HashMap;

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

    public HistoryDBHelper(Context context) {
        super(context,DATABASE_NAME,null,1);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE "+TABLE_NAME+" (ID INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,FILE_NAME TEXT NOT NULL,ITEM_TYPE TEXT NOT NULL,SIZE TEXT NOT NULL,DATE_ TEXT NOT NULL,TIME_ TEXT NOT NULL,TYPE TEXT NOT NULL,PATH TEXT NOT NULL)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS "+TABLE_NAME);
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
        long result=db.insert(TABLE_NAME,null,contentValues);
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
        long result=db.insert(TABLE_NAME,null,contentValues);
        if(result == -1) {
            return false;
        }else{
            return true;
        }
    }

    public Cursor getData() {
        SQLiteDatabase db=this.getReadableDatabase();
        Cursor res=db.rawQuery("SELECT * FROM "+TABLE_NAME+" ORDER BY ID DESC", null);
        return res;
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

//    public boolean isFavExist(String url) {
//        SQLiteDatabase db=this.getReadableDatabase();
//        Cursor res=db.rawQuery("SELECT * FROM "+TABLE_NAME+" WHERE URL='"+url+"'", null);
//        if(res.getCount()>0) {
//            return true;
//        }else{
//            return false;
//        }
//    }
}
