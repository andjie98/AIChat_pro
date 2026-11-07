/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.bukkit.configuration.ConfigurationSection
 *  org.bukkit.configuration.file.FileConfiguration
 */
package com.laoda.aichatpro;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

public class Settings {
    public final boolean debug;
    public final String serverName;
    public final Api api;
    public final Trigger trigger;
    public final RandomTrigger randomTrigger;
    public final ServerInfo serverInfo;
    public final Context context;
    public final Summary summary;
    public final Limits limits;
    public final Usage usage;
    public final AdminNotify adminNotify;
    public final Output output;
    public final Filter filter;
    public final Logging logging;
    public final Messages messages;

    public Settings(FileConfiguration fileConfiguration) {
        this.debug = Settings.bool(fileConfiguration, "general.debug", false);
        this.serverName = Settings.str(fileConfiguration, "general.server-name", "Minecraft Server");
        this.api = new Api(fileConfiguration);
        this.trigger = new Trigger(fileConfiguration);
        this.randomTrigger = new RandomTrigger(fileConfiguration);
        this.serverInfo = new ServerInfo(fileConfiguration);
        this.context = new Context(fileConfiguration);
        this.summary = new Summary(fileConfiguration);
        this.limits = new Limits(fileConfiguration);
        this.usage = new Usage(fileConfiguration);
        this.adminNotify = new AdminNotify(fileConfiguration);
        this.output = new Output(fileConfiguration);
        this.filter = new Filter(fileConfiguration);
        this.logging = new Logging(fileConfiguration);
        this.messages = new Messages(fileConfiguration);
    }

    private static String str(FileConfiguration fileConfiguration, String string, String string2) {
        String string3 = fileConfiguration.getString(string);
        return string3 == null ? string2 : string3;
    }

    private static int integer(FileConfiguration fileConfiguration, String string, int n) {
        return fileConfiguration.getInt(string, n);
    }

    private static boolean bool(FileConfiguration fileConfiguration, String string, boolean bl) {
        return fileConfiguration.getBoolean(string, bl);
    }

    private static List<String> stringList(FileConfiguration fileConfiguration, String string) {
        List list = fileConfiguration.getStringList(string);
        return list == null ? new ArrayList() : list;
    }

    private static Set<String> lowerSet(List<String> list) {
        LinkedHashSet<String> linkedHashSet = new LinkedHashSet<String>();
        for (String string : list) {
            if (string == null || string.trim().isEmpty()) continue;
            linkedHashSet.add(string.trim().toLowerCase());
        }
        return linkedHashSet;
    }

    public static class AdminNotify {
        public final boolean enabled;
        public final String format;
        public final boolean logToFile;

        AdminNotify(FileConfiguration fileConfiguration) {
            this.enabled = Settings.bool(fileConfiguration, "admin-notify.enabled", true);
            this.format = Settings.str(fileConfiguration, "admin-notify.format", "&8[&cAI\u79c1\u804a&8] &f{player} &7\u95ee:&f {question} &7\u7b54:&f {answer}");
            this.logToFile = Settings.bool(fileConfiguration, "admin-notify.log-to-file", true);
        }
    }

    public static class Price {
        public final double input;
        public final double output;
        public final double cachedInput;

        public Price(double d, double d2, double d3) {
            this.input = d;
            this.output = d2;
            this.cachedInput = d3;
        }
    }

    public static class Usage {
        public final boolean enabled;
        public final Map<String, Price> pricing;

        Usage(FileConfiguration fileConfiguration) {
            this.enabled = Settings.bool(fileConfiguration, "usage.enabled", true);
            LinkedHashMap<String, Price> linkedHashMap = new LinkedHashMap<String, Price>();
            ConfigurationSection configurationSection = fileConfiguration.getConfigurationSection("usage.pricing");
            if (configurationSection != null) {
                for (String string : configurationSection.getKeys(false)) {
                    double d = configurationSection.getDouble(string + ".input", 0.0);
                    double d2 = configurationSection.getDouble(string + ".output", 0.0);
                    double d3 = configurationSection.getDouble(string + ".cached-input", d);
                    linkedHashMap.put(string.toLowerCase(), new Price(d, d2, d3));
                }
            }
            if (!linkedHashMap.containsKey("default")) {
                linkedHashMap.put("default", new Price(1.0, 4.0, 0.1));
            }
            this.pricing = linkedHashMap;
        }

        public Price priceFor(String string) {
            Price price;
            if (string != null && (price = this.pricing.get(string.toLowerCase())) != null) {
                return price;
            }
            return this.pricing.get("default");
        }

        public double cost(String string, int n, int n2, int n3) {
            Price price = this.priceFor(string);
            if (price == null) {
                return 0.0;
            }
            int n4 = Math.max(0, Math.min(n3, n));
            int n5 = Math.max(0, n - n4);
            return (double)n5 / 1000000.0 * price.input + (double)n4 / 1000000.0 * price.cachedInput + (double)n2 / 1000000.0 * price.output;
        }
    }

    public static class ServerInfo {
        public final boolean enabled;
        public final String mode;
        public final List<String> keywords;
        public final boolean onlineCount;
        public final boolean playerList;
        public final boolean tps;
        public final boolean memory;
        public final boolean uptime;
        public final boolean world;
        public final boolean version;
        public final boolean entities;
        public final int maxPlayerList;
        public final String header;

        ServerInfo(FileConfiguration fileConfiguration) {
            this.enabled = Settings.bool(fileConfiguration, "server-info.enabled", true);
            this.mode = Settings.str(fileConfiguration, "server-info.mode", "keyword");
            ArrayList<String> arrayList = new ArrayList<String>();
            for (String string : Settings.stringList(fileConfiguration, "server-info.keywords")) {
                if (string == null || string.trim().isEmpty()) continue;
                arrayList.add(string.trim().toLowerCase());
            }
            this.keywords = arrayList;
            this.onlineCount = Settings.bool(fileConfiguration, "server-info.include.online-count", true);
            this.playerList = Settings.bool(fileConfiguration, "server-info.include.player-list", true);
            this.tps = Settings.bool(fileConfiguration, "server-info.include.tps", true);
            this.memory = Settings.bool(fileConfiguration, "server-info.include.memory", true);
            this.uptime = Settings.bool(fileConfiguration, "server-info.include.uptime", true);
            this.world = Settings.bool(fileConfiguration, "server-info.include.world", true);
            this.version = Settings.bool(fileConfiguration, "server-info.include.version", true);
            this.entities = Settings.bool(fileConfiguration, "server-info.include.entities", false);
            this.maxPlayerList = Math.max(1, Settings.integer(fileConfiguration, "server-info.max-player-list", 10));
            this.header = Settings.str(fileConfiguration, "server-info.header", "\u3010\u670d\u52a1\u5668\u5b9e\u65f6\u4fe1\u606f\u3011");
        }
    }

    public static class RandomTrigger {
        public final boolean enabled;
        public final String permission;
        public final boolean requirePermission;
        public final double chance;
        public final int cooldownSeconds;
        public final int ignoreAfterAnyAskSeconds;
        public final int minLength;
        public final int contextChatLines;
        public final List<String> ignoreWords;
        public final boolean countDaily;
        public final String replyMode;
        public final String replyFormat;
        public final String thinkingMessage;
        public final Set<String> disabledWorlds;

        RandomTrigger(FileConfiguration fileConfiguration) {
            this.enabled = Settings.bool(fileConfiguration, "random-trigger.enabled", true);
            this.permission = Settings.str(fileConfiguration, "random-trigger.permission", "aichatpro.random");
            this.requirePermission = Settings.bool(fileConfiguration, "random-trigger.require-permission", true);
            this.chance = Math.max(0.0, Math.min(1.0, fileConfiguration.getDouble("random-trigger.chance", 0.05)));
            this.cooldownSeconds = Math.max(0, Settings.integer(fileConfiguration, "random-trigger.cooldown-seconds", 60));
            this.ignoreAfterAnyAskSeconds = Math.max(0, Settings.integer(fileConfiguration, "random-trigger.ignore-after-any-ask-seconds", 30));
            this.minLength = Math.max(1, Settings.integer(fileConfiguration, "random-trigger.min-length", 4));
            this.contextChatLines = Math.max(0, Settings.integer(fileConfiguration, "random-trigger.context-chat-lines", 8));
            List<String> list = Settings.stringList(fileConfiguration, "random-trigger.ignore-words");
            ArrayList<String> arrayList = new ArrayList<String>();
            for (String string : list) {
                if (string == null || string.trim().isEmpty()) continue;
                arrayList.add(string.trim().toLowerCase());
            }
            this.ignoreWords = arrayList;
            this.countDaily = Settings.bool(fileConfiguration, "random-trigger.count-daily", true);
            this.replyMode = Settings.str(fileConfiguration, "random-trigger.reply-mode", "broadcast");
            this.replyFormat = Settings.str(fileConfiguration, "random-trigger.reply-format", "&7[&b\u5927\u80a5\u9c7c&7] &7@{player} &f{answer}");
            this.thinkingMessage = Settings.str(fileConfiguration, "random-trigger.thinking-message", "");
            this.disabledWorlds = Settings.lowerSet(Settings.stringList(fileConfiguration, "random-trigger.disabled-worlds"));
        }
    }

    public static class Messages {
        public final String prefix;
        public final String noPermission;
        public final String playersOnly;
        public final String reloaded;
        public final String usageFormat;
        public final String usageServerFormat;

        Messages(FileConfiguration fileConfiguration) {
            this.prefix = Settings.str(fileConfiguration, "messages.prefix", "&8[&bAIChat&8] &r");
            this.noPermission = Settings.str(fileConfiguration, "messages.no-permission", "&c\u4f60\u6ca1\u6709\u6743\u9650\u6267\u884c\u8fd9\u4e2a\u64cd\u4f5c\u3002");
            this.playersOnly = Settings.str(fileConfiguration, "messages.players-only", "&c\u8be5\u547d\u4ee4\u53ea\u80fd\u7531\u73a9\u5bb6\u6267\u884c\u3002");
            this.reloaded = Settings.str(fileConfiguration, "messages.reloaded", "&a\u914d\u7f6e\u6587\u4ef6\u5df2\u91cd\u8f7d\u3002");
            this.usageFormat = Settings.str(fileConfiguration, "messages.usage-format", "&f\u4eca\u65e5\u7528\u91cf\uff1a\u8f93\u5165 &b{tokens_in} &f/ \u8f93\u51fa &b{tokens_out} &f/ \u5408\u8ba1 &b{tokens_total} &ftoken\uff0c\u8bf7\u6c42 &b{requests} &f\u6b21\uff0c\u9884\u4f30\u82b1\u8d39 &e\u00a5{cost}");
            this.usageServerFormat = Settings.str(fileConfiguration, "messages.usage-server-format", "&f\u5168\u670d\u4eca\u65e5\u7528\u91cf\uff1a\u8f93\u5165 &b{tokens_in} &f/ \u8f93\u51fa &b{tokens_out} &f/ \u5408\u8ba1 &b{tokens_total} &ftoken\uff0c\u8bf7\u6c42 &b{requests} &f\u6b21\uff0c\u9884\u4f30\u82b1\u8d39 &e\u00a5{cost}");
        }
    }

    public static class Logging {
        public final boolean logRequests;
        public final boolean logSummaries;
        public final String logFile;

        Logging(FileConfiguration fileConfiguration) {
            this.logRequests = Settings.bool(fileConfiguration, "logging.log-requests", true);
            this.logSummaries = Settings.bool(fileConfiguration, "logging.log-summaries", true);
            this.logFile = Settings.str(fileConfiguration, "logging.log-file", "aichatpro.log");
        }
    }

    public static class Filter {
        public final List<String> blacklistWords;
        public final String blacklistMessage;
        public final List<Pattern> ignorePatterns;

        Filter(FileConfiguration fileConfiguration) {
            this.blacklistWords = Settings.stringList(fileConfiguration, "filter.blacklist-words");
            this.blacklistMessage = Settings.str(fileConfiguration, "filter.blacklist-message", "&c\u8fd9\u4e2a\u95ee\u9898\u6211\u4e0d\u80fd\u56de\u7b54\u54e6~");
            ArrayList<Pattern> arrayList = new ArrayList<Pattern>();
            for (String string : Settings.stringList(fileConfiguration, "filter.ignore-patterns")) {
                try {
                    arrayList.add(Pattern.compile(string));
                }
                catch (PatternSyntaxException patternSyntaxException) {}
            }
            this.ignorePatterns = arrayList;
        }
    }

    public static class Output {
        public final int maxLineLength;
        public final boolean stripMarkdown;
        public final String errorMessage;
        public final String noApiMessage;
        public final boolean notifyAdminsOnError;

        Output(FileConfiguration fileConfiguration) {
            this.maxLineLength = Math.max(20, Settings.integer(fileConfiguration, "output.max-line-length", 100));
            this.stripMarkdown = Settings.bool(fileConfiguration, "output.strip-markdown", true);
            this.errorMessage = Settings.str(fileConfiguration, "output.error-message", "&cAI \u8bf7\u6c42\u5931\u8d25\uff08{error}\uff09\u3002");
            this.noApiMessage = Settings.str(fileConfiguration, "output.no-api-message", "&c\u7ba1\u7406\u5458\u8fd8\u6ca1\u6709\u914d\u7f6e API \u5bc6\u94a5\u3002");
            this.notifyAdminsOnError = Settings.bool(fileConfiguration, "output.notify-admins-on-error", true);
        }
    }

    public static class Limits {
        public final boolean dailyEnabled;
        public final int resetHour;
        public final Map<String, Integer> tiers;
        public final boolean countWake;
        public final boolean countManualSummary;
        public final boolean countAutoSummary;
        public final boolean countRandom;
        public final String bypassPermission;
        public final String exceededMessage;
        public final int globalCooldownSeconds;
        public final String cooldownBypassPermission;
        public final int saveIntervalSeconds;

        Limits(FileConfiguration fileConfiguration) {
            this.dailyEnabled = Settings.bool(fileConfiguration, "limits.daily.enabled", true);
            this.resetHour = Math.min(23, Math.max(0, Settings.integer(fileConfiguration, "limits.daily.reset-hour", 4)));
            LinkedHashMap<String, Integer> linkedHashMap = new LinkedHashMap<String, Integer>();
            ConfigurationSection configurationSection = fileConfiguration.getConfigurationSection("limits.daily.tiers");
            if (configurationSection != null) {
                for (String string : configurationSection.getKeys(false)) {
                    linkedHashMap.put(string, Math.max(0, configurationSection.getInt(string, 0)));
                }
            }
            if (linkedHashMap.isEmpty()) {
                linkedHashMap.put("default", 20);
            }
            this.tiers = linkedHashMap;
            this.countWake = Settings.bool(fileConfiguration, "limits.daily.count-wake", true);
            this.countManualSummary = Settings.bool(fileConfiguration, "limits.daily.count-manual-summary", true);
            this.countAutoSummary = Settings.bool(fileConfiguration, "limits.daily.count-auto-summary", false);
            this.countRandom = Settings.bool(fileConfiguration, "limits.daily.count-random", true);
            this.bypassPermission = Settings.str(fileConfiguration, "limits.daily.bypass-permission", "aichatpro.bypass.limit");
            this.exceededMessage = Settings.str(fileConfiguration, "limits.daily.exceeded-message", "&c\u4eca\u65e5\u6b21\u6570\u5df2\u7528\u5b8c\uff08{limit} \u6b21\uff09\u3002");
            this.globalCooldownSeconds = Math.max(0, Settings.integer(fileConfiguration, "limits.global-cooldown-seconds", 1));
            this.cooldownBypassPermission = Settings.str(fileConfiguration, "limits.cooldown-bypass-permission", "aichatpro.bypass.cooldown");
            this.saveIntervalSeconds = Math.max(10, Settings.integer(fileConfiguration, "limits.save-interval-seconds", 60));
        }
    }

    public static class Summary {
        public final boolean enabled;
        public final int intervalMinutes;
        public final int messageCount;
        public final int minMessages;
        public final String prompt;
        public final String broadcastFormat;
        public final boolean broadcast;
        public final boolean logToFile;
        public final boolean fallbackBroadcastRaw;
        public final Set<String> disabledWorlds;

        Summary(FileConfiguration fileConfiguration) {
            this.enabled = Settings.bool(fileConfiguration, "auto-summary.enabled", true);
            this.intervalMinutes = Math.max(1, Settings.integer(fileConfiguration, "auto-summary.interval-minutes", 10));
            this.messageCount = Math.max(1, Settings.integer(fileConfiguration, "auto-summary.message-count", 15));
            this.minMessages = Math.max(1, Settings.integer(fileConfiguration, "auto-summary.min-messages", 5));
            this.prompt = Settings.str(fileConfiguration, "auto-summary.prompt", "\u8bf7\u603b\u7ed3\u4e0b\u9762 {count} \u6761\u804a\u5929\u8bb0\u5f55\u3002");
            this.broadcastFormat = Settings.str(fileConfiguration, "auto-summary.broadcast-format", "&8[&6AI\u603b\u7ed3&8] &f{summary}");
            this.broadcast = Settings.bool(fileConfiguration, "auto-summary.broadcast", true);
            this.logToFile = Settings.bool(fileConfiguration, "auto-summary.log-to-file", true);
            this.fallbackBroadcastRaw = Settings.bool(fileConfiguration, "auto-summary.fallback-broadcast-raw", false);
            this.disabledWorlds = Settings.lowerSet(Settings.stringList(fileConfiguration, "auto-summary.disabled-worlds"));
        }
    }

    public static class Context {
        public final int bufferSize;
        public final int chatLines;
        public final int historyRounds;

        Context(FileConfiguration fileConfiguration) {
            this.bufferSize = Math.max(10, Settings.integer(fileConfiguration, "context.buffer-size", 200));
            this.chatLines = Math.max(0, Settings.integer(fileConfiguration, "context.chat-lines", 10));
            this.historyRounds = Math.max(0, Settings.integer(fileConfiguration, "context.history-rounds", 3));
        }
    }

    public static class Trigger {
        public final boolean enabled;
        public final List<String> prefixes;
        public final boolean containsMode;
        public final String matchMode;
        public final boolean stripPrefix;
        public final int cooldownSeconds;
        public final boolean requirePermission;
        public final String cooldownMessage;
        public final String thinkingMessage;
        public final String replyMode;
        public final String replyFormat;
        public final String emptyReplyMessage;
        public final String emptyQuestionMessage;
        public final int maxQuestionLength;
        public final Set<String> disabledWorlds;

        Trigger(FileConfiguration fileConfiguration) {
            this.enabled = Settings.bool(fileConfiguration, "trigger.enabled", true);
            List<String> arrayList = Settings.stringList(fileConfiguration, "trigger.prefixes");
            if (arrayList.isEmpty()) {
                arrayList = new ArrayList<String>();
                arrayList.add("@\u5927\u80a5\u9c7c");
            }
            this.prefixes = arrayList;
            this.matchMode = Settings.str(fileConfiguration, "trigger.match-mode", "prefix");
            this.containsMode = "contains".equalsIgnoreCase(this.matchMode);
            this.stripPrefix = Settings.bool(fileConfiguration, "trigger.strip-prefix", true);
            this.cooldownSeconds = Math.max(0, Settings.integer(fileConfiguration, "trigger.cooldown-seconds", 8));
            this.requirePermission = Settings.bool(fileConfiguration, "trigger.require-permission", true);
            this.cooldownMessage = Settings.str(fileConfiguration, "trigger.cooldown-message", "&c\u51b7\u5374\u4e2d\uff0c{seconds} \u79d2\u540e\u518d\u8bd5~");
            this.thinkingMessage = Settings.str(fileConfiguration, "trigger.thinking-message", "");
            this.replyMode = Settings.str(fileConfiguration, "trigger.reply-mode", "whisper");
            this.replyFormat = Settings.str(fileConfiguration, "trigger.reply-format", "&7[&b\u5927\u80a5\u9c7c&7] &f{answer}");
            this.emptyReplyMessage = Settings.str(fileConfiguration, "trigger.empty-reply-message", "&7[&b\u5927\u80a5\u9c7c&7] &f\u6211\u4e00\u65f6\u8bed\u585e\u4e86\u2026");
            this.emptyQuestionMessage = Settings.str(fileConfiguration, "trigger.empty-question-message", "&7[&b\u5927\u80a5\u9c7c&7] &f\u54fc\uff0c\u5149\u558a\u6211\u540d\u5b57\u5e72\u561b\uff1f\u6709\u4e8b\u5c31\u8bf4\uff0c\u6bd4\u5982 &b@\u5927\u80a5\u9c7c \u73b0\u5728\u670d\u52a1\u5668\u5361\u5417");
            this.maxQuestionLength = Math.max(1, Settings.integer(fileConfiguration, "trigger.max-question-length", 200));
            this.disabledWorlds = Settings.lowerSet(Settings.stringList(fileConfiguration, "trigger.disabled-worlds"));
        }
    }

    public static class Api {
        public final String url;
        public final String key;
        public final String keyEnv;
        public final String model;
        public final String systemPrompt;
        public final int connectTimeoutSeconds;
        public final int readTimeoutSeconds;
        public final int retries;
        public final int maxConcurrentRequests;
        public final int maxQueuedRequests;
        public final int queueTimeoutSeconds;
        public final double temperature;
        public final int maxTokens;
        public final String extraBody;

        Api(FileConfiguration fileConfiguration) {
            this.url = Settings.str(fileConfiguration, "api.url", "https://api.deepseek.com/v1/chat/completions");
            this.key = Settings.str(fileConfiguration, "api.key", "");
            this.keyEnv = Settings.str(fileConfiguration, "api.key-env", "AICHATPRO_API_KEY");
            this.model = Settings.str(fileConfiguration, "api.model", "deepseek-flash");
            this.systemPrompt = Settings.str(fileConfiguration, "api.system-prompt", "\u4f60\u662f\u4e00\u4e2a Minecraft \u670d\u52a1\u5668\u7684\u804a\u5929\u52a9\u624b\u3002");
            this.connectTimeoutSeconds = Math.max(1, Settings.integer(fileConfiguration, "api.connect-timeout-seconds", 10));
            this.readTimeoutSeconds = Math.max(1, Settings.integer(fileConfiguration, "api.read-timeout-seconds", 60));
            this.retries = Math.max(0, Settings.integer(fileConfiguration, "api.retries", 2));
            this.maxConcurrentRequests = Math.max(1, Settings.integer(fileConfiguration, "api.max-concurrent-requests", 2));
            this.maxQueuedRequests = Math.max(1, Math.min(1000, Settings.integer(fileConfiguration, "api.max-queued-requests", 32)));
            this.queueTimeoutSeconds = Math.max(1, Settings.integer(fileConfiguration, "api.queue-timeout-seconds", 30));
            this.temperature = fileConfiguration.getDouble("api.temperature", 0.8);
            this.maxTokens = Math.max(16, Settings.integer(fileConfiguration, "api.max-tokens", 1200));
            this.extraBody = Settings.str(fileConfiguration, "api.extra-body", "").trim();
        }
    }
}

