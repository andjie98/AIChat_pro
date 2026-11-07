/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.bukkit.Bukkit
 *  org.bukkit.OfflinePlayer
 *  org.bukkit.entity.Player
 */
package com.laoda.aichatpro;

import com.laoda.aichatpro.AIChatPro;
import com.laoda.aichatpro.LimitManager;
import com.laoda.aichatpro.Settings;
import com.laoda.aichatpro.UsageRecord;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

public class PlaceholderService {
    private final AIChatPro plugin;

    public PlaceholderService(AIChatPro aIChatPro) {
        this.plugin = aIChatPro;
    }

    public String apply(String string, OfflinePlayer offlinePlayer) {
        if (string == null || string.indexOf(123) < 0) {
            return string;
        }
        String string2 = string;
        for (Map.Entry<String, String> entry : this.values(offlinePlayer).entrySet()) {
            string2 = string2.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return string2;
    }

    public String resolve(String string, OfflinePlayer offlinePlayer) {
        if (string == null) {
            return "";
        }
        String string2 = this.values(offlinePlayer).get(string.toLowerCase());
        return string2 == null ? "" : string2;
    }

    public Map<String, String> values(OfflinePlayer offlinePlayer) {
        LinkedHashMap<String, String> linkedHashMap = new LinkedHashMap<String, String>();
        Settings settings = this.plugin.getSettings();
        LimitManager limitManager = this.plugin.getLimitManager();
        String string = settings.api.model;
        UsageRecord usageRecord = limitManager.serverUsage();
        UsageRecord usageRecord2 = offlinePlayer == null ? new UsageRecord() : limitManager.playerUsage(offlinePlayer.getUniqueId());
        linkedHashMap.put("tokens_server", String.valueOf(usageRecord.totalTokens()));
        linkedHashMap.put("tokens_server_in", String.valueOf(usageRecord.promptTokens));
        linkedHashMap.put("tokens_server_out", String.valueOf(usageRecord.completionTokens));
        linkedHashMap.put("cost_server", usageRecord.costText(settings.usage, string));
        linkedHashMap.put("requests_server", String.valueOf(usageRecord.requests));
        linkedHashMap.put("tokens_player", String.valueOf(usageRecord2.totalTokens()));
        linkedHashMap.put("tokens_player_in", String.valueOf(usageRecord2.promptTokens));
        linkedHashMap.put("tokens_player_out", String.valueOf(usageRecord2.completionTokens));
        linkedHashMap.put("cost_player", usageRecord2.costText(settings.usage, string));
        linkedHashMap.put("requests_player", String.valueOf(usageRecord2.requests));
        int n = offlinePlayer == null ? 0 : limitManager.used(offlinePlayer.getUniqueId());
        int n2 = offlinePlayer instanceof Player ? limitManager.resolveLimit((Player)offlinePlayer) : limitManager.defaultLimit();
        linkedHashMap.put("used", String.valueOf(n));
        linkedHashMap.put("limit", String.valueOf(n2));
        linkedHashMap.put("left", String.valueOf(Math.max(0, n2 - n)));
        linkedHashMap.put("reset", limitManager.resetText());
        linkedHashMap.put("online", String.valueOf(this.plugin.getServerInfoService().onlineCount()));
        linkedHashMap.put("max_players", String.valueOf(Bukkit.getMaxPlayers()));
        double[] dArray = this.plugin.getServerInfoService().tpsSnapshot();
        linkedHashMap.put("tps", dArray == null ? "\u672a\u77e5" : String.format(Locale.ROOT, "%.1f", dArray[0]));
        linkedHashMap.put("model", string == null ? "" : string);
        return linkedHashMap;
    }
}

