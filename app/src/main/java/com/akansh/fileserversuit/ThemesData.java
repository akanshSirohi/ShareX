package com.akansh.fileserversuit;

public class ThemesData {
    private String[] displayList = {
            "Cyborg (Default)","Cosmo",
            "Cerulean","Journal",
            "Litera","Lumen",
            "Pulse","Simplex",
            "Sketchy","Spacelab",
            "United","Vapor",
            "Zephyr"
    };
    private String[] prefixes = {
            "cyborg","cosmo",
            "cerulean","journal",
            "litera","lumen",
            "pulse","simplex",
            "sketchy","spacelab",
            "united","vapor",
            "zephyr"
    };

    public String[] getDisplayList() {
        return displayList;
    }

    public String getDisplayItem(int idx) {
        return displayList[idx];
    }

    public String getPrefix(int idx) {
        return prefixes[idx];
    }
}
