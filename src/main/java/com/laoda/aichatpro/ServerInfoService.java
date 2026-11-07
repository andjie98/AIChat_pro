/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.bukkit.Bukkit
 *  org.bukkit.World
 *  org.bukkit.entity.Player
 *  org.bukkit.plugin.Plugin
 */
package com.laoda.aichatpro;

import com.laoda.aichatpro.AIChatPro;
import com.laoda.aichatpro.Settings;
import com.laoda.aichatpro.Util;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public class ServerInfoService {
    private final AIChatPro plugin;
    private Method tpsMethod;
    private boolean tpsResolved;
    private volatile int cachedOnline = 0;
    private volatile double[] cachedTps = null;

    public ServerInfoService(AIChatPro aIChatPro) {
        this.plugin = aIChatPro;
    }

    public void refreshCache() {
        this.cachedOnline = Bukkit.getOnlinePlayers().size();
        this.cachedTps = this.readTps();
    }

    public void startCacheTask() {
        Bukkit.getScheduler().runTaskTimer((Plugin)this.plugin, new Runnable(){

            @Override
            public void run() {
                ServerInfoService.this.refreshCache();
            }
        }, 20L, 20L);
    }

    public String build() {
        Object object;
        Settings.ServerInfo serverInfo = this.plugin.getSettings().serverInfo;
        if (!serverInfo.enabled) {
            return "";
        }
        ArrayList<String> arrayList = new ArrayList<String>();
        if (serverInfo.onlineCount) {
            object = new StringBuilder("\u5728\u7ebf\u4eba\u6570: ").append(Bukkit.getOnlinePlayers().size()).append('/').append(Bukkit.getMaxPlayers());
            if (serverInfo.playerList) {
                ((StringBuilder)object).append("\uff08").append(this.playerList(serverInfo.maxPlayerList)).append("\uff09");
            }
            arrayList.add(((StringBuilder)object).toString());
        } else if (serverInfo.playerList) {
            arrayList.add("\u5728\u7ebf\u73a9\u5bb6: " + this.playerList(serverInfo.maxPlayerList));
        }
        if (serverInfo.tps && (object = this.readTps()) != null && ((double[])object).length >= 3) {
            arrayList.add(String.format(Locale.ROOT, "TPS(1\u5206/5\u5206/15\u5206): %.2f / %.2f / %.2f\uff0820 \u4e3a\u6ee1\u503c\uff09", ((double[])object)[0], ((double[])object)[1], ((double[])object)[2]));
        }
        if (serverInfo.memory) {
            object = Runtime.getRuntime();
            long l = (((Runtime)object).totalMemory() - ((Runtime)object).freeMemory()) / 0x100000L;
            long l2 = ((Runtime)object).maxMemory() / 0x100000L;
            arrayList.add("\u5185\u5b58\u5360\u7528: " + l + "MB / " + l2 + "MB");
        }
        if (serverInfo.uptime) {
            arrayList.add("\u5df2\u8fd0\u884c: " + this.uptimeText());
        }
        if (serverInfo.world) {
            arrayList.add(this.worldText());
        }
        if (serverInfo.version) {
            arrayList.add("\u670d\u52a1\u7aef: " + Bukkit.getVersion() + " | API " + Bukkit.getBukkitVersion());
        }
        if (serverInfo.entities) {
            int n = 0;
            int n2 = 0;
            for (World world : Bukkit.getWorlds()) {
                n += world.getEntities().size();
                n2 += world.getLoadedChunks().length;
            }
            arrayList.add("\u5b9e\u4f53\u6570: " + n + " | \u5df2\u52a0\u8f7d\u533a\u5757: " + n2);
        }
        if (arrayList.isEmpty()) {
            return "";
        }
        StringBuilder stringBuilder = new StringBuilder(serverInfo.header).append('\n');
        for (String string : arrayList) {
            stringBuilder.append("- ").append(string).append('\n');
        }
        return stringBuilder.toString().trim();
    }

    public int onlineCount() {
        return this.cachedOnline;
    }

    public double[] tpsSnapshot() {
        return this.cachedTps;
    }

    private double[] readTps() {
        if (!this.tpsResolved) {
            this.tpsResolved = true;
            try {
                this.tpsMethod = Bukkit.class.getMethod("getTPS", new Class[0]);
            }
            catch (Throwable throwable) {
                this.tpsMethod = null;
            }
        }
        if (this.tpsMethod == null) {
            return null;
        }
        try {
            return (double[])this.tpsMethod.invoke(null, new Object[0]);
        }
        catch (Throwable throwable) {
            return null;
        }
    }

    private String playerList(int n) {
        ArrayList<String> arrayList = new ArrayList<String>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            arrayList.add(player.getName());
        }
        if (arrayList.isEmpty()) {
            return "\u5f53\u524d\u6ca1\u4eba";
        }
        if (arrayList.size() <= n) {
            return Util.join(arrayList, "\u3001");
        }
        List list = arrayList.subList(0, n);
        return Util.join(list, "\u3001") + " \u7b49" + arrayList.size() + "\u4eba";
    }

    private String uptimeText() {
        long l = ManagementFactory.getRuntimeMXBean().getUptime();
        long l2 = l / 60000L;
        long l3 = l2 / 1440L;
        long l4 = l2 % 1440L / 60L;
        long l5 = l2 % 60L;
        StringBuilder stringBuilder = new StringBuilder();
        if (l3 > 0L) {
            stringBuilder.append(l3).append("\u5929");
        }
        if (l4 > 0L || l3 > 0L) {
            stringBuilder.append(l4).append("\u5c0f\u65f6");
        }
        stringBuilder.append(l5).append("\u5206\u949f");
        return stringBuilder.toString();
    }

    private String worldText() {
        List<World> list = Bukkit.getWorlds();
        if (list.isEmpty()) {
            return "\u4e16\u754c: \u672a\u77e5";
        }
        ArrayList<String> arrayList = new ArrayList<String>();
        for (World world : list) {
            arrayList.add(world.getName());
        }
        World world = (World)list.get(0);
        long l = world.getTime();
        long l2 = (l / 1000L + 6L) % 24L;
        String string = world.hasStorm() ? (world.isThundering() ? "\u96f7\u96e8" : "\u4e0b\u96e8") : "\u6674\u6717";
        return "\u4e16\u754c: " + Util.join(arrayList, "\u3001") + " | \u4e3b\u4e16\u754c\u65f6\u95f4: " + (world.getTime() < 12000L ? "\u767d\u5929" : "\u591c\u665a") + String.format(Locale.ROOT, "\uff08\u7ea6 %02d:00\uff09", l2) + " | \u5929\u6c14: " + string;
    }
}

