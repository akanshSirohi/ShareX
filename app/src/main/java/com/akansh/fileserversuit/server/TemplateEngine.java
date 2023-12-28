package com.akansh.fileserversuit.server;

import android.content.Context;
import android.util.Log;

import com.akansh.fileserversuit.common.Constants;

import java.io.FileInputStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Author: Akansh Sirohi
 * Date  : 1/30/2018.
 * <p>
 *      Custom HTML TemplateEngine for ShareX.
 *      It is used to render the web interface and its components.
 * </p>
 */

public class TemplateEngine {

    enum RENDER_TYPE {
        NORMAL, PLUGIN
    }

    Context ctx;

    // Special Variables
    private String plugin_uid, base_url, plugin_dev_dir;

    public TemplateEngine(Context ctx) {
        this.ctx = ctx;
    }

    public void setPlugin_uid(String plugin_uid) {
        this.plugin_uid = plugin_uid;
    }

    public void setBase_url(String base_url) {
        this.base_url = base_url;
    }

    public void setPlugin_dev_dir(String plugin_dev_dir) {
        this.plugin_dev_dir = plugin_dev_dir;
    }

    public String renderHtml(String file, RENDER_TYPE type) {
        String html = readFile(file);
        return compileHtml(html, type);
    }

    private String readFile(String path) {
        try {
            FileInputStream fis = new FileInputStream(path);
            byte[] data = new byte[fis.available()];
            fis.read(data);
            fis.close();
            return new String(data);
        } catch (Exception e) {
            return null;
        }
    }

    public String compileHtml(String html, RENDER_TYPE type) {
        if(html != null && html.length() > 0) {
            Pattern pattern = Pattern.compile("\\{%\\s*(.*?)\\s*%\\}");
            Matcher matcher = pattern.matcher(html);

            StringBuilder result = new StringBuilder();
            int lastIndex = 0;

            while (matcher.find()) {
                result.append(html.substring(lastIndex, matcher.start()));
                String command = matcher.group(1).trim();
                if (command.startsWith(">>")) {
                    String path = command.replace(">>", "").trim();
                    String fileContent = compileHtml(readFile(getFileProperPath(path, type)), type);
                    if (fileContent == null) {
                        fileContent = "404:File Not Found";
                    }
                    result.append(fileContent);
                }else if(command.startsWith("BASE_URL")) {
                    result.append(base_url);
                }
                lastIndex = matcher.end();
            }
            result.append(html.substring(lastIndex));
            return result.toString();
        }else{
            return "";
        }
    }

    private String getFileProperPath(String path, RENDER_TYPE type) {
        String base;
        if (type == RENDER_TYPE.NORMAL) {
            base = Constants.NEW_DIR;
        } else {
            base = "plugins";
        }
        if (path.startsWith("/")) {
            path = path.substring(1);
        }

        if (type == RENDER_TYPE.NORMAL) {
            return "/data/data/" + ctx.getPackageName() + "/" + base + "/" + path;
        } else {
            if (plugin_uid == "debug") {
                return plugin_dev_dir + "/" + path;
            } else {
                return "/data/data/" + ctx.getPackageName() + "/" + base + "/" + plugin_uid + "/" + path;
            }
        }
    }
}
