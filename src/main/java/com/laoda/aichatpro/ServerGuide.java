package com.laoda.aichatpro;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

final class ServerGuide {
    static final ServerGuide EMPTY = new ServerGuide(Collections.emptyList());
    private static final Pattern SERVER_QUESTION = Pattern.compile(
            "(本服|你服|云界|服务器|服里).{0,20}(怎么|如何|哪里|在哪|多少|什么|能不能|规则|指令|玩法|价格)"
            + "|(怎么|如何|哪里|在哪|多少|什么|能不能).{0,20}(本服|云界|领地|圈地|拍卖行|箱子商店|粘液科技|新手礼包|小黑塔|公益刷怪塔)"
            + "|(刷物|刷龙蛋).{0,12}(封|ban|处罚)");
    private final List<Entry> entries;

    private ServerGuide(List<Entry> entries) {
        this.entries = entries;
    }

    static ServerGuide parse(String markdown) {
        List<Entry> entries = new ArrayList<>();
        String title = null;
        List<String> aliases = new ArrayList<>();
        List<String> body = new ArrayList<>();
        for (String raw : markdown.split("\\R")) {
            String line = raw.trim();
            if (line.startsWith("## ")) {
                add(entries, title, aliases, body);
                title = line.substring(3).trim();
                aliases = new ArrayList<>();
                body = new ArrayList<>();
            } else if (title != null && (line.startsWith("别名：") || line.startsWith("别名:"))) {
                Collections.addAll(aliases, line.substring(3).split("[、，,；;|]"));
            } else if (title != null && !line.isEmpty()) {
                body.add(line);
            }
        }
        add(entries, title, aliases, body);
        return new ServerGuide(entries);
    }

    private static void add(List<Entry> entries, String title, List<String> aliases, List<String> body) {
        if (title != null && !title.isEmpty() && !body.isEmpty()) {
            aliases.add(title);
            entries.add(new Entry(title, String.join("\n", body), aliases));
        }
    }

    int size() {
        return entries.size();
    }

    static String normalize(String text) {
        return text.toLowerCase(Locale.ROOT).replaceAll("[\\p{P}\\p{Z}\\s]+", "");
    }

    static boolean isServerQuestion(String question) {
        return SERVER_QUESTION.matcher(question.toLowerCase(Locale.ROOT)).find();
    }

    List<Entry> find(String question) {
        String normalized = normalize(question);
        List<Entry> matches = new ArrayList<>();
        for (Entry entry : entries) {
            if (entry.score(normalized) > 0) matches.add(entry);
        }
        matches.sort(Comparator.comparingInt((Entry e) -> e.score(normalized)).reversed());
        return new ArrayList<>(matches.subList(0, Math.min(3, matches.size())));
    }

    static final class Entry {
        final String title;
        final String body;
        private final List<String> terms = new ArrayList<>();

        Entry(String title, String body, List<String> aliases) {
            this.title = title;
            this.body = body;
            for (String alias : aliases) {
                String term = normalize(alias);
                if (!term.isEmpty()) terms.add(term);
            }
        }

        int score(String normalized) {
            int best = 0;
            for (String term : terms) {
                if (normalized.equals(term)) best = Math.max(best, 1000 + term.length());
                else if (term.length() >= 4 && normalized.contains(term)) best = Math.max(best, term.length());
            }
            return best;
        }

        String answer() {
            return body + "\n资料：云界指南 · " + title;
        }
    }
}
