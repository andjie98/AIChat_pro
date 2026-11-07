/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.bukkit.Bukkit
 *  org.bukkit.OfflinePlayer
 *  org.bukkit.command.Command
 *  org.bukkit.command.CommandExecutor
 *  org.bukkit.command.CommandSender
 *  org.bukkit.command.TabCompleter
 *  org.bukkit.entity.Player
 *  org.bukkit.plugin.Plugin
 */
package com.laoda.aichatpro;

import com.laoda.aichatpro.AIChatPro;
import com.laoda.aichatpro.AIService;
import com.laoda.aichatpro.LimitManager;
import com.laoda.aichatpro.Settings;
import com.laoda.aichatpro.UsageRecord;
import com.laoda.aichatpro.Util;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public class AIChatCommand
implements CommandExecutor,
TabCompleter {
    private final AIChatPro plugin;

    public AIChatCommand(AIChatPro aIChatPro) {
        this.plugin = aIChatPro;
    }

    public boolean onCommand(CommandSender commandSender, Command command, String string, String[] stringArray) {
        Settings settings = this.plugin.getSettings();
        if (stringArray.length == 0) {
            this.help(commandSender, string);
            return true;
        }
        String string2 = stringArray[0].toLowerCase();
        if (string2.equals("ask") || string2.equals("q") || string2.equals("\u63d0\u95ee")) {
            return this.ask(commandSender, string, stringArray);
        }
        if (string2.equals("summary") || string2.equals("\u603b\u7ed3")) {
            if (!commandSender.hasPermission("aichatpro.command.summary")) {
                this.deny(commandSender);
                return true;
            }
            this.plugin.getSummaryService().manual(commandSender);
            return true;
        }
        if (string2.equals("status") || string2.equals("\u72b6\u6001")) {
            if (!commandSender.hasPermission("aichatpro.command.status")) {
                this.deny(commandSender);
                return true;
            }
            this.status(commandSender, string);
            return true;
        }
        if (string2.equals("test") || string2.equals("\u81ea\u68c0")) {
            if (!commandSender.hasPermission("aichatpro.command.status")) {
                this.deny(commandSender);
                return true;
            }
            this.test(commandSender, stringArray);
            return true;
        }
        if (string2.equals("info") || string2.equals("\u4fe1\u606f") || string2.equals("\u670d\u52a1\u5668\u4fe1\u606f")) {
            if (!commandSender.hasPermission("aichatpro.command.info")) {
                this.deny(commandSender);
                return true;
            }
            this.info(commandSender);
            return true;
        }
        if (string2.equals("usage") || string2.equals("\u7528\u91cf") || string2.equals("\u82b1\u8d39")) {
            return this.usage(commandSender, string, stringArray);
        }
        if (string2.equals("limit") || string2.equals("\u989d\u5ea6")) {
            return this.limit(commandSender, string, stringArray);
        }
        if (string2.equals("reset") || string2.equals("\u91cd\u7f6e")) {
            return this.reset(commandSender, string, stringArray);
        }
        if (string2.equals("reload") || string2.equals("\u91cd\u8f7d")) {
            if (!commandSender.hasPermission("aichatpro.command.reload")) {
                this.deny(commandSender);
                return true;
            }
            this.plugin.reloadAll();
            commandSender.sendMessage(Util.color(settings.messages.prefix + settings.messages.reloaded));
            return true;
        }
        this.help(commandSender, string);
        return true;
    }

    private boolean ask(CommandSender commandSender, String string, String[] stringArray) {
        if (!(commandSender instanceof Player)) {
            commandSender.sendMessage(Util.color(this.plugin.getSettings().messages.playersOnly));
            return true;
        }
        Player player = (Player)commandSender;
        if (!player.hasPermission("aichatpro.command.ask")) {
            this.deny(commandSender);
            return true;
        }
        if (stringArray.length < 2) {
            player.sendMessage(Util.color(this.plugin.getSettings().messages.prefix + "&e\u7528\u6cd5: /" + string + " ask <\u4f60\u60f3\u95ee\u7684\u95ee\u9898>"));
            return true;
        }
        StringBuilder stringBuilder = new StringBuilder();
        for (int i = 1; i < stringArray.length; ++i) {
            if (i > 1) {
                stringBuilder.append(' ');
            }
            stringBuilder.append(stringArray[i]);
        }
        this.plugin.getAskService().ask(player, stringBuilder.toString(), 0);
        return true;
    }

    private boolean limit(CommandSender commandSender, String string, String[] stringArray) {
        Settings settings = this.plugin.getSettings();
        if (stringArray.length >= 2) {
            if (!commandSender.hasPermission("aichatpro.command.limit.others")) {
                this.deny(commandSender);
                return true;
            }
            OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer((String)stringArray[1]);
            int n = this.plugin.getLimitManager().used(offlinePlayer.getUniqueId());
            int n2 = 0;
            n2 = offlinePlayer.isOnline() ? this.plugin.getLimitManager().resolveLimit(offlinePlayer.getPlayer()) : (settings.limits.tiers.containsKey("default") ? settings.limits.tiers.get("default") : 20);
            commandSender.sendMessage(Util.color(settings.messages.prefix + "&f\u73a9\u5bb6 &b" + stringArray[1] + " &f\u4eca\u65e5\u5df2\u7528 &b" + n + "&f/&b" + n2 + " &f\u6b21\uff0c&b" + this.plugin.getLimitManager().resetText() + " &f\u540e\u91cd\u7f6e\u3002"));
            return true;
        }
        if (!(commandSender instanceof Player)) {
            commandSender.sendMessage(Util.color(settings.messages.prefix + "&e\u63a7\u5236\u53f0\u8bf7\u4f7f\u7528 /" + string + " limit <\u73a9\u5bb6\u540d>"));
            return true;
        }
        if (!commandSender.hasPermission("aichatpro.command.limit")) {
            this.deny(commandSender);
            return true;
        }
        Player player = (Player)commandSender;
        int n = this.plugin.getLimitManager().used(player.getUniqueId());
        int n3 = this.plugin.getLimitManager().resolveLimit(player);
        boolean bl = this.plugin.getLimitManager().isBypassed(player);
        commandSender.sendMessage(Util.color(settings.messages.prefix + "&f\u4eca\u65e5\u5df2\u7528 &b" + n + "&f/&b" + (bl ? "\u221e" : String.valueOf(n3)) + " &f\u6b21\uff0c&b" + this.plugin.getLimitManager().resetText() + " &f\u540e\u91cd\u7f6e\u3002"));
        return true;
    }

    private void test(final CommandSender commandSender, String[] stringArray) {
        final Settings settings = this.plugin.getSettings();
        final String string = settings.messages.prefix;
        if (!this.plugin.getAiService().isConfigured()) {
            commandSender.sendMessage(Util.color(string + settings.output.noApiMessage));
            return;
        }
        StringBuilder stringBuilder = new StringBuilder();
        for (int i = 1; i < stringArray.length; ++i) {
            if (stringBuilder.length() > 0) {
                stringBuilder.append(' ');
            }
            stringBuilder.append(stringArray[i]);
        }
        String string2 = stringBuilder.length() > 0 ? stringBuilder.toString() : "\u8bf7\u56de\u590d\u4e00\u53e5\u8bdd\uff0c\u786e\u8ba4\u8fde\u63a5\u6d4b\u8bd5\u6210\u529f\u3002";
        commandSender.sendMessage(Util.color(string + "&7\u6b63\u5728\u6d4b\u8bd5 AI \u63a5\u53e3\u2026"));
        ArrayList<AIService.ChatTurn> arrayList = new ArrayList<AIService.ChatTurn>();
        arrayList.add(new AIService.ChatTurn("user", string2));
        this.plugin.getAiService().complete("\u4f60\u662f\u4e00\u4e2a\u63a5\u53e3\u8fde\u901a\u6027\u6d4b\u8bd5\u52a9\u624b\uff0c\u8bf7\u7528\u4e00\u53e5\u8bdd\u7b80\u77ed\u56de\u590d\u3002", arrayList, new AIService.Callback(){

            @Override
            public void success(AIService.Response response) {
                String string2 = " &8[tokens in=" + response.promptTokens + " out=" + response.completionTokens + " \u9884\u4f30\u00a5" + AIChatCommand.this.plugin.getLimitManager().serverUsage().costText(settings.usage, settings.api.model) + "]";
                AIChatCommand.this.sendLater(commandSender, Util.color(string + "&a\u63a5\u53e3\u6b63\u5e38\uff0c\u6a21\u578b\u8fd4\u56de\uff1a&f" + response.content + string2));
            }

            @Override
            public void failure(String string2) {
                AIChatCommand.this.sendLater(commandSender, Util.color(string + Util.format(settings.output.errorMessage, "error", string2)));
            }
        });
    }

    private void sendLater(final CommandSender commandSender, final String string) {
        Bukkit.getScheduler().runTask((Plugin)this.plugin, new Runnable(){

            @Override
            public void run() {
                commandSender.sendMessage(string);
            }
        });
    }

    private boolean reset(CommandSender commandSender, String string, String[] stringArray) {
        Settings settings = this.plugin.getSettings();
        if (!commandSender.hasPermission("aichatpro.command.reset")) {
            this.deny(commandSender);
            return true;
        }
        if (stringArray.length < 2) {
            commandSender.sendMessage(Util.color(settings.messages.prefix + "&e\u7528\u6cd5: /" + string + " reset <\u73a9\u5bb6\u540d>"));
            return true;
        }
        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer((String)stringArray[1]);
        this.plugin.getLimitManager().setUsed(offlinePlayer.getUniqueId(), stringArray[1], 0);
        commandSender.sendMessage(Util.color(settings.messages.prefix + "&a\u5df2\u91cd\u7f6e &b" + stringArray[1] + " &a\u7684\u4eca\u65e5\u989d\u5ea6\u3002"));
        return true;
    }

    private void status(CommandSender commandSender, String string) {
        Settings settings = this.plugin.getSettings();
        String string2 = settings.messages.prefix;
        commandSender.sendMessage(Util.color("&8&m--------&r &bAIChat_pro &7v" + this.plugin.getDescription().getVersion() + " &8&m--------"));
        commandSender.sendMessage(Util.color("&f\u4f5c\u8005: &blaoda    &fAPI: " + (this.plugin.getAiService().isConfigured() ? "&a\u5df2\u914d\u7f6e" : "&c\u672a\u914d\u7f6e")));
        commandSender.sendMessage(Util.color("&f\u6a21\u578b: &b" + settings.api.model + " &7(" + settings.api.url + ")"));
        commandSender.sendMessage(Util.color("&f\u5524\u9192\u8bcd: &b" + Util.join(settings.trigger.prefixes, " / ") + " &7(" + settings.trigger.matchMode + ")    &f\u5f00\u5173: " + (settings.trigger.enabled ? "&a\u5f00" : "&c\u5173")));
        commandSender.sendMessage(Util.color("&f\u81ea\u52a8\u603b\u7ed3: " + (settings.summary.enabled ? "&a\u6bcf " + settings.summary.intervalMinutes + " \u5206\u949f &7/ &a\u6700\u8fd1 " + settings.summary.messageCount + " \u6761" : "&c\u5173\u95ed")));
        commandSender.sendMessage(Util.color("&f\u804a\u5929\u7f13\u5b58: &b" + this.plugin.getMessageStore().size() + " &7/ " + settings.context.bufferSize + " &f\u6761"));
        UsageRecord usageRecord = this.plugin.getLimitManager().serverUsage();
        commandSender.sendMessage(Util.color("&f\u4eca\u65e5\u5168\u670d: &b" + usageRecord.totalTokens() + " &ftoken\uff08\u8f93\u5165 " + usageRecord.promptTokens + " / \u8f93\u51fa " + usageRecord.completionTokens + "\uff09\uff0c\u9884\u4f30 &e\u00a5" + usageRecord.costText(settings.usage, settings.api.model)));
        commandSender.sendMessage(Util.color("&f\u968f\u673a\u5524\u9192: " + (settings.randomTrigger.enabled ? "&a\u5f00 &7(" + settings.randomTrigger.chance * 100.0 + "%\uff0c\u6743\u9650 " + settings.randomTrigger.permission + ")" : "&c\u5173")));
        commandSender.sendMessage(Util.color("&fPlaceholderAPI: " + (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null ? "&a\u5df2\u8054\u52a8" : "&7\u672a\u5b89\u88c5")));
        commandSender.sendMessage(Util.color("&f\u6bcf\u65e5\u989d\u5ea6\u6863\u4f4d: &b" + settings.limits.tiers + " &7(\u91cd\u7f6e " + settings.limits.resetHour + ":00)"));
        if (commandSender instanceof Player) {
            Player player = (Player)commandSender;
            commandSender.sendMessage(Util.color(string2 + "&f\u4f60\u4eca\u65e5\u5df2\u7528 &b" + this.plugin.getLimitManager().used(player.getUniqueId()) + "&f/&b" + this.plugin.getLimitManager().resolveLimit(player)));
        }
        commandSender.sendMessage(Util.color(string2 + "&7\u8f93\u5165 &b/" + string + " help &7\u67e5\u770b\u5e2e\u52a9\u3002"));
    }

    private void info(CommandSender commandSender) {
        Settings settings = this.plugin.getSettings();
        String string = this.plugin.getServerInfoService().build();
        if (string == null || string.isEmpty()) {
            commandSender.sendMessage(Util.color(settings.messages.prefix + "&e\u670d\u52a1\u5668\u4fe1\u606f\u529f\u80fd\u672a\u5f00\u542f\uff0c\u6216\u5f53\u524d\u6ca1\u6709\u53ef\u663e\u793a\u7684\u5185\u5bb9\u3002"));
            return;
        }
        commandSender.sendMessage(Util.color(settings.messages.prefix + "&7\u4ee5\u4e0b\u5185\u5bb9\u4f1a\u5728\u76f8\u5173\u63d0\u95ee\u65f6\u63d0\u4f9b\u7ed9 AI\uff1a"));
        for (String string2 : string.split("\n")) {
            commandSender.sendMessage(Util.color("&7" + string2));
        }
        commandSender.sendMessage(Util.color(settings.messages.prefix + "&7\u5728\u7ebf\u4eba\u6570 {online} / TPS {tps}"));
    }

    private boolean usage(CommandSender commandSender, String string, String[] stringArray) {
        String permission = stringArray.length >= 2 ? "aichatpro.command.usage.others" : "aichatpro.command.usage";
        if (!commandSender.hasPermission(permission)) {
            this.deny(commandSender);
            return true;
        }
        Object object;
        Settings settings = this.plugin.getSettings();
        LimitManager limitManager = this.plugin.getLimitManager();
        String string2 = settings.api.model;
        String string3 = this.plugin.getPlaceholders().apply(settings.messages.prefix, null);
        UsageRecord usageRecord = limitManager.serverUsage();
        commandSender.sendMessage(Util.color(string3 + Util.format(settings.messages.usageServerFormat, "tokens_in", usageRecord.promptTokens, "tokens_out", usageRecord.completionTokens, "tokens_total", usageRecord.totalTokens(), "requests", usageRecord.requests, "cost", usageRecord.costText(settings.usage, string2))));
        UsageRecord usageRecord2 = null;
        String string4 = null;
        if (stringArray.length >= 2) {
            if (!commandSender.hasPermission("aichatpro.command.usage.others")) {
                this.deny(commandSender);
                return true;
            }
            object = Bukkit.getOfflinePlayer((String)stringArray[1]);
            string4 = stringArray[1];
            usageRecord2 = limitManager.playerUsage(((OfflinePlayer)object).getUniqueId());
        } else if (commandSender instanceof Player) {
            object = (Player)commandSender;
            if (!((Player)object).hasPermission("aichatpro.command.usage")) {
                this.deny(commandSender);
                return true;
            }
            string4 = ((OfflinePlayer)object).getName();
            usageRecord2 = limitManager.playerUsage(((OfflinePlayer)object).getUniqueId());
        }
        if (usageRecord2 != null) {
            commandSender.sendMessage(Util.color(string3 + "&b" + string4 + " &r" + Util.format(settings.messages.usageFormat, "tokens_in", usageRecord2.promptTokens, "tokens_out", usageRecord2.completionTokens, "tokens_total", usageRecord2.totalTokens(), "requests", usageRecord2.requests, "cost", usageRecord2.costText(settings.usage, string2))));
        } else {
            commandSender.sendMessage(Util.color(string3 + "&e\u63a7\u5236\u53f0\u8bf7\u4f7f\u7528 /" + string + " usage <\u73a9\u5bb6\u540d>"));
        }
        object = settings.usage.priceFor(string2);
        commandSender.sendMessage(Util.color(string3 + "&7\u4ef7\u683c\uff08\u5143/\u767e\u4e07 token\uff09\uff1a\u8f93\u5165 &b" + ((Settings.Price)object).input + " &7\u8f93\u51fa &b" + ((Settings.Price)object).output + " &7\u7f13\u5b58\u8f93\u5165 &b" + ((Settings.Price)object).cachedInput + " &8| &7\u6a21\u578b &b" + string2 + " &8\uff08\u53ef\u5728 config.yml \u7684 usage.pricing \u4fee\u6539\uff09"));
        return true;
    }

    private void help(CommandSender commandSender, String string) {
        String string2 = this.plugin.getSettings().messages.prefix;
        commandSender.sendMessage(Util.color("&8&m--------&r &bAIChat_pro &7\u5e2e\u52a9 &8&m--------"));
        commandSender.sendMessage(Util.color("&b/" + string + " ask <\u95ee\u9898> &7- \u76f4\u63a5\u5411 AI \u63d0\u95ee"));
        commandSender.sendMessage(Util.color("&b/" + string + " info &7- \u67e5\u770b\u670d\u52a1\u5668\u5b9e\u65f6\u4fe1\u606f\uff08TPS/\u5728\u7ebf\u4eba\u6570\u7b49\uff09"));
        commandSender.sendMessage(Util.color("&b/" + string + " usage [\u73a9\u5bb6] &7- \u67e5\u770b token \u7528\u91cf\u4e0e\u9884\u4f30\u82b1\u8d39"));
        commandSender.sendMessage(Util.color("&b/" + string + " summary &7- \u7acb\u5373\u603b\u7ed3\u6700\u8fd1\u7684\u804a\u5929"));
        commandSender.sendMessage(Util.color("&b/" + string + " limit [\u73a9\u5bb6] &7- \u67e5\u770b\u4eca\u65e5\u5269\u4f59\u989d\u5ea6"));
        commandSender.sendMessage(Util.color("&b/" + string + " status &7- \u67e5\u770b\u63d2\u4ef6\u72b6\u6001"));
        commandSender.sendMessage(Util.color("&b/" + string + " test [\u6587\u672c] &7- \u6d4b\u8bd5 AI \u63a5\u53e3\u662f\u5426\u53ef\u7528"));
        commandSender.sendMessage(Util.color("&b/" + string + " reset <\u73a9\u5bb6> &7- \u91cd\u7f6e\u67d0\u73a9\u5bb6\u4eca\u65e5\u989d\u5ea6"));
        commandSender.sendMessage(Util.color("&b/" + string + " reload &7- \u91cd\u8f7d\u914d\u7f6e\u6587\u4ef6"));
        commandSender.sendMessage(Util.color(string2 + "&7\u804a\u5929\u91cc\u8f93\u5165 &b@\u5927\u80a5\u9c7c \u4f60\u7684\u95ee\u9898 &7\u5373\u53ef\u5524\u9192\u3002"));
    }

    private void deny(CommandSender commandSender) {
        commandSender.sendMessage(Util.color(this.plugin.getSettings().messages.prefix + this.plugin.getSettings().messages.noPermission));
    }

    public List<String> onTabComplete(CommandSender commandSender, Command command, String string, String[] stringArray) {
        ArrayList<String> arrayList = new ArrayList<String>();
        if (stringArray.length == 1) {
            ArrayList<String> arrayList2 = new ArrayList<String>(Arrays.asList("ask", "info", "usage", "summary", "limit", "status", "test", "reset", "reload", "help"));
            for (String string2 : arrayList2) {
                if (!string2.startsWith(stringArray[0].toLowerCase())) continue;
                arrayList.add(string2);
            }
            return arrayList;
        }
        if (stringArray.length == 2 && (stringArray[0].equalsIgnoreCase("limit") || stringArray[0].equalsIgnoreCase("reset"))) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (!player.getName().toLowerCase().startsWith(stringArray[1].toLowerCase())) continue;
                arrayList.add(player.getName());
            }
            return arrayList;
        }
        return arrayList;
    }
}

