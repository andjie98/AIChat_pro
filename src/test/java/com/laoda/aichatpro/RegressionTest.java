package com.laoda.aichatpro;

import org.junit.*;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginLogger;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;
import java.io.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import com.sun.net.httpserver.HttpServer;

public class RegressionTest {
    AIChatPro plugin;
    YamlConfiguration config;
    LimitManager limits;
    Player player;
    Path folder;
    static Server server;
    final List<AIService> services = new ArrayList<AIService>();
    HttpServer http;

    @BeforeClass public static void server() {
        server = mock(Server.class);
        when(server.getLogger()).thenReturn(java.util.logging.Logger.getLogger("test-server"));
        when(server.getName()).thenReturn("test");
        when(server.getVersion()).thenReturn("1.12.2");
        when(server.getBukkitVersion()).thenReturn("1.12.2");
        when(server.isPrimaryThread()).thenReturn(true);
        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        when(server.getScheduler()).thenReturn(scheduler);
        doAnswer(call -> { ((Runnable) call.getArgument(1)).run(); return null; })
            .when(scheduler).runTask(any(org.bukkit.plugin.Plugin.class), any(Runnable.class));
        Bukkit.setServer(server);
    }
    static void field(Object target, Class<?> type, String name, Object value) throws Exception {
        Field f=type.getDeclaredField(name); f.setAccessible(true); f.set(target,value);
    }
    @Before public void setup() throws Exception {
        folder=Files.createTempDirectory("aichat-test-");
        plugin=mock(AIChatPro.class);
        field(plugin,JavaPlugin.class,"dataFolder",folder.toFile());
        field(plugin,JavaPlugin.class,"isEnabled",true);
        field(plugin,JavaPlugin.class,"logger",mock(PluginLogger.class));
        config=new YamlConfiguration();
        config.set("limits.global-cooldown-seconds",0);
        config.set("trigger.cooldown-seconds",0);
        config.set("limits.daily.tiers.default",1);
        config.set("api.key","local-test-key");
        config.set("api.max-concurrent-requests",1);
        config.set("api.max-queued-requests",1);
        config.set("api.retries",2);
        config.set("auto-summary.min-messages",2);
        config.set("auto-summary.broadcast",false);
        config.set("auto-summary.log-to-file",false);
        config.set("server-info.enabled",false);
        refresh();
        limits=new LimitManager(plugin);
        when(plugin.getLimitManager()).thenReturn(limits);
        player=mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.getName()).thenReturn("tester");
        when(player.isOnline()).thenReturn(true);
        PlaceholderService placeholders=mock(PlaceholderService.class);
        when(placeholders.apply(anyString(), any())).thenAnswer(c -> c.getArgument(0));
        when(plugin.getPlaceholders()).thenReturn(placeholders);
    }
    void refresh() {when(plugin.getSettings()).thenReturn(new Settings(config));}
    @After public void cleanup() throws Exception {
        for(AIService service:services) service.shutdown();
        if(http!=null) http.stop(0);
        try(java.util.stream.Stream<Path> paths=Files.walk(folder)) {
            for(Path p:(Iterable<Path>)paths.sorted(Comparator.reverseOrder())::iterator) Files.deleteIfExists(p);
        }
    }
    @Test public void controlCharactersEscapeAndRoundTrip() {
        for(int c=0;c<32;c++) {
            String input=String.valueOf((char)c);
            assertEquals(input,Json.parse("\""+Json.escape(input)+"\""));
        }
    }
    @Test public void zeroContextReturnsNothing() {
        MessageStore store=new MessageStore(200);
        store.add(new MessageStore.ChatMessage("p","world","private",1));
        assertTrue(store.last(0).isEmpty()); assertEquals(1,store.last(1).size());
    }
    @Test public void reservationsPreventOverbookingAndFinishOnlyOnce() {
        LimitManager.Reservation first=limits.reserve(player,0,0);
        assertTrue(first.allowed());
        assertFalse(limits.reserve(player,0,0).allowed());
        first.finish(true); first.finish(true); first.finish(false);
        assertEquals(1,limits.used(player.getUniqueId()));
        assertFalse(limits.reserve(player,0,0).allowed());
    }
    @Test public void failedReservationReleasesAllowance() {
        LimitManager.Reservation first=limits.reserve(player,0,0);
        first.finish(false);
        assertEquals(0,limits.used(player.getUniqueId()));
        assertTrue(limits.reserve(player,0,0).allowed());
    }
    @Test public void concurrentAdmissionAcceptsOnlyOne() throws Exception {
        ExecutorService pool=Executors.newFixedThreadPool(8);
        try {
            List<Future<Boolean>> results=new ArrayList<>();
            for(int i=0;i<30;i++)results.add(pool.submit(() -> limits.reserve(player,0,0).allowed()));
            int accepted=0;for(Future<Boolean> result:results)if(result.get())accepted++;
            assertEquals(1,accepted);
        } finally {pool.shutdownNow();}
    }
    @Test public void globalCooldownAppliesAcrossPlayers() {
        config.set("limits.global-cooldown-seconds",10);refresh();
        assertTrue(limits.reserve(player,0,0).allowed());
        Player other=mock(Player.class);when(other.getUniqueId()).thenReturn(UUID.randomUUID());
        assertFalse(limits.reserve(other,0,0).allowed());
    }
    @Test public void snapshotDoesNotEraseConcurrentUpdate() {
        AtomicInteger writes=new AtomicInteger();
        LimitManager manager=new LimitManager(plugin) {
            @Override void writeSnapshot(YamlConfiguration snapshot) throws IOException {
                if(writes.incrementAndGet()==1) recordUsage(player.getUniqueId(),"tester",10,2,0);
                super.writeSnapshot(snapshot);
            }
        };
        manager.recordUsage(player.getUniqueId(),"tester",1,1,0);
        manager.save(); manager.save(); manager.save();
        assertEquals(2,writes.get());
        YamlConfiguration saved=YamlConfiguration.loadConfiguration(folder.resolve("data.yml").toFile());
        assertEquals(11,saved.getInt("server.promptTokens"));
        assertEquals(2,saved.getInt("server.requests"));
    }
    @Test public void failedWriteRemainsDirtyAndRetries() {
        AtomicInteger writes=new AtomicInteger();
        LimitManager manager=new LimitManager(plugin) {
            @Override void writeSnapshot(YamlConfiguration snapshot) throws IOException {
                if(writes.incrementAndGet()==1)throw new IOException("disk unavailable");
                super.writeSnapshot(snapshot);
            }
        };
        manager.recordUsage(null,null,4,1,0);manager.save();manager.save();manager.save();
        assertEquals(2,writes.get());
        assertEquals(4,YamlConfiguration.loadConfiguration(folder.resolve("data.yml").toFile()).getInt("server.promptTokens"));
    }
    @Test public void usageDenialDoesNotReadOrSendStatistics() {
        new AIChatCommand(plugin).onCommand(player,null,"aichat",new String[]{"usage"});
        verify(player,times(1)).sendMessage(anyString());
        verify(plugin,never()).getLimitManager();
        verify(plugin,never()).getPlaceholders();
    }
    @Test public void othersUsageDenialDoesNotReadStatistics() {
        new AIChatCommand(plugin).onCommand(player,null,"aichat",new String[]{"usage","other"});
        verify(player,times(1)).sendMessage(anyString());verify(plugin,never()).getLimitManager();
    }
    MessageStore messages() {
        MessageStore store=new MessageStore(20);
        store.add(new MessageStore.ChatMessage("p","world","one",1));
        store.add(new MessageStore.ChatMessage("p","world","two",1));
        when(plugin.getMessageStore()).thenReturn(store);return store;
    }
    AIService mockedAI(AtomicReference<AIService.Callback> callback) {
        AIService ai=mock(AIService.class);when(ai.isConfigured()).thenReturn(true);
        when(plugin.getAiService()).thenReturn(ai);
        doAnswer(c -> {callback.set(c.getArgument(2));return null;}).when(ai).complete(anyString(),anyList(),any());
        return ai;
    }
    @Test public void manualSummaryCountsAndBlocksAtLimit() {
        messages();AtomicReference<AIService.Callback> callback=new AtomicReference<>();AIService ai=mockedAI(callback);
        SummaryService summary=new SummaryService(plugin);summary.manual(player);
        callback.get().success(new AIService.Response("summary",5,2,0));summary.manual(player);
        verify(ai,times(1)).complete(anyString(),anyList(),any());
        assertEquals(1,limits.used(player.getUniqueId()));assertEquals(7,limits.playerUsage(player.getUniqueId()).totalTokens());
    }
    @Test public void failedManualSummaryReleasesReservation() {
        messages();AtomicReference<AIService.Callback> callback=new AtomicReference<>();AIService ai=mockedAI(callback);
        SummaryService summary=new SummaryService(plugin);summary.manual(player);callback.get().failure("cancelled");summary.manual(player);
        verify(ai,times(2)).complete(anyString(),anyList(),any());assertEquals(0,limits.used(player.getUniqueId()));
    }
    @Test public void manualSummaryHonorsGlobalCooldown() {
        config.set("limits.global-cooldown-seconds",10);refresh();messages();
        AtomicReference<AIService.Callback> callback=new AtomicReference<>();AIService ai=mockedAI(callback);
        limits.reserve(player,0,0).finish(false);
        new SummaryService(plugin).manual(player);
        verify(ai,never()).complete(anyString(),anyList(),any());
    }
    @Test public void autoSummaryDoesNotRepeatAndFailureDoesNotAdvanceWatermark() throws Exception {
        MessageStore store=messages();AtomicReference<AIService.Callback> callback=new AtomicReference<>();AIService ai=mockedAI(callback);
        SummaryService summary=new SummaryService(plugin);
        Method auto=SummaryService.class.getDeclaredMethod("autoSummary");auto.setAccessible(true);
        auto.invoke(summary);auto.invoke(summary);verify(ai,times(1)).complete(anyString(),anyList(),any());
        callback.get().failure("cancelled");auto.invoke(summary);verify(ai,times(2)).complete(anyString(),anyList(),any());
        callback.get().success(new AIService.Response("summary",2,1,0));auto.invoke(summary);
        verify(ai,times(2)).complete(anyString(),anyList(),any());
        store.add(new MessageStore.ChatMessage("p","world","three",1));auto.invoke(summary);
        verify(ai,times(2)).complete(anyString(),anyList(),any());
        store.add(new MessageStore.ChatMessage("p","world","four",1));auto.invoke(summary);
        verify(ai,times(3)).complete(anyString(),anyList(),any());
    }
    @Test public void watermarkSurvivesBufferClearAndFiltersExcludedWorlds() {
        MessageStore store=messages();long watermark=store.last(1).get(0).sequence;
        store.clear();store.add(new MessageStore.ChatMessage("p","excluded","hidden",1));
        store.add(new MessageStore.ChatMessage("p","world","new",1));
        List<MessageStore.ChatMessage> batch=store.since(watermark,10,Collections.singleton("excluded"));
        assertEquals(1,batch.size());assertEquals("new",batch.get(0).message);assertTrue(batch.get(0).sequence>watermark);
    }
    AIService httpService(AtomicInteger hits, CountDownLatch entered, CountDownLatch release) throws Exception {
        http=HttpServer.create(new java.net.InetSocketAddress("127.0.0.1",0),0);
        http.createContext("/chat/completions",exchange -> {
            hits.incrementAndGet();if(entered!=null)entered.countDown();
            try {if(release!=null)release.await(5,TimeUnit.SECONDS);}catch(InterruptedException ex){Thread.currentThread().interrupt();}
            byte[] body="{\"choices\":[{\"message\":{\"content\":\"ok\"}}],\"usage\":{\"prompt_tokens\":1}}".getBytes("UTF-8");
            exchange.sendResponseHeaders(200,body.length);try(OutputStream out=exchange.getResponseBody()){out.write(body);}exchange.close();
        });http.start();
        config.set("api.url","http://127.0.0.1:"+http.getAddress().getPort()+"/chat/completions");refresh();
        AIService ai=new AIService(plugin);services.add(ai);return ai;
    }
    void drained(AIService ai)throws Exception {
        Field f=AIService.class.getDeclaredField("pool");f.setAccessible(true);
        ((ExecutorService)f.get(ai)).submit(() -> {}).get(5,TimeUnit.SECONDS);
    }
    @Test public void successfulCallbackFailureDoesNotRepeatHttpRequest() throws Exception {
        AtomicInteger hits=new AtomicInteger();AIService ai=httpService(hits,null,null);
        AtomicInteger callbacks=new AtomicInteger();
        ai.complete("test",Collections.emptyList(),new AIService.Callback(){
            public void success(AIService.Response r){callbacks.incrementAndGet();throw new IllegalStateException("callback failed");}
            public void failure(String s){fail(s);}
        });drained(ai);assertEquals(1,hits.get());assertEquals(1,callbacks.get());
    }
    @Test public void boundedQueueRejectsAndShutdownNotifiesQueuedRequests() throws Exception {
        AtomicInteger hits=new AtomicInteger();CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
        AIService ai=httpService(hits,entered,release);
        AtomicInteger outcomes=new AtomicInteger(),failures=new AtomicInteger();
        AIService.Callback callback=new AIService.Callback(){
            public void success(AIService.Response r){outcomes.incrementAndGet();}
            public void failure(String s){failures.incrementAndGet();outcomes.incrementAndGet();}
        };
        try {
            ai.complete("one",Collections.emptyList(),callback);assertTrue(entered.await(5,TimeUnit.SECONDS));
            ai.complete("two",Collections.emptyList(),callback);ai.complete("three",Collections.emptyList(),callback);
            assertEquals(1,failures.get());assertEquals(1,hits.get());
            // Cancel queued and active work; every caller receives exactly one terminal outcome.
            release.countDown();ai.shutdown();assertEquals(3,outcomes.get());
            ai.shutdown();assertEquals(3,outcomes.get());
        } finally {release.countDown();}
    }

    @Test public void queuedRequestExpiresWithoutHttpCall() throws Exception {
        config.set("api.queue-timeout-seconds",1);
        AtomicInteger hits=new AtomicInteger();CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
        AIService ai=httpService(hits,entered,release);
        CountDownLatch complete=new CountDownLatch(2);AtomicReference<String> error=new AtomicReference<>();
        AIService.Callback callback=new AIService.Callback(){
            public void success(AIService.Response r){complete.countDown();}
            public void failure(String s){error.set(s);complete.countDown();}
        };
        try {
            ai.complete("active",Collections.emptyList(),callback);assertTrue(entered.await(5,TimeUnit.SECONDS));
            ai.complete("expired",Collections.emptyList(),callback);
            Field f=AIService.class.getDeclaredField("pool");f.setAccessible(true);
            Runnable queued=((ThreadPoolExecutor)f.get(ai)).getQueue().peek();assertNotNull(queued);
            field(queued,queued.getClass(),"submitted",System.nanoTime()-TimeUnit.SECONDS.toNanos(2));
            release.countDown();assertTrue(complete.await(5,TimeUnit.SECONDS));
            assertEquals(1,hits.get());assertNotNull(error.get());assertTrue(error.get().contains("超时"));
        } finally {release.countDown();}
    }
    @Test public void authenticationFailureDoesNotRetry() throws Exception {
        AtomicInteger hits=new AtomicInteger();AIService ai=httpService(hits,null,null);
        http.removeContext("/chat/completions");
        http.createContext("/chat/completions",exchange -> {
            hits.incrementAndGet();byte[] body="unauthorized".getBytes("UTF-8");
            exchange.sendResponseHeaders(401,body.length);
            try(OutputStream out=exchange.getResponseBody()){out.write(body);}exchange.close();
        });
        AtomicInteger failures=new AtomicInteger();AtomicReference<String> error=new AtomicReference<>();
        ai.complete("test",Collections.emptyList(),new AIService.Callback(){
            public void success(AIService.Response r){fail("Unexpected success");}
            public void failure(String s){failures.incrementAndGet();error.set(s);}
        });drained(ai);assertEquals(1,hits.get());assertEquals(1,failures.get());assertTrue(error.get().contains("401"));
    }
    @Test public void actualAskBuilderOmitsZeroContext() throws Exception {
        config.set("context.chat-lines",0);refresh();messages();
        AskService asks=new AskService(plugin);
        Method build=AskService.class.getDeclaredMethod("buildTurns",Player.class,String.class,Settings.class,int.class);
        build.setAccessible(true);
        List<?> turns=(List<?>)build.invoke(asks,player,"question",plugin.getSettings(),0);
        assertEquals(1,turns.size());
        assertEquals("tester \u8bf4\uff1aquestion",((AIService.ChatTurn)turns.get(0)).content);
    }

    private ServerGuide bundledGuide() throws IOException {
        try (InputStream stream = getClass().getResourceAsStream("/guide.md")) {
            assertNotNull(stream);
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            int count;
            while ((count = stream.read(buffer)) != -1) bytes.write(buffer, 0, count);
            return ServerGuide.parse(bytes.toString("UTF-8"));
        }
    }

    @Test public void guideFindsRealQuestionsIncludingSectionsAfterForty() throws Exception {
        ServerGuide guide = bundledGuide();
        assertTrue(guide.size() > 40);
        assertTrue(guide.find("，我之前圈了有地盘，怎么回领地").get(0).answer().contains("/res tp"));
        assertTrue(guide.find("支票指令是啥？").get(0).answer().contains("/cheque"));
        assertTrue(guide.find("刷物封了还能解吗").get(0).answer().contains("不可解封"));
        assertTrue(guide.find("粘液科技是啥").get(0).answer().contains("大多数"));
        assertTrue(guide.find("你好大肥鱼").isEmpty());
    }

    @Test public void guideAnswersThroughAskWithoutApiOrChargingQuota() throws Exception {
        when(plugin.getServerGuide()).thenReturn(bundledGuide());
        when(player.hasPermission("aichatpro.use")).thenReturn(true);
        new AskService(plugin).ask(player, "支票指令是啥？", 0);
        org.mockito.ArgumentCaptor<String[]> reply = org.mockito.ArgumentCaptor.forClass(String[].class);
        verify(player).sendMessage(reply.capture());
        assertTrue(Arrays.asList(reply.getValue()).contains("/cheque [金额]"));
        verify(plugin, never()).getAiService();
        assertEquals(0, limits.used(player.getUniqueId()));
    }
}
