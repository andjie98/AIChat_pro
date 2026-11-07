/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  me.clip.placeholderapi.expansion.PlaceholderExpansion
 *  org.bukkit.OfflinePlayer
 *  org.bukkit.entity.Player
 */
package com.laoda.aichatpro.hook;

import com.laoda.aichatpro.AIChatPro;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

public class AIChatExpansion
extends PlaceholderExpansion {
    private final AIChatPro plugin;

    public AIChatExpansion(AIChatPro aIChatPro) {
        this.plugin = aIChatPro;
    }

    public String getIdentifier() {
        return "aichatpro";
    }

    public String getAuthor() {
        return "laoda";
    }

    public String getVersion() {
        return this.plugin.getDescription().getVersion();
    }

    public boolean persist() {
        return true;
    }

    public boolean canRegister() {
        return true;
    }

    public String onPlaceholderRequest(Player player, String string) {
        return this.plugin.getPlaceholders().resolve(string, (OfflinePlayer)player);
    }
}

