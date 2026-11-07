/*
 * Decompiled with CFR 0.152.
 */
package com.laoda.aichatpro;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class Json {
    private final String src;
    private int pos;

    private Json(String string) {
        this.src = string;
    }

    public static Object parse(String string) {
        if (string == null || string.trim().isEmpty()) {
            return null;
        }
        Json json = new Json(string);
        json.skipWhitespace();
        if (json.pos >= string.length()) {
            return null;
        }
        return json.value();
    }

    public static Object at(Object object, String string) {
        String[] stringArray;
        if (object == null || string == null || string.isEmpty()) {
            return object;
        }
        Object object2 = object;
        String[] stringArray2 = stringArray = string.split("\\.");
        int n = stringArray2.length;
        for (int i = 0; i < n; ++i) {
            int n2;
            String string2;
            String string3 = string2 = stringArray2[i];
            do {
                int n3;
                if ((n3 = string3.indexOf(91)) < 0) {
                    object2 = Json.step(object2, string3);
                    break;
                }
                String string4 = string3.substring(0, n3);
                if (!string4.isEmpty()) {
                    object2 = Json.step(object2, string4);
                }
                if (object2 == null) {
                    return null;
                }
                n2 = string3.indexOf(93, n3);
                if (n2 < 0) {
                    return null;
                }
                object2 = Json.index(object2, string3.substring(n3 + 1, n2));
            } while (!(string3 = string3.substring(n2 + 1)).isEmpty());
            if (object2 != null) continue;
            return null;
        }
        return object2;
    }

    public static String stringAt(Object object, String string) {
        Object object2 = Json.at(object, string);
        return object2 == null ? null : String.valueOf(object2);
    }

    public static int intAt(Object object, String string, int n) {
        Object object2 = Json.at(object, string);
        if (object2 instanceof Number) {
            return ((Number)object2).intValue();
        }
        if (object2 instanceof String) {
            try {
                return (int)Double.parseDouble((String)object2);
            }
            catch (NumberFormatException numberFormatException) {
                return n;
            }
        }
        return n;
    }

    private static Object step(Object object, String string) {
        if (object instanceof Map) {
            return ((Map)object).get(string);
        }
        return null;
    }

    private static Object index(Object object, String string) {
        if (object instanceof List) {
            List list = (List)object;
            try {
                int n = Integer.parseInt(string.trim());
                return n >= 0 && n < list.size() ? list.get(n) : null;
            }
            catch (NumberFormatException numberFormatException) {
                return null;
            }
        }
        if (object instanceof Map) {
            return ((Map)object).get(string.trim());
        }
        return null;
    }

    public static String escape(String string) {
        if (string == null) {
            return "";
        }
        StringBuilder stringBuilder = new StringBuilder(string.length() + 16);
        block9: for (int i = 0; i < string.length(); ++i) {
            char c = string.charAt(i);
            switch (c) {
                case '\"': {
                    stringBuilder.append("\\\"");
                    continue block9;
                }
                case '\\': {
                    stringBuilder.append("\\\\");
                    continue block9;
                }
                case '\n': {
                    stringBuilder.append("\\n");
                    continue block9;
                }
                case '\r': {
                    stringBuilder.append("\\r");
                    continue block9;
                }
                case '\t': {
                    stringBuilder.append("\\t");
                    continue block9;
                }
                case '\b': {
                    stringBuilder.append("\\b");
                    continue block9;
                }
                case '\f': {
                    stringBuilder.append("\\f");
                    continue block9;
                }
                default: {
                    if (c < ' ') {
                        stringBuilder.append(String.format("\\u%04x", (int)c));
                        continue block9;
                    }
                    stringBuilder.append(c);
                }
            }
        }
        return stringBuilder.toString();
    }

    private void skipWhitespace() {
        while (this.pos < this.src.length() && Character.isWhitespace(this.src.charAt(this.pos))) {
            ++this.pos;
        }
    }

    private char peek() {
        return this.pos < this.src.length() ? this.src.charAt(this.pos) : (char)'\u0000';
    }

    private Object value() {
        this.skipWhitespace();
        char c = this.peek();
        if (c == '{') {
            return this.object();
        }
        if (c == '[') {
            return this.array();
        }
        if (c == '\"') {
            return this.string();
        }
        if (c == 't') {
            this.expect("true");
            return Boolean.TRUE;
        }
        if (c == 'f') {
            this.expect("false");
            return Boolean.FALSE;
        }
        if (c == 'n') {
            this.expect("null");
            return null;
        }
        return this.number();
    }

    private Map<String, Object> object() {
        LinkedHashMap<String, Object> linkedHashMap = new LinkedHashMap<String, Object>();
        ++this.pos;
        this.skipWhitespace();
        if (this.peek() == '}') {
            ++this.pos;
            return linkedHashMap;
        }
        while (this.pos < this.src.length()) {
            this.skipWhitespace();
            String string = this.string();
            this.skipWhitespace();
            if (this.peek() == ':') {
                ++this.pos;
            }
            linkedHashMap.put(string, this.value());
            this.skipWhitespace();
            char c = this.peek();
            if (c == ',') {
                ++this.pos;
                continue;
            }
            if (c != '}') break;
            ++this.pos;
            break;
        }
        return linkedHashMap;
    }

    private List<Object> array() {
        ArrayList<Object> arrayList = new ArrayList<Object>();
        ++this.pos;
        this.skipWhitespace();
        if (this.peek() == ']') {
            ++this.pos;
            return arrayList;
        }
        while (this.pos < this.src.length()) {
            arrayList.add(this.value());
            this.skipWhitespace();
            char c = this.peek();
            if (c == ',') {
                ++this.pos;
                continue;
            }
            if (c != ']') break;
            ++this.pos;
            break;
        }
        return arrayList;
    }

    private String string() {
        char c;
        this.skipWhitespace();
        if (this.peek() != '\"') {
            return "";
        }
        ++this.pos;
        StringBuilder stringBuilder = new StringBuilder();
        block10: while (this.pos < this.src.length() && (c = this.src.charAt(this.pos++)) != '\"') {
            if (c == '\\') {
                if (this.pos >= this.src.length()) break;
                char c2 = this.src.charAt(this.pos++);
                switch (c2) {
                    case 'n': {
                        stringBuilder.append('\n');
                        break;
                    }
                    case 't': {
                        stringBuilder.append('\t');
                        break;
                    }
                    case 'r': {
                        stringBuilder.append('\r');
                        break;
                    }
                    case 'b': {
                        stringBuilder.append('\b');
                        break;
                    }
                    case 'f': {
                        stringBuilder.append('\f');
                        break;
                    }
                    case 'u': {
                        if (this.pos + 4 > this.src.length()) continue block10;
                        try {
                            stringBuilder.append((char)Integer.parseInt(this.src.substring(this.pos, this.pos + 4), 16));
                        }
                        catch (NumberFormatException numberFormatException) {
                            // empty catch block
                        }
                        this.pos += 4;
                        break;
                    }
                    default: {
                        stringBuilder.append(c2);
                        break;
                    }
                }
                continue;
            }
            stringBuilder.append(c);
        }
        return stringBuilder.toString();
    }

    private Object number() {
        int n = this.pos;
        while (this.pos < this.src.length()) {
            boolean bl;
            char c = this.src.charAt(this.pos);
            boolean bl2 = bl = c >= '0' && c <= '9' || c == '-' || c == '+' || c == '.' || c == 'e' || c == 'E';
            if (!bl) break;
            ++this.pos;
        }
        String string = this.src.substring(n, this.pos);
        try {
            return Double.valueOf(string);
        }
        catch (NumberFormatException numberFormatException) {
            return null;
        }
    }

    private void expect(String string) {
        this.pos = this.src.startsWith(string, this.pos) ? (this.pos += string.length()) : ++this.pos;
    }
}

