package com.akansh.t_history;

import android.content.Context;
import android.database.Cursor;

import java.util.ArrayList;
import java.util.HashMap;

public class HistoryDBManager {
    HistoryDBHelper historyDBHelper;

    public HistoryDBManager(Context ctx) {
        historyDBHelper=new HistoryDBHelper(ctx);
    }

    public void addTransferHistory(int item_type, String file_name, String size, String date, String time, String type, String path) {
        HashMap<String,String> values=new HashMap<>();
        values.put("file_name",file_name);
        values.put("item_type",String.valueOf(item_type));
        values.put("size",size);
        values.put("date",date);
        values.put("time",time);
        values.put("type",type);
        values.put("path",path);
        historyDBHelper.insertData(values);
    }

    public ArrayList<HistoryItem> getHistory() {
        ArrayList<HistoryItem> historyItems=new ArrayList<>();
        Cursor cursor=historyDBHelper.getData();
        while(cursor.moveToNext()) {
            HistoryItem item=new HistoryItem(Integer.parseInt(cursor.getString(2)),cursor.getString(1),cursor.getString(3),cursor.getString(4),cursor.getString(5),cursor.getString(6),cursor.getString(7));
            item.setUid(cursor.getInt(0));
            historyItems.add(item);
        }
        return historyItems;
    }

    public void restoreHistory(HistoryItem historyItem) {
        HashMap<String,String> values=new HashMap<>();
        values.put("uid",String.valueOf(historyItem.getUid()));
        values.put("file_name",historyItem.getFile_name());
        values.put("item_type",String.valueOf(historyItem.getItem_type()));
        values.put("size",historyItem.getSize());
        values.put("date",historyItem.getDate());
        values.put("time",historyItem.getTime());
        values.put("type",historyItem.getType());
        values.put("path",historyItem.getPath());
        historyDBHelper.restoreData(values);
    }

    public int getItemsCount() {
        return (int) historyDBHelper.getItemCount();
    }

    public void deleteHistory(String path) {
        historyDBHelper.deleteOne(path);
    }

    public void clearHistory() {
        historyDBHelper.delete();
    }
}
