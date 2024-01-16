package com.akansh.fileserversuit.common;
import static org.apache.commons.lang3.math.NumberUtils.isDigits;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.google.common.base.Splitter;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;

import java.util.List;

public class JSONHelper {
    static final ObjectMapper mapper = new ObjectMapper();

    public static void deeplySet(DocumentContext json, JsonPath path, Object value) {
        deeplySet(json, path, value, true);
    }

    public static void deeplySet(DocumentContext json, JsonPath path, Object value, boolean expandArray) {
        List<String> parts = Splitter
                .on('.')
                .omitEmptyStrings()
                .splitToList(path.getPath().replaceAll("(\\]\\[)|\\[|\\]", ".").replaceAll("\\$|'", ""));

        String currentPath = "$";
        String prevPath = currentPath;

        // traverse the JSON path and create parent if it doesn't already exist
        for (int i = 0; i < parts.size(); i++) {
            String node = parts.get(i);
            var isNodeArray = isDigits(parts.get(i));

            prevPath = currentPath;
            currentPath = isNodeArray
                    ? currentPath + "[" + node + "]"
                    : currentPath + "." + parts.get(i);

            var nodeValue = json.read(currentPath, JsonNode.class);

            /**
             * if element is an array, then do the following
             * 1. if there is existing array, check if it has enough capacity, if not expand it
             * 2. create a new array with an initial capacity if  none exists yet
             */
            if (isNodeArray && expandArray) {
                var nodeIndex = Integer.valueOf(node);
                var expandSize = nodeIndex + 1;
                ArrayNode arrayNode = null;

                var prevNodeValue = json.read(prevPath);
                if (prevNodeValue instanceof ArrayNode) {
                    arrayNode = (ArrayNode) prevNodeValue;
                    // re-adjust expand size to account for existing size
                    expandSize = Math.max(expandSize - arrayNode.size(), 0);
                } else {
                    arrayNode = mapper.createArrayNode();
                }

                // expand the array
                for (int cnt = 0; cnt < expandSize; cnt++) {
                    arrayNode.addObject();
                }

                // ensure the node is non-null so we could set the children later on
                if (arrayNode.get(nodeIndex).isNull()) {
                    arrayNode.insertObject(nodeIndex);
                }

                // set the array back to the json
                json.set(prevPath, arrayNode);
            } else if (nodeValue == null || nodeValue.isNull()) {
                json.put(prevPath, node, mapper.createObjectNode());
            }
        }
        json.set(path, value);
    }
}
