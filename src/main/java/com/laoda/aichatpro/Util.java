/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.bukkit.ChatColor
 */
package com.laoda.aichatpro;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.ChatColor;

public final class Util {
    private Util() {
    }

    public static String color(String string) {
        if (string == null) {
            return "";
        }
        return ChatColor.translateAlternateColorCodes((char)'&', (String)string);
    }

    public static String format(String string, Object ... objectArray) {
        if (string == null) {
            return "";
        }
        String string2 = string;
        int n = 0;
        while (n + 1 < objectArray.length) {
            String string3 = String.valueOf(objectArray[n]);
            String string4 = String.valueOf(objectArray[n + 1]);
            string2 = string2.replace("{" + string3 + "}", string4);
            n += 2;
        }
        return string2;
    }

    public static boolean startsWithIgnoreCase(String string, String string2) {
        if (string == null || string2 == null || string2.isEmpty()) {
            return false;
        }
        return string.regionMatches(true, 0, string2, 0, string2.length());
    }

    public static int indexOfIgnoreCase(String string, String string2) {
        if (string == null || string2 == null || string2.isEmpty()) {
            return -1;
        }
        int n = string.length() - string2.length();
        for (int i = 0; i <= n; ++i) {
            if (!string.regionMatches(true, i, string2, 0, string2.length())) continue;
            return i;
        }
        return -1;
    }

    public static List<String> split(String string, int n) {
        String[] stringArray;
        ArrayList<String> arrayList = new ArrayList<String>();
        if (string == null) {
            return arrayList;
        }
        int n2 = n <= 0 ? 100 : n;
        for (String string2 : stringArray = string.replace("\r", "").split("\n", -1)) {
            String string3 = string2.trim();
            if (string3.isEmpty()) continue;
            while (string3.length() > n2) {
                String string4;
                int n3 = -1;
                n3 = Math.max(n3, string3.lastIndexOf(32, n2));
                n3 = Math.max(n3, string3.lastIndexOf(12290, n2));
                n3 = Math.max(n3, string3.lastIndexOf(65292, n2));
                n3 = Math.max(n3, string3.lastIndexOf(65307, n2));
                n3 = Math.max(n3, string3.lastIndexOf(65281, n2));
                if ((n3 = Math.max(n3, string3.lastIndexOf(65311, n2))) <= 0) {
                    n3 = n2;
                }
                if (!(string4 = string3.substring(0, n3).trim()).isEmpty()) {
                    arrayList.add(string4);
                }
                string3 = string3.substring(n3).trim();
            }
            if (string3.isEmpty()) continue;
            arrayList.add(string3);
        }
        return arrayList;
    }

    public static String stripMarkdown(String string) {
        if (string == null) {
            return "";
        }
        String string2 = string.replace("**", "").replace("__", "").replace("`", "");
        string2 = string2.replaceAll("(?m)^\\s*#{1,6}\\s*", "");
        string2 = string2.replaceAll("(?m)^\\s*>\\s?", "");
        string2 = string2.replaceAll("(?m)^\\s*[-*+]\\s+", "");
        string2 = string2.replace("*", "");
        return string2.trim();
    }

    public static String join(List<String> list, String string) {
        StringBuilder stringBuilder = new StringBuilder();
        for (int i = 0; i < list.size(); ++i) {
            if (i > 0) {
                stringBuilder.append(string);
            }
            stringBuilder.append(list.get(i));
        }
        return stringBuilder.toString();
    }
}

