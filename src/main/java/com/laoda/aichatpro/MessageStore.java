/*
 * Decompiled with CFR 0.152.
 */
package com.laoda.aichatpro;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

public class MessageStore {
    private final Deque<ChatMessage> buffer = new ArrayDeque<ChatMessage>();
    private int capacity;
    private long sequence;

    public MessageStore(int n) {
        this.capacity = Math.max(10, n);
    }

    public synchronized void setCapacity(int n) {
        this.capacity = Math.max(10, n);
        while (this.buffer.size() > this.capacity) {
            this.buffer.pollFirst();
        }
    }

    public synchronized void add(ChatMessage chatMessage) {
        this.buffer.addLast(new ChatMessage(chatMessage.player, chatMessage.world, chatMessage.message, chatMessage.time, ++sequence));
        while (this.buffer.size() > this.capacity) {
            this.buffer.pollFirst();
        }
    }

    public synchronized List<ChatMessage> last(int n) {
        ArrayList<ChatMessage> arrayList = new ArrayList<ChatMessage>(this.buffer);
        if (n <= 0) return new ArrayList<ChatMessage>();
        if (n >= arrayList.size()) {
            return arrayList;
        }
        return new ArrayList<ChatMessage>(arrayList.subList(arrayList.size() - n, arrayList.size()));
    }

    public synchronized List<ChatMessage> since(long after, int limit, java.util.Set<String> excludedWorlds) {
        List<ChatMessage> result = new ArrayList<ChatMessage>();
        if (limit <= 0) return result;
        for (ChatMessage message : buffer) {
            if (message.sequence > after && !excludedWorlds.contains(message.world.toLowerCase(java.util.Locale.ROOT))) {
                result.add(message);
                if (result.size() == limit) break;
            }
        }
        return result;
    }

    public synchronized int size() {
        return this.buffer.size();
    }

    public synchronized void clear() {
        this.buffer.clear();
    }

    public static class ChatMessage {
        public final String player;
        public final String world;
        public final String message;
        public final long time;
        public final long sequence;

        public ChatMessage(String string, String string2, String string3, long l) {
            this(string, string2, string3, l, 0L);
        }

        private ChatMessage(String string, String string2, String string3, long l, long sequence) {
            this.sequence = sequence;
            this.player = string;
            this.world = string2;
            this.message = string3;
            this.time = l;
        }

        public String line() {
            return this.player + ": " + this.message;
        }
    }
}

