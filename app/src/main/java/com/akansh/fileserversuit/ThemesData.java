package com.akansh.fileserversuit;

public class ThemesData {
    private String[] displayList = {
            "Cyborg (Default)","Cosmo","Cerulean","Journal","Litera","Lumen","Materia","Morph"
    };
    private String[] prefixes = {
            "cyborg","cosmo","cerulean","journal","litera","lumen","materia","morph"
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
