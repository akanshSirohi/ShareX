package com.akansh.fileserversuit;

import android.content.Context;
import android.util.Log;

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

    Context ctx;

    public TemplateEngine(Context ctx) {
        this.ctx = ctx;
    }

    public String renderHtml(String file) {
        String html = readFile(file);
        return compileHtml(html);
    }

    public String compileHtml(String html) {
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
                    String fileContent = compileHtml(readFile(getFileProperPath(path)));
                    if (fileContent == null) {
                        fileContent = "404:File Not Found";
                    }
                    result.append(fileContent);
                }
                lastIndex = matcher.end();
            }
            result.append(html.substring(lastIndex));
            return result.toString();
        }else{
            return "";
        }
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

    private String getFileProperPath(String path) {
        if (path.startsWith("/")) {
            path = path.substring(1);
        }
        return "/data/data/" + ctx.getPackageName() + "/" + Constants.NEW_DIR + "/" + path;
    }
}
