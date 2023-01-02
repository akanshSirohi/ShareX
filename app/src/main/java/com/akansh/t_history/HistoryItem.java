package com.akansh.t_history;

public class HistoryItem {

    // Data Items
    private int item_type;
    private String
            file_name, size,
            date, time,
            type, path;
    private int uid;

    // Constructor
    public HistoryItem(int item_type, String file_name, String size, String date, String time, String type, String path) {
        this.item_type = item_type;
        this.file_name = file_name;
        this.size = size;
        this.date = date;
        this.time = time;
        this.type = type;
        this.path = path;
    }

    public int getItem_type() {
        return item_type;
    }

    public String getFile_name() {
        return file_name;
    }

    public String getSize() {
        return size;
    }

    public String getDate() {
        return date;
    }

    public String getTime() {
        return time;
    }

    public String getType() {
        return type;
    }

    public String getPath() {
        return path;
    }

    public int getUid() {
        return uid;
    }

    public void setUid(int uid) {
        this.uid = uid;
    }
}