/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.bukkit.configuration.ConfigurationSection
 *  org.bukkit.configuration.file.YamlConfiguration
 *  org.bukkit.entity.Player
 */
package com.laoda.aichatpro;

import com.laoda.aichatpro.AIChatPro;
import com.laoda.aichatpro.Settings;
import com.laoda.aichatpro.UsageRecord;
import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

public class LimitManager {
    public static final int ACTION_WAKE = 0;
    public static final int ACTION_MANUAL_SUMMARY = 1;
    public static final int ACTION_RANDOM = 2;
    private final AIChatPro plugin;
    private final File file;
    private final Map<UUID, Entry> data = new HashMap<UUID, Entry>();
    private final Map<UUID, Long> lastUseByPlayer = new ConcurrentHashMap<UUID, Long>();
    private Entry serverEntry = new Entry();
    private volatile long lastGlobalUse = 0L;
    private long mutationVersion;
    private long savedVersion;
    private final Map<UUID, Reservation> pending = new HashMap<UUID, Reservation>();

    public LimitManager(AIChatPro aIChatPro) {
        this.plugin = aIChatPro;
        this.file = new File(aIChatPro.getDataFolder(), "data.yml");
    }

    public String currentDay() {
        int n = this.plugin.getSettings().limits.resetHour;
        LocalDateTime localDateTime = LocalDateTime.now().minusHours(n);
        return localDateTime.toLocalDate().format(DateTimeFormatter.ISO_LOCAL_DATE);
    }

    public String resetText() {
        int n = this.plugin.getSettings().limits.resetHour;
        LocalDate localDate = LocalDateTime.now().minusHours(n).toLocalDate();
        ZonedDateTime zonedDateTime = localDate.plusDays(1L).atTime(n, 0).atZone(ZoneId.systemDefault());
        long l = Duration.between(ZonedDateTime.now(), zonedDateTime).toMinutes();
        if (l < 0L) {
            l = 0L;
        }
        long l2 = l / 60L;
        long l3 = l % 60L;
        return (l2 > 0L ? l2 + "\u5c0f\u65f6" : "") + l3 + "\u5206\u949f";
    }

    public boolean counts(int n) {
        Settings settings = this.plugin.getSettings();
        Settings.Limits limits = settings.limits;
        if (n == 0) {
            return limits.countWake;
        }
        if (n == 1) {
            return limits.countManualSummary;
        }
        if (n == 2) {
            return limits.countRandom && settings.randomTrigger.countDaily;
        }
        return false;
    }

    public int defaultLimit() {
        Integer n = this.plugin.getSettings().limits.tiers.get("default");
        return n != null ? n : 20;
    }

    public int resolveLimit(Player player) {
        Settings.Limits limits = this.plugin.getSettings().limits;
        Integer n = null;
        for (Map.Entry<String, Integer> entry : limits.tiers.entrySet()) {
            if ("default".equalsIgnoreCase(entry.getKey())) {
                n = entry.getValue();
                continue;
            }
            if (player == null || !player.hasPermission("aichatpro.limit." + entry.getKey())) continue;
            return entry.getValue();
        }
        return n != null ? n : 20;
    }

    public boolean isBypassed(Player player) {
        return player != null && player.hasPermission(this.plugin.getSettings().limits.bypassPermission);
    }

    /** Called on the server thread; admission and reservations share the data lock. */
    public Reservation reserve(Player player, int action, int cooldownSeconds) {
        synchronized (data) {
            UUID id = player == null ? null : player.getUniqueId();
            if (id != null && pending.containsKey(id)) return new Reservation("你还有一个 AI 请求正在处理，请稍后再试。 ");
            Settings settings = plugin.getSettings();
            long wait = Math.max(globalCooldownRemaining(settings.limits.globalCooldownSeconds), cooldownRemaining(player, cooldownSeconds));
            if (wait > 0) return new Reservation(Util.format(settings.trigger.cooldownMessage, "seconds", (wait + 999) / 1000));
            Result result = check(player, action);
            if (!result.allowed) return new Reservation(Util.format(settings.limits.exceededMessage,
                    "limit", result.limit, "used", result.used, "reset", resetText()));
            boolean counted = player != null && settings.limits.dailyEnabled && counts(action);
            Reservation reservation = new Reservation(id, player == null ? null : player.getName(), counted, currentDay());
            if (id != null) pending.put(id, reservation);
            markUse(player);
            return reservation;
        }
    }

    public final class Reservation {
        public final String error;
        private final UUID id;
        private final String name;
        private final boolean counted;
        private final String day;
        private boolean finished;

        private Reservation(String error) {
            this.error = error; this.id = null; this.name = null; this.counted = false; this.day = null;
            this.finished = true;
        }
        private Reservation(UUID id, String name, boolean counted, String day) {
            this.error = null; this.id = id; this.name = name; this.counted = counted; this.day = day;
        }
        public boolean allowed() { return error == null; }
        public void finish(boolean success) {
            synchronized (data) {
                if (finished) return;
                finished = true;
                if (id != null) pending.remove(id, this);
                // A request admitted yesterday must not consume today's allowance.
                if (success && counted && day.equals(currentDay())) {
                    ++entry(id, name).used;
                    ++mutationVersion;
                }
            }
        }
    }

    public Result check(Player player, int n) {
        int n2 = this.resolveLimit(player);
        int n3 = player == null ? 0 : this.used(player.getUniqueId());
        Settings.Limits limits = this.plugin.getSettings().limits;
        if (!limits.dailyEnabled || !this.counts(n) || this.isBypassed(player)) {
            return new Result(true, n3, n2);
        }
        return new Result(n3 < n2, n3, n2);
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public void consume(Player player, int n) {
        if (player == null || !this.plugin.getSettings().limits.dailyEnabled || !this.counts(n)) {
            return;
        }
        Map<UUID, Entry> map = this.data;
        synchronized (map) {
            Entry entry = this.entry(player.getUniqueId(), player.getName());
            ++entry.used;
            ++this.mutationVersion;
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public void setUsed(UUID uUID, String string, int n) {
        Map<UUID, Entry> map = this.data;
        synchronized (map) {
            Entry entry = this.entry(uUID, string);
            entry.used = Math.max(0, n);
            if (n == 0) {
                entry.usage.clear();
            }
            ++this.mutationVersion;
        }
        this.plugin.debug("\u5df2\u91cd\u7f6e " + string + " \u7684\u4eca\u65e5\u989d\u5ea6\u4e3a " + n + "\u3002");
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public int used(UUID uUID) {
        Map<UUID, Entry> map = this.data;
        synchronized (map) {
            Entry entry = this.data.get(uUID);
            if (entry == null) {
                return 0;
            }
            if (!this.currentDay().equals(entry.day)) {
                this.reset(entry);
            }
            return entry.used;
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public void recordUsage(UUID uUID, String string, int n, int n2, int n3) {
        if (!this.plugin.getSettings().usage.enabled) {
            return;
        }
        Map<UUID, Entry> map = this.data;
        synchronized (map) {
            if (this.serverEntry == null || !this.currentDay().equals(this.serverEntry.day)) {
                this.serverEntry = new Entry();
                this.serverEntry.day = this.currentDay();
            }
            this.serverEntry.usage.add(n, n2, n3);
            if (uUID != null) {
                this.entry((UUID)uUID, (String)string).usage.add(n, n2, n3);
            }
            ++this.mutationVersion;
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public UsageRecord playerUsage(UUID uUID) {
        Map<UUID, Entry> map = this.data;
        synchronized (map) {
            Entry entry = this.data.get(uUID);
            if (entry == null) {
                return new UsageRecord();
            }
            if (!this.currentDay().equals(entry.day)) {
                this.reset(entry);
            }
            return entry.usage.copy();
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public UsageRecord serverUsage() {
        Map<UUID, Entry> map = this.data;
        synchronized (map) {
            if (this.serverEntry == null) {
                this.serverEntry = new Entry();
            }
            if (!this.currentDay().equals(this.serverEntry.day)) {
                this.reset(this.serverEntry);
            }
            return this.serverEntry.usage.copy();
        }
    }

    public long globalCooldownRemaining(int n) {
        long l;
        if (n <= 0) {
            return 0L;
        }
        long l2 = System.currentTimeMillis() - this.lastGlobalUse;
        return l2 >= (l = (long)n * 1000L) ? 0L : l - l2;
    }

    public long cooldownRemaining(Player player, int n) {
        long l;
        if (player == null || n <= 0) {
            return 0L;
        }
        if (player.hasPermission(this.plugin.getSettings().limits.cooldownBypassPermission)) {
            return 0L;
        }
        Long l2 = this.lastUseByPlayer.get(player.getUniqueId());
        if (l2 == null) {
            return 0L;
        }
        long l3 = System.currentTimeMillis() - l2;
        return l3 >= (l = (long)n * 1000L) ? 0L : l - l3;
    }

    public void markUse(Player player) {
        long l;
        this.lastGlobalUse = l = System.currentTimeMillis();
        if (player != null) {
            this.lastUseByPlayer.put(player.getUniqueId(), l);
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public void load() {
        Map<UUID, Entry> dataLock = this.data;
        synchronized (dataLock) {
            this.data.clear();
            this.serverEntry = new Entry();
            this.serverEntry.day = this.currentDay();
        }
        if (!this.file.exists()) {
            return;
        }
        YamlConfiguration yamlConfiguration = YamlConfiguration.loadConfiguration((File)this.file);
        ConfigurationSection configurationSection = yamlConfiguration.getConfigurationSection("players");
        if (configurationSection != null) {
            for (String string : configurationSection.getKeys(false)) {
                try {
                    UUID uUID = UUID.fromString(string);
                    Entry entry = new Entry();
                    entry.name = configurationSection.getString(string + ".name", "");
                    entry.day = configurationSection.getString(string + ".day", "");
                    entry.used = configurationSection.getInt(string + ".used", 0);
                    entry.usage.promptTokens = configurationSection.getInt(string + ".promptTokens", 0);
                    entry.usage.completionTokens = configurationSection.getInt(string + ".completionTokens", 0);
                    entry.usage.cachedTokens = configurationSection.getInt(string + ".cachedTokens", 0);
                    entry.usage.requests = configurationSection.getInt(string + ".requests", 0);
                    Map<UUID, Entry> map = this.data;
                    synchronized (map) {
                        this.data.put(uUID, entry);
                    }
                }
                catch (IllegalArgumentException illegalArgumentException) {
                }
            }
        }
        Map<UUID, Entry> map = this.data;
        synchronized (map) {
            this.serverEntry.day = yamlConfiguration.getString("server.day", this.currentDay());
            this.serverEntry.usage.promptTokens = yamlConfiguration.getInt("server.promptTokens", 0);
            this.serverEntry.usage.completionTokens = yamlConfiguration.getInt("server.completionTokens", 0);
            this.serverEntry.usage.cachedTokens = yamlConfiguration.getInt("server.cachedTokens", 0);
            this.serverEntry.usage.requests = yamlConfiguration.getInt("server.requests", 0);
            if (!this.currentDay().equals(this.serverEntry.day)) {
                this.reset(this.serverEntry);
            }
        }
    }

    public void save() {
        synchronized (data) {
            if (mutationVersion == savedVersion) return;
        }
        this.saveNow();
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public synchronized void saveNow() {
        final long snapshotVersion;
        YamlConfiguration yamlConfiguration = new YamlConfiguration();
        Object object = this.data;
        synchronized (object) {
            snapshotVersion = mutationVersion;
            for (Map.Entry<UUID, Entry> entry : this.data.entrySet()) {
                Entry entry2 = entry.getValue();
                String string = "players." + entry.getKey() + ".";
                yamlConfiguration.set(string + "name", (Object)entry2.name);
                yamlConfiguration.set(string + "day", (Object)entry2.day);
                yamlConfiguration.set(string + "used", (Object)entry2.used);
                yamlConfiguration.set(string + "promptTokens", (Object)entry2.usage.promptTokens);
                yamlConfiguration.set(string + "completionTokens", (Object)entry2.usage.completionTokens);
                yamlConfiguration.set(string + "cachedTokens", (Object)entry2.usage.cachedTokens);
                yamlConfiguration.set(string + "requests", (Object)entry2.usage.requests);
            }
            if (this.serverEntry != null) {
                yamlConfiguration.set("server.day", (Object)this.serverEntry.day);
                yamlConfiguration.set("server.promptTokens", (Object)this.serverEntry.usage.promptTokens);
                yamlConfiguration.set("server.completionTokens", (Object)this.serverEntry.usage.completionTokens);
                yamlConfiguration.set("server.cachedTokens", (Object)this.serverEntry.usage.cachedTokens);
                yamlConfiguration.set("server.requests", (Object)this.serverEntry.usage.requests);
            }
        }
        try {
            object = this.plugin.getDataFolder();
            if (!((File)object).exists() && !((File)object).mkdirs()) {
                this.plugin.getLogger().warning("\u65e0\u6cd5\u521b\u5efa\u63d2\u4ef6\u6570\u636e\u76ee\u5f55\uff1a" + ((File)object).getAbsolutePath());
            }
            writeSnapshot(yamlConfiguration);
            synchronized (data) { savedVersion = snapshotVersion; }
        }
        catch (IOException iOException) {
            this.plugin.getLogger().warning("\u4fdd\u5b58 data.yml \u5931\u8d25\uff1a" + iOException.getMessage());
        }
    }

    // Separate seam permits deterministic tests of updates arriving during disk writes.
    void writeSnapshot(YamlConfiguration snapshot) throws IOException {
        java.nio.file.Path temporary = java.nio.file.Files.createTempFile(file.getParentFile().toPath(), "data-", ".tmp");
        try {
            snapshot.save(temporary.toFile());
            try {
                java.nio.file.Files.move(temporary, file.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                        java.nio.file.StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException ex) {
                java.nio.file.Files.move(temporary, file.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            java.nio.file.Files.deleteIfExists(temporary);
        }
    }

    private void reset(Entry entry) {
        ++mutationVersion;
        entry.day = this.currentDay();
        entry.used = 0;
        entry.usage.clear();
    }

    private Entry entry(UUID uUID, String string) {
        Entry entry = this.data.get(uUID);
        if (entry == null) {
            entry = new Entry();
            entry.day = this.currentDay();
            this.data.put(uUID, entry);
        }
        if (string != null && !string.isEmpty()) {
            entry.name = string;
        }
        if (!this.currentDay().equals(entry.day)) {
            this.reset(entry);
        }
        return entry;
    }

    public static class Result {
        public final boolean allowed;
        public final int used;
        public final int limit;

        public Result(boolean bl, int n, int n2) {
            this.allowed = bl;
            this.used = n;
            this.limit = n2;
        }
    }

    private static class Entry {
        String name = "";
        String day = "";
        int used = 0;
        final UsageRecord usage = new UsageRecord();

        private Entry() {
        }
    }
}

