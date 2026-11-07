/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.bukkit.Bukkit
 *  org.bukkit.command.CommandExecutor
 *  org.bukkit.command.PluginCommand
 *  org.bukkit.command.TabCompleter
 *  org.bukkit.entity.Player
 *  org.bukkit.event.Listener
 *  org.bukkit.plugin.Plugin
 *  org.bukkit.plugin.java.JavaPlugin
 */
package com.laoda.aichatpro;

import com.laoda.aichatpro.AIChatCommand;
import com.laoda.aichatpro.AIService;
import com.laoda.aichatpro.AskService;
import com.laoda.aichatpro.ChatListener;
import com.laoda.aichatpro.LimitManager;
import com.laoda.aichatpro.MessageStore;
import com.laoda.aichatpro.PlaceholderService;
import com.laoda.aichatpro.ServerInfoService;
import com.laoda.aichatpro.Settings;
import com.laoda.aichatpro.SummaryService;
import com.laoda.aichatpro.Util;
import com.laoda.aichatpro.hook.AIChatExpansion;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

public class AIChatPro
extends JavaPlugin {
    private volatile Settings settings;
    private ServerGuide serverGuide = ServerGuide.EMPTY;
    private MessageStore messageStore;
    private volatile AIService aiService;
    private LimitManager limitManager;
    private AskService askService;
    private SummaryService summaryService;
    private ServerInfoService serverInfoService;
    private PlaceholderService placeholders;
    private final Object logLock = new Object();

    public void onEnable() {
        this.saveDefaultConfig();
        this.settings = new Settings(this.getConfig());
        this.loadServerGuide();
        this.messageStore = new MessageStore(this.settings.context.bufferSize);
        this.aiService = new AIService(this);
        this.limitManager = new LimitManager(this);
        this.limitManager.load();
        this.serverInfoService = new ServerInfoService(this);
        this.serverInfoService.refreshCache();
        this.serverInfoService.startCacheTask();
        this.placeholders = new PlaceholderService(this);
        this.askService = new AskService(this);
        this.summaryService = new SummaryService(this);
        this.getServer().getPluginManager().registerEvents((Listener)new ChatListener(this), (Plugin)this);
        this.setupPlaceholderAPI();
        PluginCommand pluginCommand = this.getCommand("aichat");
        if (pluginCommand != null) {
            AIChatCommand aIChatCommand = new AIChatCommand(this);
            pluginCommand.setExecutor((CommandExecutor)aIChatCommand);
            pluginCommand.setTabCompleter((TabCompleter)aIChatCommand);
        } else {
            this.getLogger().warning("\u547d\u4ee4 /aichat \u6ce8\u518c\u5931\u8d25\uff0c\u8bf7\u68c0\u67e5 plugin.yml\u3002");
        }
        this.summaryService.start();
        long l = (long)Math.max(10, this.settings.limits.saveIntervalSeconds) * 20L;
        this.getServer().getScheduler().runTaskTimerAsynchronously((Plugin)this, new Runnable(){

            @Override
            public void run() {
                AIChatPro.this.limitManager.save();
            }
        }, l, l);
        this.banner();
    }

    public void onDisable() {
        if (this.summaryService != null) {
            this.summaryService.stop();
        }
        if (this.aiService != null) {
            this.aiService.shutdown();
        }
        if (this.limitManager != null) {
            this.limitManager.saveNow();
        }
        this.getServer().getScheduler().cancelTasks((Plugin)this);
        this.getLogger().info("AIChat_pro \u5df2\u5378\u8f7d\u3002");
    }

    public void reloadAll() {
        if (this.summaryService != null) {
            this.summaryService.stop();
        }
        if (this.aiService != null) {
            this.aiService.shutdown();
        }
        if (this.askService != null) {
            this.askService.clearAllMemory();
        }
        this.reloadConfig();
        this.settings = new Settings(this.getConfig());
        this.loadServerGuide();
        this.messageStore.setCapacity(this.settings.context.bufferSize);
        this.aiService = new AIService(this);
        if (this.summaryService != null) {
            this.summaryService.start();
        }
        this.getLogger().info("\u914d\u7f6e\u5df2\u91cd\u8f7d\u3002");
    }

    ServerGuide getServerGuide() {
        return serverGuide;
    }

    private void loadServerGuide() {
        if (!getConfig().getBoolean("guide.enabled", true)) {
            serverGuide = ServerGuide.EMPTY;
            return;
        }
        File file = new File(getDataFolder(), "guide.md");
        try {
            if (!file.exists()) saveResource("guide.md", false);
            ServerGuide loaded = ServerGuide.parse(new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8));
            if (loaded.size() == 0) throw new IOException("没有找到带正文的二级标题");
            serverGuide = loaded;
            getLogger().info("服务器指南已加载，共 " + loaded.size() + " 条。");
        } catch (IOException | IllegalArgumentException ex) {
            getLogger().warning("服务器指南加载失败，保留上次可用资料：" + ex.getMessage());
        }
    }

    private void setupPlaceholderAPI() {
        if (this.getServer().getPluginManager().getPlugin("PlaceholderAPI") == null) {
            return;
        }
        try {
            new AIChatExpansion(this).register();
            this.getLogger().info("\u5df2\u6ce8\u518c PlaceholderAPI \u5360\u4f4d\u7b26: %aichatpro_xxx%");
        }
        catch (Throwable throwable) {
            this.getLogger().warning("PlaceholderAPI \u8054\u52a8\u6ce8\u518c\u5931\u8d25\uff1a" + throwable);
        }
    }

    private void banner() {
        this.getLogger().info("=========================================");
        this.getLogger().info(" AIChat_pro v" + this.getDescription().getVersion() + "  by laoda");
        this.getLogger().info(" \u5524\u9192\u65b9\u5f0f: @\u5524\u9192" + Util.join(this.settings.trigger.prefixes, " / ") + (this.settings.randomTrigger.enabled ? " | \u968f\u673a\u89e6\u53d1 " + this.settings.randomTrigger.chance * 100.0 + "%\uff08\u6743\u9650 " + this.settings.randomTrigger.permission + "\uff09" : "") + " | \u5b9a\u65f6\u603b\u7ed3" + (this.settings.summary.enabled ? "\uff08\u6bcf " + this.settings.summary.intervalMinutes + " \u5206\u949f\uff09" : "\uff08\u5173\u95ed\uff09"));
        this.getLogger().info(" \u670d\u52a1\u5668\u4fe1\u606f: " + (this.settings.serverInfo.enabled ? "\u5f00\u542f\uff08\u6a21\u5f0f " + this.settings.serverInfo.mode + "\uff09" : "\u5173\u95ed") + " | \u7528\u91cf\u7edf\u8ba1: " + (this.settings.usage.enabled ? "\u5f00\u542f" : "\u5173\u95ed"));
        this.getLogger().info(" AI \u63a5\u53e3: " + (this.aiService.isConfigured() ? this.settings.api.model + " @ " + this.aiService.endpoint() : "\u672a\u914d\u7f6e\u5bc6\u94a5\uff08AI \u529f\u80fd\u6682\u505c\uff0c\u8bf7\u5728 config.yml \u586b\u5199 api.key\uff09"));
        this.getLogger().info("=========================================");
    }

    public void debug(String string) {
        if (this.settings != null && this.settings.debug) {
            this.getLogger().info("[\u8c03\u8bd5] " + string);
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public void logToFile(String string) {
        String string2 = this.settings.logging.logFile;
        try {
            File file = this.getDataFolder();
            if (!file.exists() && !file.mkdirs()) {
                return;
            }
            File file2 = new File(file, string2);
            String string3 = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
            byte[] byArray = ("[" + string3 + "] " + string + System.lineSeparator()).getBytes(StandardCharsets.UTF_8);
            Object object = this.logLock;
            synchronized (object) {
                Files.write(file2.toPath(), byArray, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            }
        }
        catch (IOException iOException) {
            this.getLogger().log(Level.WARNING, "\u5199\u5165\u65e5\u5fd7\u6587\u4ef6\u5931\u8d25: " + iOException.getMessage(), iOException);
        }
    }

    public void notifyAdmins(String string) {
        if (this.settings == null || !this.settings.output.notifyAdminsOnError || !this.isEnabled()) {
            return;
        }
        final String string2 = Util.color(string);
        Bukkit.getScheduler().runTask((Plugin)this, new Runnable(){

            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (!player.hasPermission("aichatpro.notify")) continue;
                    player.sendMessage(string2);
                }
            }
        });
    }

    public Settings getSettings() {
        return this.settings;
    }

    public MessageStore getMessageStore() {
        return this.messageStore;
    }

    public AIService getAiService() {
        return this.aiService;
    }

    public LimitManager getLimitManager() {
        return this.limitManager;
    }

    public AskService getAskService() {
        return this.askService;
    }

    public SummaryService getSummaryService() {
        return this.summaryService;
    }

    public ServerInfoService getServerInfoService() {
        return this.serverInfoService;
    }

    public PlaceholderService getPlaceholders() {
        return this.placeholders;
    }
}

