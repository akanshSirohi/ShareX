package com.akansh.sharex.server;

public class ThemesData {
    private final String[] displayList = { "Default", "Zen Inspired", "2077", "hex", "Purple Rain", "Light Green", "Claude +" };
    private final String[] prefixes = { "default", "zen", "2077", "hex", "purple-rain", "light-green", "claude-plus" };

    public String[] getDisplayList() { return displayList; }
    public String getDisplayItem(int idx) { return displayList[normalizeIndex(idx)]; }
    public String getPrefix(int idx) { return prefixes[normalizeIndex(idx)]; }
    public int normalizeIndex(int idx) { return idx >= 0 && idx < displayList.length ? idx : 0; }
}
