/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.bukkit.entity.Player
 *  org.bukkit.event.EventHandler
 *  org.bukkit.event.EventPriority
 *  org.bukkit.event.Listener
 *  org.bukkit.event.player.AsyncPlayerChatEvent
 */
package com.laoda.aichatpro;

import com.laoda.aichatpro.AIChatPro;
import com.laoda.aichatpro.MessageStore;
import com.laoda.aichatpro.Settings;
import com.laoda.aichatpro.Util;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Pattern;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

public class ChatListener
implements Listener {
    private final AIChatPro plugin;

    public ChatListener(AIChatPro aIChatPro) {
        this.plugin = aIChatPro;
    }

    @EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
    public void onChat(AsyncPlayerChatEvent asyncPlayerChatEvent) {
        String string;
        Settings settings = this.plugin.getSettings();
        Player player = asyncPlayerChatEvent.getPlayer();
        String string2 = asyncPlayerChatEvent.getMessage();
        if (string2 == null || string2.trim().isEmpty()) {
            return;
        }
        for (Pattern object2 : settings.filter.ignorePatterns) {
            if (!object2.matcher(string2).matches()) continue;
            return;
        }
        final String message = string2;
        org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> process(player, message));
    }

    private void process(Player player, String string2) {
        if (!plugin.isEnabled() || !player.isOnline()) return;
        Settings settings = plugin.getSettings();
        String string;
        this.plugin.getMessageStore().add(new MessageStore.ChatMessage(player.getName(), player.getWorld().getName(), string2, System.currentTimeMillis()));
        if (this.plugin.getAskService() == null) {
            return;
        }
        String string22 = player.getWorld().getName().toLowerCase();
        if (settings.trigger.enabled && !settings.trigger.disabledWorlds.contains(string22) && (string = this.extractQuestion(string2, settings.trigger)) != null) {
            this.plugin.getAskService().ask(player, string, 0);
            return;
        }
        this.maybeRandom(player, string2, settings, string22);
    }

    private void maybeRandom(Player player, String string, Settings settings, String string2) {
        long l;
        Settings.RandomTrigger randomTrigger = settings.randomTrigger;
        if (!randomTrigger.enabled) {
            return;
        }
        if (randomTrigger.disabledWorlds.contains(string2)) {
            return;
        }
        if (randomTrigger.requirePermission && !player.hasPermission(randomTrigger.permission)) {
            return;
        }
        String string3 = string.trim();
        if (string3.length() < randomTrigger.minLength) {
            return;
        }
        String string4 = string3.toLowerCase();
        for (String string5 : randomTrigger.ignoreWords) {
            if (!string4.contains(string5)) continue;
            return;
        }
        long l2 = (long)Math.max(randomTrigger.cooldownSeconds, randomTrigger.ignoreAfterAnyAskSeconds) * 1000L;
        if (l2 > 0L && (l = System.currentTimeMillis() - this.plugin.getAskService().lastAskMillis(player.getUniqueId())) < l2) {
            return;
        }
        if (ThreadLocalRandom.current().nextDouble() >= randomTrigger.chance) {
            return;
        }
        this.plugin.getAskService().ask(player, string3, 2);
    }

    private String extractQuestion(String string, Settings.Trigger trigger) {
        for (String string2 : trigger.prefixes) {
            if (string2 == null || string2.isEmpty()) continue;
            if (trigger.containsMode) {
                int n = Util.indexOfIgnoreCase(string, string2);
                if (n < 0) continue;
                return trigger.stripPrefix ? string.substring(n + string2.length()).trim() : string.trim();
            }
            if (!Util.startsWithIgnoreCase(string, string2)) continue;
            return trigger.stripPrefix ? string.substring(string2.length()).trim() : string.trim();
        }
        return null;
    }
}

