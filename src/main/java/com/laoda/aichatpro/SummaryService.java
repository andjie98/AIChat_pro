/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.bukkit.Bukkit
 *  org.bukkit.command.CommandSender
 *  org.bukkit.plugin.Plugin
 *  org.bukkit.scheduler.BukkitTask
 */
package com.laoda.aichatpro;

import com.laoda.aichatpro.AIChatPro;
import com.laoda.aichatpro.AIService;
import com.laoda.aichatpro.MessageStore;
import com.laoda.aichatpro.Settings;
import com.laoda.aichatpro.Util;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

public class SummaryService {
    private final AIChatPro plugin;
    private BukkitTask task;
    private long summaryWatermark;
    private final java.util.concurrent.atomic.AtomicBoolean autoPending = new java.util.concurrent.atomic.AtomicBoolean();

    public SummaryService(AIChatPro aIChatPro) {
        this.plugin = aIChatPro;
    }

    public void start() {
        this.stop();
        Settings settings = this.plugin.getSettings();
        if (!settings.summary.enabled) {
            this.plugin.getLogger().info("\u81ea\u52a8\u603b\u7ed3\u672a\u542f\u7528\uff08auto-summary.enabled = false\uff09\u3002");
            return;
        }
        long l = (long)Math.max(1, settings.summary.intervalMinutes) * 60L * 20L;
        this.task = Bukkit.getScheduler().runTaskTimer((Plugin)this.plugin, new Runnable(){

            @Override
            public void run() {
                SummaryService.this.autoSummary();
            }
        }, l, l);
        this.plugin.getLogger().info("\u81ea\u52a8\u603b\u7ed3\u5df2\u542f\u52a8\uff1a\u6bcf " + settings.summary.intervalMinutes + " \u5206\u949f\u603b\u7ed3\u6700\u8fd1 " + settings.summary.messageCount + " \u6761\u6d88\u606f\u3002");
    }

    public void stop() {
        if (this.task != null) {
            this.task.cancel();
            this.task = null;
        }
    }

    private void autoSummary() {
        Settings settings = this.plugin.getSettings();
        if (autoPending.get()) return;
        List<MessageStore.ChatMessage> list = plugin.getMessageStore().since(summaryWatermark,
                settings.summary.messageCount, settings.summary.disabledWorlds);
        if (list.size() < settings.summary.minMessages) {
            this.plugin.debug("\u672c\u6b21\u8df3\u8fc7\u603b\u7ed3\uff1a\u6d88\u606f\u4e0d\u8db3\uff08" + list.size() + "/" + settings.summary.minMessages + "\uff09\u3002");
            return;
        }
        if (!this.plugin.getAiService().isConfigured()) {
            this.plugin.debug("\u672c\u6b21\u8df3\u8fc7\u603b\u7ed3\uff1a\u672a\u914d\u7f6e API \u5bc6\u94a5\u3002");
            if (settings.summary.fallbackBroadcastRaw) {
                this.broadcast(Util.format(settings.summary.broadcastFormat, "summary", this.transcript(list)));
                summaryWatermark = list.get(list.size() - 1).sequence;
            }
            return;
        }
        if (!autoPending.compareAndSet(false, true)) return;
        this.requestSummary(list, null, null, true);
    }

    public void manual(CommandSender commandSender) {
        Settings settings = this.plugin.getSettings();
        List<MessageStore.ChatMessage> list = this.collect(settings.summary.messageCount, settings.summary.disabledWorlds);
        if (list.isEmpty()) {
            commandSender.sendMessage(Util.color(settings.messages.prefix + "&e\u73b0\u5728\u8fd8\u6ca1\u6709\u804a\u5929\u8bb0\u5f55\u53ef\u4ee5\u603b\u7ed3\u3002"));
            return;
        }
        if (!this.plugin.getAiService().isConfigured()) {
            commandSender.sendMessage(Util.color(settings.messages.prefix + settings.output.noApiMessage));
            return;
        }
        org.bukkit.entity.Player player = commandSender instanceof org.bukkit.entity.Player ? (org.bukkit.entity.Player)commandSender : null;
        LimitManager.Reservation reservation = plugin.getLimitManager().reserve(player,
                LimitManager.ACTION_MANUAL_SUMMARY, settings.trigger.cooldownSeconds);
        if (!reservation.allowed()) {
            commandSender.sendMessage(Util.color(reservation.error));
            return;
        }
        commandSender.sendMessage(Util.color(settings.messages.prefix + "&7\u6b63\u5728\u628a\u6700\u8fd1 " + list.size() + " \u6761\u804a\u5929\u8bb0\u5f55\u4ea4\u7ed9 AI \u603b\u7ed3\u2026"));
        this.requestSummary(list, commandSender, reservation, false);
    }

    private void requestSummary(final List<MessageStore.ChatMessage> list, final CommandSender commandSender,
                                final LimitManager.Reservation reservation, final boolean automatic) {
        final org.bukkit.entity.Player player = commandSender instanceof org.bukkit.entity.Player ? (org.bukkit.entity.Player)commandSender : null;
        final java.util.UUID playerId = player == null ? null : player.getUniqueId();
        final String playerName = player == null ? null : player.getName();
        try {
        final Settings settings = this.plugin.getSettings();
        String string = "\u4f60\u662f\u4e00\u4e2a Minecraft \u670d\u52a1\u5668\u7684\u804a\u5929\u6574\u7406\u52a9\u624b\uff0c\u64c5\u957f\u628a\u96f6\u6563\u7684\u804a\u5929\u8bb0\u5f55\u6d53\u7f29\u6210\u7b80\u77ed\u6709\u8da3\u7684\u603b\u7ed3\u3002";
        String string2 = Util.format(settings.summary.prompt, "count", String.valueOf(list.size()), "server", settings.serverName);
        ArrayList<AIService.ChatTurn> arrayList = new ArrayList<AIService.ChatTurn>();
        arrayList.add(new AIService.ChatTurn("user", string2 + "\n\n\u3010\u804a\u5929\u8bb0\u5f55\u3011\n" + this.transcript(list)));
        this.plugin.getAiService().complete(string, arrayList, new AIService.Callback(){

            @Override
            public void success(AIService.Response response) {
                if (reservation != null) reservation.finish(true);
                if (automatic) {
                    summaryWatermark = list.get(list.size() - 1).sequence;
                    autoPending.set(false);
                }
                SummaryService.this.plugin.getLimitManager().recordUsage(playerId, playerName, response.promptTokens, response.completionTokens, response.cachedTokens);
                String string = settings.output.stripMarkdown ? Util.stripMarkdown(response.content) : response.content;
                SummaryService.this.broadcast(Util.format(settings.summary.broadcastFormat, "summary", string));
                if (settings.summary.logToFile) {
                    SummaryService.this.plugin.logToFile("[\u603b\u7ed3] \u5171 " + list.size() + " \u6761\uff1a" + string + " [tokens in=" + response.promptTokens + " out=" + response.completionTokens + "]");
                }
                if (commandSender != null) {
                    final String string2 = SummaryService.this.plugin.getPlaceholders().apply(settings.messages.prefix, null);
                    Bukkit.getScheduler().runTask((Plugin)SummaryService.this.plugin, new Runnable(){

                        @Override
                        public void run() {
                            commandSender.sendMessage(Util.color(string2 + "&a\u603b\u7ed3\u5df2\u751f\u6210\u5e76\u64ad\u62a5\u3002"));
                        }
                    });
                }
            }

            @Override
            public void failure(String string) {
                if (reservation != null) reservation.finish(false);
                if (automatic) autoPending.set(false);
                if ("cancelled".equals(string)) return;
                SummaryService.this.plugin.getLogger().warning("\u751f\u6210\u804a\u5929\u603b\u7ed3\u5931\u8d25\uff1a" + string);
                SummaryService.this.plugin.notifyAdmins("&c\u751f\u6210\u804a\u5929\u603b\u7ed3\u5931\u8d25\uff1a" + string);
                if (settings.summary.fallbackBroadcastRaw) {
                    SummaryService.this.broadcast(Util.format(settings.summary.broadcastFormat, "summary", SummaryService.this.transcript(list)));
                }
                if (commandSender != null) {
                    final String string2 = settings.messages.prefix;
                    final String string3 = Util.format(settings.output.errorMessage, "error", string);
                    Bukkit.getScheduler().runTask((Plugin)SummaryService.this.plugin, new Runnable(){

                        @Override
                        public void run() {
                            commandSender.sendMessage(Util.color(string2 + string3));
                        }
                    });
                }
            }
        });
        } catch (RuntimeException ex) {
            if (reservation != null) reservation.finish(false);
            if (automatic) autoPending.set(false);
            throw ex;
        }
    }

    private List<MessageStore.ChatMessage> collect(int n, Set<String> set) {
        List<MessageStore.ChatMessage> list = this.plugin.getMessageStore().last(n);
        if (set == null || set.isEmpty()) {
            return list;
        }
        ArrayList<MessageStore.ChatMessage> arrayList = new ArrayList<MessageStore.ChatMessage>();
        for (MessageStore.ChatMessage chatMessage : list) {
            if (set.contains(chatMessage.world.toLowerCase())) continue;
            arrayList.add(chatMessage);
        }
        return arrayList;
    }

    private String transcript(List<MessageStore.ChatMessage> list) {
        StringBuilder stringBuilder = new StringBuilder();
        for (MessageStore.ChatMessage chatMessage : list) {
            stringBuilder.append(chatMessage.line()).append('\n');
        }
        return stringBuilder.toString().trim();
    }

    private void broadcast(String string) {
        Settings settings = this.plugin.getSettings();
        final ArrayList<String> arrayList = new ArrayList<String>();
        String string2 = settings.output.stripMarkdown ? Util.stripMarkdown(string) : string;
        string2 = this.plugin.getPlaceholders().apply(string2, null);
        for (String string3 : Util.split(string2, settings.output.maxLineLength)) {
            arrayList.add(Util.color(string3));
        }
        if (settings.summary.broadcast && this.plugin.isEnabled()) {
            Bukkit.getScheduler().runTask((Plugin)this.plugin, new Runnable(){

                @Override
                public void run() {
                    for (String string : arrayList) {
                        Bukkit.broadcastMessage((String)string);
                    }
                }
            });
        } else {
            this.plugin.debug("\u603b\u7ed3\u672a\u64ad\u62a5\uff08auto-summary.broadcast = false\uff09\uff1a" + Util.join(arrayList, " / "));
        }
    }
}

